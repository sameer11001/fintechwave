package com.fintechwave.iam.exception;

@Deprecated(since = "1.1.0", forRemoval = true)
public abstract class BaseServiceException extends com.fintechwave.core.exception.BaseServiceException {

    protected BaseServiceException(org.springframework.http.HttpStatus status,
            String errorCode, String message) {
        super(status, errorCode, message);
    }
}
