package com.fintechwave.kyc.exception;

import org.springframework.http.HttpStatus;

public class DocumentStorageException extends KycServiceException {
    public DocumentStorageException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    public DocumentStorageException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
        initCause(cause);
    }
}
