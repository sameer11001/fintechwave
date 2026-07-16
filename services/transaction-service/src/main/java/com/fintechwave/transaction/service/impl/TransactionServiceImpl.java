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
import com.fintechwave.transaction.mapper.TransactionMapper;
import com.fintechwave.transaction.repository.OutboxEventRepository;
import com.fintechwave.transaction.repository.TransactionRepository;
import com.fintechwave.transaction.saga.TransactionSagaManager;
import com.fintechwave.transaction.service.IFeeService;
import com.fintechwave.transaction.service.ITransactionService;
import com.fintechwave.transaction.service.KycPolicyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import com.fintechwave.core.observability.BusinessContextMdc;
import com.fintechwave.core.messaging.OutboxEventEnvelope;
import com.fintechwave.core.messaging.OutboxEventHelper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
    private final MeterRegistry meterRegistry;
    private final TransactionMapper transactionMapper;
    private final KycPolicyService kycPolicyService;
    private final TransactionSagaManager sagaManager;

    private Timer p2pTransferTimer;

    @PostConstruct
    void initMetrics() {
        this.p2pTransferTimer = Timer.builder("fintechwave.p2p.transfer.duration")
                .description("End-to-end duration of P2P transfer initiation")
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public TransactionResponse initiateP2PTransfer(UUID senderId, InitiateTransferRequest request) {
        Span currentSpan = Span.current();
        currentSpan.setAttribute("fintechwave.transaction.type", "P2P");
        currentSpan.setAttribute("fintechwave.transaction.currency", request.currency());
        currentSpan.setAttribute("fintechwave.user.sender_id", senderId.toString());

        return p2pTransferTimer.record(() -> {
            try (var ctx = BusinessContextMdc.of(senderId, null, "P2P_TRANSFER_INITIATED")) {
                kycPolicyService.enforce(senderId, TransactionType.P2P);
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

                MDC.put("transaction_id", tx.getId().toString());
                currentSpan.setAttribute("fintechwave.transaction.id", tx.getId().toString());

                try {
                    ledgerGrpcClient.reserveFundsSync(tx.getId(), senderId, request.amount().add(fee),
                            request.currency());
                } catch (Exception reserveEx) {
                    log.warn("Ledger reservation failed for txId={} — aborting: {}", tx.getId(),
                            reserveEx.getMessage());
                    tx.transition(TransactionStatus.FAILED);
                    transactionRepository.save(tx);
                    throw reserveEx;
                }

                tx.transition(TransactionStatus.RESERVED);
                transactionRepository.save(tx);

                // Publish TRANSFER_INITIATED to trigger fraud-service evaluation
                publishOutboxEvent(tx.getId(), "TRANSACTION", "TRANSFER_INITIATED", 1,
                        Map.of(
                                "transactionId", tx.getId().toString(),
                                "senderId", senderId.toString(),
                                "receiverId", request.receiverId().toString(),
                                "amount", request.amount().toPlainString(),
                                "currency", request.currency(),
                                "feeAmount", fee.toPlainString()));

                // Saga records the state — further compensation events are owned by sagaManager
                sagaManager.onP2PInitiated(tx.getId(), senderId, request.receiverId(), request.amount(), fee,
                        request.currency());

                log.info("P2P transfer initiated and reserved: txId={} senderId={} amount={} {}", tx.getId(),
                        senderId, request.amount(),
                        request.currency());

                Counter.builder("fintechwave.transaction.initiated")
                        .description("Transactions successfully initiated, by type")
                        .tags("type", "P2P", "currency", request.currency())
                        .register(meterRegistry)
                        .increment();

                return transactionMapper.toResponse(tx);
            } catch (Exception e) {
                Counter.builder("fintechwave.transaction.failed")
                        .description("Transaction failures, by type and reason")
                        .tags("type", "P2P", "currency", request.currency(), "reason", e.getClass().getSimpleName())
                        .register(meterRegistry)
                        .increment();
                throw e;
            }
        });
    }

    @Override
    @Transactional
    public TransactionResponse initiateCashIn(UUID userId, CashInRequest request) {
        Span currentSpan = Span.current();
        currentSpan.setAttribute("fintechwave.transaction.type", "CASH_IN");
        currentSpan.setAttribute("fintechwave.transaction.currency", request.currency());
        currentSpan.setAttribute("fintechwave.user.sender_id", userId.toString());

        try (var ctx = BusinessContextMdc.of(userId, null, "CASH_IN_INITIATED")) {
            kycPolicyService.enforce(userId, TransactionType.CASH_IN);
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

            MDC.put("transaction_id", tx.getId().toString());
            currentSpan.setAttribute("fintechwave.transaction.id", tx.getId().toString());

            sagaManager.onCashInInitiated(tx.getId(), userId, request.amount(), request.currency());

            log.info("Cash-in initiated: txId={} userId={} stripeIntentId={}", tx.getId(), userId,
                    intent.paymentIntentId());

            Counter.builder("fintechwave.transaction.initiated")
                    .description("Transactions successfully initiated, by type")
                    .tags("type", "CASH_IN", "currency", request.currency())
                    .register(meterRegistry)
                    .increment();

            return transactionMapper.toResponse(tx);
        } catch (Exception e) {
            Counter.builder("fintechwave.transaction.failed")
                    .description("Transaction failures, by type and reason")
                    .tags("type", "CASH_IN", "currency", request.currency(), "reason", e.getClass().getSimpleName())
                    .register(meterRegistry)
                    .increment();
            throw e;
        }
    }

    @Override
    @Transactional
    public TransactionResponse initiateCashOut(UUID userId, CashOutRequest request) {
        Span currentSpan = Span.current();
        currentSpan.setAttribute("fintechwave.transaction.type", "CASH_OUT");
        currentSpan.setAttribute("fintechwave.transaction.currency", request.currency());
        currentSpan.setAttribute("fintechwave.user.sender_id", userId.toString());

        try (var ctx = BusinessContextMdc.of(userId, null, "CASH_OUT_INITIATED")) {
            kycPolicyService.enforce(userId, TransactionType.CASH_OUT);
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

            MDC.put("transaction_id", tx.getId().toString());
            currentSpan.setAttribute("fintechwave.transaction.id", tx.getId().toString());

            try {
                ledgerGrpcClient.reserveFundsSync(tx.getId(), userId, request.amount().add(fee), request.currency());
            } catch (Exception reserveEx) {
                log.warn("Ledger reservation failed for cash-out txId={} — aborting: {}", tx.getId(),
                        reserveEx.getMessage());
                tx.transition(TransactionStatus.FAILED);
                transactionRepository.save(tx);
                throw reserveEx;
            }

            // Initiate Stripe Instant Payout
            Money money = Money.of(request.amount(), request.currency());
            PayoutResult payout = paymentGateway.initiateInstantPayout(request.stripePaymentMethodId(), money);

            tx.setStripePayoutId(payout.payoutId());
            tx.transition(TransactionStatus.RESERVED);
            transactionRepository.save(tx);

            // Publish event — ledger-service listens to commit on payout.paid webhook
            publishOutboxEvent(tx.getId(), "TRANSACTION", "CASH_OUT_INITIATED", 1,
                    Map.of(
                            "transactionId", tx.getId().toString(),
                            "userId", userId.toString(),
                            "amount", request.amount().toPlainString(),
                            "currency", request.currency(),
                            "stripePayoutId", payout.payoutId()));

            sagaManager.onCashOutInitiated(tx.getId(), userId, request.amount(), fee, request.currency());

            log.info("Cash-out initiated: txId={} userId={} stripePayoutId={}", tx.getId(), userId, payout.payoutId());

            Counter.builder("fintechwave.transaction.initiated")
                    .description("Transactions successfully initiated, by type")
                    .tags("type", "CASH_OUT", "currency", request.currency())
                    .register(meterRegistry)
                    .increment();

            return transactionMapper.toResponse(tx);
        } catch (Exception e) {
            Counter.builder("fintechwave.transaction.failed")
                    .description("Transaction failures, by type and reason")
                    .tags("type", "CASH_OUT", "currency", request.currency(), "reason", e.getClass().getSimpleName())
                    .register(meterRegistry)
                    .increment();
            throw e;
        }
    }

    @Override
    @Transactional
    public void handleStripeWebhook(String rawPayload, String signature) {
        WebhookEvent event = paymentGateway.parseAndValidateWebhook(rawPayload, signature);
        log.info("Stripe webhook received: type={}", event.eventType());

        try {
            switch (event.eventType()) {
                case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event.objectId());
                case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event.objectId());
                case "payout.paid" -> handlePayoutPaid(event.objectId());
                case "payout.failed" -> handlePayoutFailed(event.objectId());
                default -> log.debug("Unhandled Stripe webhook type: {}", event.eventType());
            }

            Counter.builder("fintechwave.stripe.webhook.received")
                    .description("Stripe webhook events received, by type and outcome")
                    .tags("event_type", event.eventType(), "outcome", "processed")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            Counter.builder("fintechwave.stripe.webhook.received")
                    .description("Stripe webhook events received, by type and outcome")
                    .tags("event_type", event.eventType(), "outcome", "failed")
                    .register(meterRegistry)
                    .increment();
            throw e;
        }
    }

    private void handlePaymentIntentSucceeded(String paymentIntentId) {
        transactionRepository.findByStripePaymentIntentId(paymentIntentId)
                .ifPresentOrElse(tx -> {
                    try (var ctx = BusinessContextMdc.of(tx.getSenderId(), tx.getId(), "CASH_IN_COMPLETED")) {
                        tx.transition(TransactionStatus.PENDING_LEDGER);
                        transactionRepository.save(tx);

                        // Saga owns publishing CASH_IN_COMPLETED to ledger and recording WAITING_LEDGER step
                        sagaManager.onStripeSuccess(tx.getId());

                        log.info("Cash-in completed via Stripe webhook: txId={}", tx.getId());
                    }
                }, () -> log.warn("No transaction found for payment_intent.succeeded: paymentIntentId={}",
                        paymentIntentId));
    }

    private void handlePaymentIntentFailed(String paymentIntentId) {
        transactionRepository.findByStripePaymentIntentId(paymentIntentId)
                .ifPresentOrElse(tx -> {
                    try (var ctx = BusinessContextMdc.of(tx.getSenderId(), tx.getId(), "CASH_IN_FAILED")) {
                        tx.transition(TransactionStatus.FAILED);
                        transactionRepository.save(tx);

                        // Saga marks FAILED (no ledger compensation needed for Cash-In)
                        sagaManager.onStripeFailed(tx.getId(), "Payment intent failed via Stripe webhook");

                        log.warn("Cash-in failed via Stripe webhook: txId={}", tx.getId());
                    }
                }, () -> log.warn("No transaction found for payment_intent.payment_failed: paymentIntentId={}",
                        paymentIntentId));
    }

    private void handlePayoutPaid(String payoutId) {
        transactionRepository.findByStripePayoutId(payoutId)
                .ifPresentOrElse(tx -> {
                    try (var ctx = BusinessContextMdc.of(tx.getSenderId(), tx.getId(), "CASH_OUT_COMPLETED")) {
                        tx.transition(TransactionStatus.PENDING_LEDGER);
                        transactionRepository.save(tx);

                        // Saga owns publishing CASH_OUT_COMPLETED to ledger and recording WAITING_LEDGER step
                        sagaManager.onStripeSuccess(tx.getId());

                        log.info("Cash-out completed via payout.paid webhook: txId={}", tx.getId());
                    }
                }, () -> log.warn("No transaction found for payout.paid: payoutId={}", payoutId));
    }

    private void handlePayoutFailed(String payoutId) {
        transactionRepository.findByStripePayoutId(payoutId)
                .ifPresentOrElse(tx -> {
                    try (var ctx = BusinessContextMdc.of(tx.getSenderId(), tx.getId(), "CASH_OUT_FAILED")) {
                        tx.transition(TransactionStatus.FAILED);
                        transactionRepository.save(tx);

                        // Saga owns the CASH_OUT_FAILED compensation event to ledger (releases reserved funds)
                        sagaManager.onStripeFailed(tx.getId(), "Payout failed via Stripe webhook");

                        log.warn("Cash-out failed via payout.failed webhook: txId={}", tx.getId());
                    }
                }, () -> log.warn("No transaction found for payout.failed: payoutId={}", payoutId));
    }

    @Override
    @Transactional
    public void handleFraudDecision(UUID transactionId, boolean approved) {
        TransactionRecord tx = transactionRepository.findById(transactionId).orElse(null);
        if (tx == null) {
            log.warn("handleFraudDecision: transaction not found for txId={} — skipping",
                    transactionId);
            return;
        }

        if (tx.getStatus() != TransactionStatus.RESERVED && tx.getStatus() != TransactionStatus.INITIATED) {
            log.warn("handleFraudDecision: txId={} already in terminal status={} — skipping",
                    transactionId, tx.getStatus());
            return;
        }

        try (var ctx = BusinessContextMdc.of(tx.getSenderId(), tx.getId(),
                approved ? "FRAUD_APPROVED" : "FRAUD_REJECTED")) {
            if (approved) {
                // Saga publishes TRANSFER_COMPLETED to ledger and updates its step to FRAUD_APPROVED
                tx.transition(TransactionStatus.PENDING_LEDGER);
                transactionRepository.save(tx);
                sagaManager.onFraudApproved(tx.getId());
                log.info("P2P fraud approved — saga commanding ledger commit: txId={}", tx.getId());
            } else {
                // Saga publishes TRANSFER_FAILED (compensation) and updates its step to COMPENSATING_FUNDS_RELEASE
                tx.transition(TransactionStatus.FAILED);
                transactionRepository.save(tx);
                sagaManager.onFraudRejected(tx.getId(), "Fraud flagged by risk engine");
                log.warn("P2P fraud rejected — saga commanding ledger release: txId={}", tx.getId());
            }
        }
    }

    @Override
    @Transactional
    public void markLedgerCommitted(UUID transactionId) {
        transactionRepository.findById(transactionId).ifPresentOrElse(tx -> {
            try (var ctx = BusinessContextMdc.of(tx.getSenderId(), tx.getId(), "LEDGER_COMMITTED")) {
                if (tx.getStatus() == TransactionStatus.PENDING_LEDGER) {
                    tx.transition(TransactionStatus.COMPLETED);
                    transactionRepository.save(tx);
                    sagaManager.onLedgerCommitted(tx.getId());
                    log.info("Ledger confirmed, transaction marked COMPLETED: txId={}", transactionId);
                } else {
                    log.warn("markLedgerCommitted called but tx is not PENDING_LEDGER: txId={} status={}",
                            transactionId,
                            tx.getStatus());
                }
            }
        }, () -> log.warn("markLedgerCommitted: transaction not found txId={}", transactionId));
    }

    private void guardDuplicate(UUID idempotencyKey) {
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new DuplicateTransactionException(idempotencyKey);
        }
    }

    private void publishOutboxEvent(UUID aggregateId, String aggregateType,
            String eventType, int version, Map<String, Object> payload) {
        OutboxEventEnvelope env = OutboxEventHelper.prepare(
                objectMapper, eventType, version, aggregateId, aggregateType, payload);
        outboxEventRepository.save(OutboxEvent.from(env));
    }

    @Scheduled(fixedDelay = 300_000)
    public void recordStuckTransactions() {
        long count = transactionRepository.countByStatusInAndCreatedAtBefore(
                List.of(TransactionStatus.INITIATED, TransactionStatus.RESERVED),
                Instant.now().minus(10, ChronoUnit.MINUTES));
        Gauge.builder("fintechwave.transaction.stuck", () -> count)
                .description("Transactions stuck in INITIATED/RESERVED > 10m")
                .register(meterRegistry);
    }
}
