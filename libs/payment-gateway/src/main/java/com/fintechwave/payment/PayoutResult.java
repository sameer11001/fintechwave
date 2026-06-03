package com.fintechwave.payment;

public record PayoutResult(
        String payoutId,
        String status,
        Long arrivalAt) {
}
