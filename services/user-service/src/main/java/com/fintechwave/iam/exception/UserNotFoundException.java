package com.fintechwave.iam.exception;

import java.util.UUID;

import com.fintechwave.core.exception.ResourceNotFoundException;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(UUID keycloakId) {
        super("User profile not found for keycloakId: " + keycloakId);
    }

    public UserNotFoundException(String email) {
        super("User profile not found for email: " + email);
    }

    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public static UserNotFoundException withMessage(String message) {
        return new UserNotFoundException(message, null);
    }
}
