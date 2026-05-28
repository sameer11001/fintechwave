package com.fintechwave.iam.exception;

public class InvalidCredentialsException extends BaseServiceException {

    public InvalidCredentialsException() {
        super("Invalid credentials", "INVALID_CREDENTIALS");
    }

    public InvalidCredentialsException(String message) {
        super(message, "INVALID_CREDENTIALS");
    }
}
