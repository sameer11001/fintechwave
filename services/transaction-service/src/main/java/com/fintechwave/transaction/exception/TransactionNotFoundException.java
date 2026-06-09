package com.fintechwave.transaction.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TransactionNotFoundException extends TransactionServiceException {
    public TransactionNotFoundException(UUID id) {
        super("Transaction not found: " + id, HttpStatus.NOT_FOUND);
    }
}
