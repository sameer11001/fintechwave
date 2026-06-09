package com.fintechwave.transaction.exception;

import org.springframework.http.HttpStatus;

public abstract class TransactionServiceException extends RuntimeException {

    private final HttpStatus httpStatus;

    protected TransactionServiceException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
