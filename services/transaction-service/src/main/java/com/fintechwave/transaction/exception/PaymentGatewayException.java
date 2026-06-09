package com.fintechwave.transaction.exception;

import org.springframework.http.HttpStatus;

public class PaymentGatewayException extends TransactionServiceException {
    public PaymentGatewayException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY);
        initCause(cause);
    }
}
