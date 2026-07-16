package com.fintechwave.transaction.exception;

import com.fintechwave.core.exception.BaseServiceException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class SagaNotFoundException extends BaseServiceException {
    public SagaNotFoundException(UUID transactionId) {
        super(HttpStatus.NOT_FOUND, "SAGA_NOT_FOUND", "Saga state not found for transaction " + transactionId);
    }
}
