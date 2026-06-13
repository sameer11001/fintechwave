package com.fintechwave.core.exception;

import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends BaseServiceException {

    public InvalidStateTransitionException(String entity, Object fromState, Object toState) {
        super(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
                String.format("Cannot transition %s from %s to %s", entity, fromState, toState));
    }

    public InvalidStateTransitionException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
