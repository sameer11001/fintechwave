package com.fintechwave.payment;

public record CardPaymentIntent(
        String paymentIntentId,
        String clientSecret,
        String status
) {}
