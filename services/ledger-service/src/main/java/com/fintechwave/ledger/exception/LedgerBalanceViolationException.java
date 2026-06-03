package com.fintechwave.ledger.exception;

import com.fintechwave.core.exception.BaseServiceException;

public class LedgerBalanceViolationException extends BaseServiceException {
    public LedgerBalanceViolationException(String message) {
        super(message, "LEDGER_BALANCE_VIOLATION");
    }
}