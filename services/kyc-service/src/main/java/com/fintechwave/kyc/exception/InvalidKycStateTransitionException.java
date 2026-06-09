package com.fintechwave.kyc.exception;

import org.springframework.http.HttpStatus;

public class InvalidKycStateTransitionException extends KycServiceException {
    public InvalidKycStateTransitionException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
