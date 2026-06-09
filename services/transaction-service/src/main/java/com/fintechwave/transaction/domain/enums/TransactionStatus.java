package com.fintechwave.transaction.domain.enums;

/**
 * Transaction state machine.
 *
 * Valid transitions:
 * INITIATED → FRAUD_CHECK → RESERVED → COMMITTED → COMPLETED
 * FRAUD_CHECK → FLAGGED
 * COMMITTED → FAILED → REVERSED
 * RESERVED → FAILED → REVERSED (on downstream failure)
 */
public enum TransactionStatus {
    /** Transaction record created, not yet submitted for fraud check. */
    INITIATED,
    /** Submitted for fraud review (async — funds already locked for P2P). */
    FRAUD_CHECK,
    /** Funds reserved in ledger via RESERVE posting. */
    RESERVED,
    /** Ledger COMMIT posting executed — funds transferred. */
    COMMITTED,
    /** End state: transaction fully settled. */
    COMPLETED,
    /** Fraud service flagged this transaction — under manual review. */
    FLAGGED,
    /** Failed after reservation — RELEASE posting needed. */
    FAILED,
    /** RELEASE posting executed — funds returned to source. */
    REVERSED
}
