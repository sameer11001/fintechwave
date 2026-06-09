package com.fintechwave.fraud.service;

import com.fintechwave.fraud.dto.FraudDecisionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface IFraudService {

    /**
     * Evaluate a transfer for fraud risk.
     * Runs velocity checks via Redis and configurable rule engine.
     * Publishes TransactionApproved or TransactionFlagged via outbox.
     *
     * @param transactionId  the transaction to evaluate
     * @param userId         the initiating user
     * @param amount         transaction amount
     * @param currency       ISO-4217 currency code
     * @param idempotencyKey consumer idempotency key — deduplicates re-deliveries
     */
    void evaluate(UUID transactionId, UUID userId, BigDecimal amount, String currency, UUID idempotencyKey);

    /**
     * Paginated fraud decision history for a user (ADMIN use).
     */
    Page<FraudDecisionResponse> getUserDecisions(UUID userId, Pageable pageable);
}
