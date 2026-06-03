package com.fintechwave.core.exception;

public abstract class ResourceNotFoundException extends BaseServiceException {

    protected ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND");
    }
}
