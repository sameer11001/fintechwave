package com.fintechwave.transaction.domain.enums;

public enum TransactionSagaStep {
    FUNDS_RESERVED, // gRPC reserve succeeded, TRANSFER_INITIATED published
    FRAUD_APPROVED, // TRANSACTION_APPROVED consumed, TRANSFER_COMPLETED published to ledger
    FRAUD_REJECTED, // TRANSACTION_FLAGGED — saga owns compensation from here
    PENDING_STRIPE, // Cash-In/Out: waiting for Stripe confirmation
    WAITING_LEDGER, // Cash-In/Out: Stripe confirmed, waiting for ledger commit
    LEDGER_COMMITTED, // LEDGER_COMMITTED consumed (intermediate before COMPLETED)
    COMPENSATING_FUNDS_RELEASE, // Compensation in progress: TRANSFER_FAILED/CASH_OUT_FAILED published
    COMPENSATION_COMPLETE, // Ledger confirmed funds released — terminal success of compensation
    STRIPE_REFUND_INITIATED, // Stripe refund triggered (Cash-In DLT fallback)
    COMPLETED, // Terminal: fully settled
    FAILED // Terminal: failed with no compensatable actions remaining
}