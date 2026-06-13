package com.fintechwave.core.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends BaseServiceException {

    public ServiceUnavailableException(String errorCode, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, errorCode, message);
    }

    public ServiceUnavailableException(String errorCode, String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, errorCode, message, cause);
    }
}
