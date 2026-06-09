package com.fintechwave.transaction.service;

import com.fintechwave.transaction.domain.enums.TransactionType;

import java.math.BigDecimal;

/**
 * Pluggable fee calculation service.
 * Returns the fee amount for a given transaction type and amount.
 * Config-driven, hot-reloadable via Config Server @RefreshScope.
 */
public interface IFeeService {

    /**
     * Calculates the fee for a transaction.
     *
     * @param transactionType Type of transaction (P2P, CASH_IN, CASH_OUT, BILL_PAY)
     * @param amount          Transaction amount
     * @param currency        ISO currency code
     * @return Fee amount (may be ZERO for certain transaction types or tiers)
     */
    BigDecimal calculateFee(TransactionType transactionType, BigDecimal amount, String currency);
}
