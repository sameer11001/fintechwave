package com.fintechwave.iam.exception;

public abstract class ResourceNotFoundException extends BaseServiceException {

    protected ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND");
    }
}
