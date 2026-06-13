package com.fintechwave.transaction.exception;

import com.fintechwave.core.exception.BaseServiceException;
import org.springframework.http.HttpStatus;

public abstract class TransactionServiceException extends BaseServiceException {

    protected TransactionServiceException(HttpStatus status, String errorCode, String message) {
        super(status, errorCode, message);
    }

    protected TransactionServiceException(HttpStatus status, String errorCode,
            String message, Throwable cause) {
        super(status, errorCode, message, cause);
    }
}
