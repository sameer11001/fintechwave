package com.fintechwave.transaction.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.payment.CardPaymentIntent;
import com.fintechwave.payment.Money;
import com.fintechwave.payment.PaymentGatewayPort;
import com.fintechwave.payment.PayoutResult;
import com.fintechwave.payment.WebhookEvent;
import com.fintechwave.transaction.api.grpc.LedgerGrpcClient;
import com.fintechwave.transaction.domain.entity.OutboxEvent;
import com.fintechwave.transaction.domain.entity.TransactionRecord;
import com.fintechwave.transaction.domain.enums.TransactionStatus;
import com.fintechwave.transaction.domain.enums.TransactionType;
import com.fintechwave.transaction.dto.request.CashInRequest;
import com.fintechwave.transaction.dto.request.CashOutRequest;
import com.fintechwave.transaction.dto.request.InitiateTransferRequest;
import com.fintechwave.transaction.dto.response.TransactionResponse;
import com.fintechwave.transaction.exception.DuplicateTransactionException;
import com.fintechwave.transaction.exception.InvalidTransactionStateException;
import com.fintechwave.transaction.exception.TransactionNotFoundException;
import com.fintechwave.transaction.repository.OutboxEventRepository;
import com.fintechwave.transaction.repository.TransactionRepository;
import com.fintechwave.transaction.service.IFeeService;
import com.fintechwave.transaction.service.ITransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import com.fintechwave.events.GenericDomainEvent;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TransactionServiceImpl implements ITransactionService {

    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentGatewayPort paymentGateway;
    private final IFeeService feeService;
    private final ObjectMapper objectMapper;
    private final LedgerGrpcClient ledgerGrpcClient;

    @Override
    @Transactional
    public TransactionResponse initiateP2PTransfer(UUID senderId, InitiateTransferRequest request) {
        guardDuplicate(request.idempotencyKey());

        if (senderId.equals(request.receiverId())) {
            throw new InvalidTransactionStateException("Cannot transfer to yourself");
        }

        BigDecimal fee = feeService.calculateFee(TransactionType.P2P, request.amount(), request.currency());

        TransactionRecord tx = transactionRepository.save(
                TransactionRecord.builder()
                        .transactionType(TransactionType.P2P)
                        .status(TransactionStatus.INITIATED)
                        .senderId(senderId)
                        .receiverId(request.receiverId())
                        .amount(request.amount())
                        .currency(request.currency())
                        .feeAmount(fee)
                        .idempotencyKey(request.idempotencyKey())
                        .description(request.description())
                        .build());

        // Synchronous Reservation — if this fails the transaction is marked FAILED
        // and we throw so the caller gets an appropriate error response.
        try {
            ledgerGrpcClient.reserveFundsSync(tx.getId(), senderId, request.amount().add(fee), request.currency());
        } catch (Exception reserveEx) {
            log.warn("Ledger reservation failed for txId={} — aborting: {}", tx.getId(), reserveEx.getMessage());
            tx.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(tx);
            throw reserveEx;
        }

        publishOutboxEvent(tx.getId(), "TRANSACTION", "TRANSFER_INITIATED", 1,
                Map.of(
                        "transactionId", tx.getId().toString(),
                        "senderId", senderId.toString(),
                        "receiverId", request.receiverId().toString(),
                        "amount", request.amount().toPlainString(),
                        "currency", request.currency(),
                        "feeAmount", fee.toPlainString()));

        log.info("P2P transfer initiated and reserved: txId={} senderId={} amount={} {}", tx.getId(),
                senderId, request.amount(),
                request.currency());
        return TransactionResponse.from(tx);
    }

    @Override
    @Transactional
    public TransactionResponse initiateCashIn(UUID userId, CashInRequest request) {
        guardDuplicate(request.idempotencyKey());

        // Create Stripe PaymentIntent
        Money money = Money.of(request.amount(), request.currency());
        CardPaymentIntent intent = paymentGateway.createCardPaymentIntent(money, request.stripePaymentMethodId());

        TransactionRecord tx = transactionRepository.save(
                TransactionRecord.builder()
                        .transactionType(TransactionType.CASH_IN)
                        .status(TransactionStatus.INITIATED)
                        .senderId(userId)
                        .amount(request.amount())
                        .currency(request.currency())
                        .feeAmount(BigDecimal.ZERO) // No fee for cash-in
                        .stripePaymentIntentId(intent.paymentIntentId())
                        .idempotencyKey(request.idempotencyKey())
                        .description("Cash-in via card")
                        .build());

        log.info("Cash-in initiated: txId={} userId={} stripeIntentId={}", tx.getId(), userId,
                intent.paymentIntentId());
        // Ledger credit happens on payment_intent.succeeded webhook
        return TransactionResponse.from(tx);
    }

    @Override
    @Transactional
    public TransactionResponse initiateCashOut(UUID userId, CashOutRequest request) {
        guardDuplicate(request.idempotencyKey());

        BigDecimal fee = feeService.calculateFee(TransactionType.CASH_OUT, request.amount(), request.currency());

        // RESERVE funds in ledger first (via outbox event to ledger-service)
        TransactionRecord tx = transactionRepository.save(
                TransactionRecord.builder()
                        .transactionType(TransactionType.CASH_OUT)
                        .status(TransactionStatus.INITIATED)
                        .senderId(userId)
                        .amount(request.amount())
                        .currency(request.currency())
                        .feeAmount(fee)
                        .idempotencyKey(request.idempotencyKey())
                        .description("Cash-out to card")
                        .build());

        // Initiate Stripe Instant Payout
        Money money = Money.of(request.amount(), request.currency());
        PayoutResult payout = paymentGateway.initiateInstantPayout(request.stripePaymentMethodId(), money);

        tx.setStripePayoutId(payout.payoutId());
        tx.setStatus(TransactionStatus.RESERVED);
        transactionRepository.save(tx);

        // Publish event — ledger-service listens to commit on payout.paid webhook
        publishOutboxEvent(tx.getId(), "TRANSACTION", "CASH_OUT_INITIATED", 1,
                Map.of(
                        "transactionId", tx.getId().toString(),
                        "userId", userId.toString(),
                        "amount", request.amount().toPlainString(),
                        "currency", request.currency(),
                        "stripePayoutId", payout.payoutId()));

        log.info("Cash-out initiated: txId={} userId={} stripePayoutId={}", tx.getId(), userId, payout.payoutId());
        return TransactionResponse.from(tx);
    }

    @Override
    @Transactional
    public void handleStripeWebhook(String rawPayload, String signature) {
        WebhookEvent event = paymentGateway.parseAndValidateWebhook(rawPayload, signature);
        log.info("Stripe webhook received: type={}", event.eventType());

        switch (event.eventType()) {
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event.objectId());
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event.objectId());
            case "payout.paid" -> handlePayoutPaid(event.objectId());
            case "payout.failed" -> handlePayoutFailed(event.objectId());
            default -> log.debug("Unhandled Stripe webhook type: {}", event.eventType());
        }
    }

    private void handlePaymentIntentSucceeded(String paymentIntentId) {
        transactionRepository.findByStripePaymentIntentId(paymentIntentId)
                .ifPresentOrElse(tx -> {
                    tx.setStatus(TransactionStatus.COMPLETED);
                    transactionRepository.save(tx);

                    publishOutboxEvent(tx.getId(), "TRANSACTION", "CASH_IN_COMPLETED", 1,
                            Map.of("transactionId", tx.getId().toString(),
                                    "userId", tx.getSenderId().toString(),
                                    "amount", tx.getAmount().toPlainString(),
                                    "currency", tx.getCurrency()));

                    log.info("Cash-in completed via Stripe webhook: txId={}", tx.getId());
                }, () -> log.warn("No transaction found for payment_intent.succeeded: paymentIntentId={}",
                        paymentIntentId));
    }

    private void handlePaymentIntentFailed(String paymentIntentId) {
        transactionRepository.findByStripePaymentIntentId(paymentIntentId)
                .ifPresentOrElse(tx -> {
                    tx.setStatus(TransactionStatus.FAILED);
                    transactionRepository.save(tx);

                    publishOutboxEvent(tx.getId(), "TRANSACTION", "CASH_IN_FAILED", 1,
                            Map.of("transactionId", tx.getId().toString(),
                                    "userId", tx.getSenderId().toString()));

                    log.warn("Cash-in failed via Stripe webhook: txId={}", tx.getId());
                }, () -> log.warn("No transaction found for payment_intent.payment_failed: paymentIntentId={}",
                        paymentIntentId));
    }

    private void handlePayoutPaid(String payoutId) {
        transactionRepository.findByStripePayoutId(payoutId)
                .ifPresentOrElse(tx -> {
                    tx.setStatus(TransactionStatus.COMPLETED);
                    transactionRepository.save(tx);

                    publishOutboxEvent(tx.getId(), "TRANSACTION", "CASH_OUT_COMPLETED", 1,
                            Map.of("transactionId", tx.getId().toString(),
                                    "userId", tx.getSenderId().toString(),
                                    "amount", tx.getAmount().toPlainString(),
                                    "currency", tx.getCurrency()));

                    log.info("Cash-out completed via payout.paid webhook: txId={}", tx.getId());
                }, () -> log.warn("No transaction found for payout.paid: payoutId={}", payoutId));
    }

    private void handlePayoutFailed(String payoutId) {
        transactionRepository.findByStripePayoutId(payoutId)
                .ifPresentOrElse(tx -> {
                    tx.setStatus(TransactionStatus.FAILED);
                    transactionRepository.save(tx);

                    publishOutboxEvent(tx.getId(), "TRANSACTION", "CASH_OUT_FAILED", 1,
                            Map.of("transactionId", tx.getId().toString(),
                                    "userId", tx.getSenderId().toString()));

                    log.warn("Cash-out failed via payout.failed webhook: txId={}", tx.getId());
                }, () -> log.warn("No transaction found for payout.failed: payoutId={}", payoutId));
    }

    @Override
    public Page<TransactionResponse> getMyTransactions(UUID userId, Pageable pageable) {
        return transactionRepository.findBySenderIdOrReceiverId(userId, userId, pageable)
                .map(TransactionResponse::from);
    }

    @Override
    public TransactionResponse getTransactionById(UUID transactionId, UUID callerId) {
        TransactionRecord tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        if (!tx.getSenderId().equals(callerId) &&
                (tx.getReceiverId() == null || !tx.getReceiverId().equals(callerId))) {
            throw new TransactionNotFoundException(transactionId); // 404 not 403 (avoid info leak)
        }

        return TransactionResponse.from(tx);
    }

    @Override
    @Transactional
    public void handleFraudDecision(UUID transactionId, boolean approved) {
        TransactionRecord tx = transactionRepository.findById(transactionId).orElse(null);
        if (tx == null) {
            log.warn("handleFraudDecision: transaction not found for txId={} — skipping (likely already cleaned up)",
                    transactionId);
            return;
        }

        if (tx.getStatus() != TransactionStatus.RESERVED && tx.getStatus() != TransactionStatus.INITIATED) {
            log.warn("handleFraudDecision: transaction txId={} is already in terminal status={} — skipping",
                    transactionId, tx.getStatus());
            return;
        }

        if (approved) {
            tx.setStatus(TransactionStatus.COMPLETED);
            publishOutboxEvent(tx.getId(), "TRANSACTION", "TRANSFER_COMPLETED", 1,
                    Map.of("transactionId", tx.getId().toString(),
                            "senderId", tx.getSenderId().toString(),
                            "receiverId", tx.getReceiverId().toString(),
                            "amount", tx.getAmount().toPlainString(),
                            "currency", tx.getCurrency()));
            log.info("P2P transfer approved by fraud, completing: txId={}", tx.getId());
        } else {
            tx.setStatus(TransactionStatus.FAILED);
            // For release, we must release the amount + fee back to the sender
            publishOutboxEvent(tx.getId(), "TRANSACTION", "TRANSFER_FAILED", 1,
                    Map.of("transactionId", tx.getId().toString(),
                            "senderId", tx.getSenderId().toString(),
                            "amount", tx.getAmount().add(tx.getFeeAmount()).toPlainString(),
                            "currency", tx.getCurrency()));
            log.info("P2P transfer rejected by fraud, failing: txId={}", tx.getId());
        }
        transactionRepository.save(tx);
    }

    private void guardDuplicate(UUID idempotencyKey) {
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new DuplicateTransactionException(idempotencyKey);
        }
    }

    private void publishOutboxEvent(UUID aggregateId, String aggregateType,
            String eventType, int version, Map<String, Object> payload) {
        try {
            GenericDomainEvent domainEvent = new GenericDomainEvent(
                    eventType,
                    version,
                    aggregateId,
                    aggregateType,
                    payload);

            String payloadJson = objectMapper.writeValueAsString(domainEvent);
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateId(domainEvent.getAggregateId())
                    .aggregateType(domainEvent.getAggregateType())
                    .eventType(domainEvent.getEventType())
                    .eventVersion(domainEvent.getEventVersion())
                    .payload(payloadJson)
                    .idempotencyKey(domainEvent.getIdempotencyKey())
                    .published(false)
                    .build());
        } catch (Exception e) {
            log.error("Failed to serialize outbox event: eventType={}", eventType, e);
            throw new RuntimeException("Outbox serialization failed", e);
        }
    }
}
