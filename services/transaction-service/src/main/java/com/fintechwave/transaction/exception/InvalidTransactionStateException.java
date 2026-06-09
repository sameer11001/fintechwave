package com.fintechwave.transaction.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransactionStateException extends TransactionServiceException {
    public InvalidTransactionStateException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
