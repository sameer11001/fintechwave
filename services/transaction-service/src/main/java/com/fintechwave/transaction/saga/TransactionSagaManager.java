package com.fintechwave.transaction.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.core.messaging.OutboxEventEnvelope;
import com.fintechwave.core.messaging.OutboxEventHelper;
import com.fintechwave.transaction.domain.entity.OutboxEvent;
import com.fintechwave.transaction.domain.entity.TransactionSagaState;
import com.fintechwave.transaction.domain.enums.TransactionSagaStep;
import com.fintechwave.transaction.exception.SagaNotFoundException;
import com.fintechwave.transaction.repository.OutboxEventRepository;
import com.fintechwave.transaction.repository.TransactionSagaStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionSagaManager {

    private final TransactionSagaStateRepository repository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void onP2PInitiated(UUID txId, UUID senderId, UUID receiverId,
            BigDecimal amount, BigDecimal fee, String currency) {
        TransactionSagaState state = TransactionSagaState.builder()
                .transactionId(txId)
                .sagaType("P2P")
                .currentStep(TransactionSagaStep.FUNDS_RESERVED)
                .senderId(senderId)
                .receiverId(receiverId)
                .amount(amount)
                .feeAmount(fee)
                .currency(currency)
                .build();
        repository.save(state);
        log.debug("Saga[P2P] FUNDS_RESERVED txId={}", txId);
    }

    @Transactional
    public void onFraudApproved(UUID txId) {
        TransactionSagaState saga = loadAndGuard(txId, TransactionSagaStep.FUNDS_RESERVED);
        saga.setCurrentStep(TransactionSagaStep.FRAUD_APPROVED);
        repository.save(saga);

        // Saga owns the command to the ledger
        publishOutboxEvent(txId, "TRANSACTION", "TRANSFER_COMPLETED", 1,
                Map.of("transactionId", txId.toString(),
                        "senderId", saga.getSenderId().toString(),
                        "receiverId", saga.getReceiverId().toString(),
                        "amount", saga.getAmount().toPlainString(),
                        "feeAmount", saga.getFeeAmount().toPlainString(),
                        "currency", saga.getCurrency()));

        log.info("Saga[P2P] FRAUD_APPROVED → published TRANSFER_COMPLETED txId={}", txId);
    }

    @Transactional
    public void onFraudRejected(UUID txId, String reason) {
        TransactionSagaState saga = loadAndGuard(txId, TransactionSagaStep.FUNDS_RESERVED);
        saga.setCurrentStep(TransactionSagaStep.COMPENSATING_FUNDS_RELEASE);
        saga.setFailureReason(reason);
        repository.save(saga);

        // Saga owns the compensation command
        publishOutboxEvent(txId, "TRANSACTION", "TRANSFER_FAILED", 1,
                Map.of("transactionId", txId.toString(),
                        "senderId", saga.getSenderId().toString(),
                        "amount", saga.getAmount().add(saga.getFeeAmount()).toPlainString(),
                        "currency", saga.getCurrency()));

        log.warn("Saga[P2P] COMPENSATING_FUNDS_RELEASE → published TRANSFER_FAILED txId={} reason={}",
                txId, reason);
    }

    @Transactional
    public void onCashInInitiated(UUID txId, UUID userId, BigDecimal amount, String currency) {
        repository.save(TransactionSagaState.builder()
                .transactionId(txId)
                .sagaType("CASH_IN")
                .currentStep(TransactionSagaStep.PENDING_STRIPE)
                .senderId(userId)
                .amount(amount)
                .currency(currency)
                .build());
        log.debug("Saga[CASH_IN] PENDING_STRIPE txId={}", txId);
    }

    @Transactional
    public void onStripeSuccess(UUID txId) {
        TransactionSagaState saga = loadForStripe(txId);
        saga.setCurrentStep(TransactionSagaStep.WAITING_LEDGER);
        repository.save(saga);

        String eventType = "CASH_IN".equals(saga.getSagaType()) ? "CASH_IN_COMPLETED" : "CASH_OUT_COMPLETED";
        publishOutboxEvent(txId, "TRANSACTION", eventType, 1,
                Map.of("transactionId", txId.toString(),
                        "userId", saga.getSenderId().toString(),
                        "amount", saga.getAmount().toPlainString(),
                        "currency", saga.getCurrency()));

        log.info("Saga[{}] WAITING_LEDGER → published {} txId={}", saga.getSagaType(), eventType, txId);
    }

    @Transactional
    public void onStripeFailed(UUID txId, String reason) {
        TransactionSagaState saga = loadForStripe(txId);
        saga.setFailureReason(reason);

        if ("CASH_OUT".equals(saga.getSagaType())) {
            // Funds were reserved in ledger before Stripe — must release them
            saga.setCurrentStep(TransactionSagaStep.COMPENSATING_FUNDS_RELEASE);
            repository.save(saga);

            publishOutboxEvent(txId, "TRANSACTION", "CASH_OUT_FAILED", 1,
                    Map.of("transactionId", txId.toString(),
                            "userId", saga.getSenderId().toString(),
                            "amount", saga.getAmount().add(saga.getFeeAmount()).toPlainString(),
                            "currency", saga.getCurrency()));

            log.warn("Saga[CASH_OUT] COMPENSATING_FUNDS_RELEASE → published CASH_OUT_FAILED txId={}", txId);
        } else {
            // Cash-In: no ledger reservation was made — just mark failed
            saga.setCurrentStep(TransactionSagaStep.FAILED);
            repository.save(saga);
            log.warn("Saga[CASH_IN] FAILED txId={} reason={}", txId, reason);
        }
    }

    @Transactional
    public void onCashOutInitiated(UUID txId, UUID userId, BigDecimal amount, BigDecimal fee, String currency) {
        repository.save(TransactionSagaState.builder()
                .transactionId(txId)
                .sagaType("CASH_OUT")
                .currentStep(TransactionSagaStep.PENDING_STRIPE)
                .senderId(userId)
                .amount(amount)
                .feeAmount(fee)
                .currency(currency)
                .build());
        log.debug("Saga[CASH_OUT] PENDING_STRIPE txId={}", txId);
    }

    @Transactional
    public void onLedgerCommitted(UUID txId) {
        TransactionSagaState saga = load(txId);
        saga.setCurrentStep(TransactionSagaStep.COMPLETED);
        repository.save(saga);
        log.info("Saga[{}] COMPLETED txId={}", saga.getSagaType(), txId);
    }

    @Transactional
    public void onFundsReleased(UUID txId) {
        TransactionSagaState saga = loadAndGuard(txId, TransactionSagaStep.COMPENSATING_FUNDS_RELEASE);
        saga.setCurrentStep(TransactionSagaStep.COMPENSATION_COMPLETE);
        repository.save(saga);
        log.info("Saga[{}] COMPENSATION_COMPLETE txId={}", saga.getSagaType(), txId);
    }

    // DLT FALLBACK: STRIPE REFUND
    @Transactional
    public void onStripeRefundInitiated(UUID txId, String refundReason) {
        repository.findById(txId).ifPresent(saga -> {
            saga.setCurrentStep(TransactionSagaStep.STRIPE_REFUND_INITIATED);
            saga.setFailureReason(refundReason);
            repository.save(saga);
            log.warn("Saga[{}] STRIPE_REFUND_INITIATED txId={} reason={}", saga.getSagaType(), txId, refundReason);
        });
    }

    /**
     * Called by DltCompensationConsumer for TRANSFER_COMPLETED or
     * CASH_OUT_COMPLETED
     * DLT events where ledger rollback was published as compensation.
     */
    @Transactional
    public void onDltCompensationPublished(UUID txId, String failureReason) {
        repository.findById(txId).ifPresent(saga -> {
            saga.setCurrentStep(TransactionSagaStep.COMPENSATING_FUNDS_RELEASE);
            saga.setFailureReason(failureReason);
            repository.save(saga);
            log.warn("Saga[{}] DLT COMPENSATING_FUNDS_RELEASE txId={}", saga.getSagaType(), txId);
        });
    }

    // ─────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────

    private TransactionSagaState load(UUID txId) {
        return repository.findById(txId)
                .orElseThrow(() -> new SagaNotFoundException(txId));
    }

    private TransactionSagaState loadAndGuard(UUID txId, TransactionSagaStep expected) {
        TransactionSagaState saga = load(txId);
        if (saga.getCurrentStep() != expected) {
            log.warn("Saga[{}] step mismatch for txId={}: expected={} actual={}",
                    saga.getSagaType(), txId, expected, saga.getCurrentStep());
        }
        return saga;
    }

    /**
     * Load for stripe steps — accepts both PENDING_STRIPE (normal) and
     * FUNDS_RESERVED guard.
     */
    private TransactionSagaState loadForStripe(UUID txId) {
        return repository.findById(txId)
                .orElseThrow(() -> new SagaNotFoundException(txId));
    }

    private void publishOutboxEvent(UUID aggregateId, String aggregateType,
            String eventType, int version, Map<String, Object> payload) {
        OutboxEventEnvelope env = OutboxEventHelper.prepare(
                objectMapper, eventType, version, aggregateId, aggregateType, payload);
        outboxEventRepository.save(OutboxEvent.from(env));
    }
}
