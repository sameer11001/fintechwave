package com.fintechwave.payment;

public record WebhookEvent(
        String eventId,
        String eventType,
        String objectId,
        String rawPayload
) {}
