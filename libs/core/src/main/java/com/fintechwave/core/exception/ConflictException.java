package com.fintechwave.core.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends BaseServiceException {

    public ConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }

    public ConflictException(String resource, String field, Object value) {
        super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE",
                String.format("%s already exists with %s: %s", resource, field, value));
    }
}
