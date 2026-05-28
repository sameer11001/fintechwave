package com.fintechwave.iam.exception;

public class DuplicateResourceException extends BaseServiceException {

    public DuplicateResourceException(String resource, String field, Object value) {
        super(
            String.format("%s already exists with %s: %s", resource, field, value),
            "DUPLICATE_RESOURCE"
        );
    }
}
