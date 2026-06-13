package com.fintechwave.transaction.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TransactionNotFoundException extends TransactionServiceException {

    public TransactionNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found: " + id);
    }
}
