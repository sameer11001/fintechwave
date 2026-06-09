package com.fintechwave.kyc.exception;

import org.springframework.http.HttpStatus;

public abstract class KycServiceException extends RuntimeException {

    private final HttpStatus httpStatus;

    protected KycServiceException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
