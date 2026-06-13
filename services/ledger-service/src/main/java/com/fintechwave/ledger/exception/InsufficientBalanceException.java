package com.fintechwave.ledger.exception;

import com.fintechwave.core.exception.BaseServiceException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientBalanceException extends BaseServiceException {

    public InsufficientBalanceException(UUID accountId, BigDecimal current, BigDecimal requested) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_BALANCE",
                "Insufficient balance on account " + accountId +
                        ": current=" + current + " requested=" + requested);
    }
}
