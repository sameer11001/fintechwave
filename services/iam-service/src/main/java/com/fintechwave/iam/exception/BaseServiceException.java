package com.fintechwave.iam.exception;

public abstract class BaseServiceException extends RuntimeException {

    private final String errorCode;

    protected BaseServiceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
