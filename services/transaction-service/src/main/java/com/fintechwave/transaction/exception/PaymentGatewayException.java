package com.fintechwave.transaction.exception;

import org.springframework.http.HttpStatus;

public class PaymentGatewayException extends TransactionServiceException {

    public PaymentGatewayException(String message) {
        super(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", message, cause);
    }
}
