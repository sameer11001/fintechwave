package com.fintechwave.core.exception;

public abstract class BaseServiceException extends RuntimeException {

    private final String errorCode;

    protected BaseServiceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BaseServiceException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
