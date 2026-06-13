package com.fintechwave.ledger.exception;

import com.fintechwave.core.exception.BaseServiceException;
import org.springframework.http.HttpStatus;

public class LedgerBalanceViolationException extends BaseServiceException {

    public LedgerBalanceViolationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "LEDGER_BALANCE_VIOLATION", message);
    }
}