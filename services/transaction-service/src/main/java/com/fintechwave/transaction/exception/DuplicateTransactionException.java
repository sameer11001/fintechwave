package com.fintechwave.transaction.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DuplicateTransactionException extends TransactionServiceException {

    public DuplicateTransactionException(UUID idempotencyKey) {
        super(HttpStatus.CONFLICT, "DUPLICATE_TRANSACTION",
                "Transaction already exists for idempotencyKey=" + idempotencyKey);
    }
}
