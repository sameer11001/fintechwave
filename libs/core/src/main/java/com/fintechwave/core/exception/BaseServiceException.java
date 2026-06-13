package com.fintechwave.core.exception;

import org.springframework.http.HttpStatus;

public abstract class BaseServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected BaseServiceException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    protected BaseServiceException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
