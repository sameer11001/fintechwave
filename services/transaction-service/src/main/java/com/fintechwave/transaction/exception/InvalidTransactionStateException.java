package com.fintechwave.transaction.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransactionStateException extends TransactionServiceException {

    public InvalidTransactionStateException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_TRANSACTION_STATE", message);
    }
}
