package com.fintechwave.ledger.exception;

import com.fintechwave.core.exception.BaseServiceException;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientBalanceException extends BaseServiceException {
    public InsufficientBalanceException(UUID accountId, BigDecimal current, BigDecimal requested) {
        super("Insufficient balance on account " + accountId +
              ": current=" + current + " requested=" + requested,
              "INSUFFICIENT_BALANCE");
    }
}
