package com.fintechwave.iam.exception;

public class InvalidTokenException extends BaseServiceException {

    public InvalidTokenException(String message) {
        super(message, "INVALID_TOKEN");
    }
}
