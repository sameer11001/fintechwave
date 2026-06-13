package com.fintechwave.core.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends BaseServiceException {

    public BusinessRuleException(String errorCode, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, errorCode, message);
    }
}
