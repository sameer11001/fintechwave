package com.fintechwave.transaction.domain.enums;

public enum TransactionType {
    /** Internal wallet-to-wallet transfer between two platform users. */
    P2P,
    /** User funds their wallet via card (Stripe Payment Intent). */
    CASH_IN,
    /** User withdraws funds to their original card (Stripe Instant Payout). */
    CASH_OUT,
    /** User pays a biller via third-party aggregator API. */
    BILL_PAY
}
