package com.fintechwave.kyc.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class KycApplicationNotFoundException extends KycServiceException {
    public KycApplicationNotFoundException(UUID userId) {
        super("KYC application not found for userId=" + userId, HttpStatus.NOT_FOUND);
    }
    public KycApplicationNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
