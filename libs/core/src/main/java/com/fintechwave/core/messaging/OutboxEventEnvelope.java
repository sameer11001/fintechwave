package com.fintechwave.core.messaging;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventEnvelope(
                UUID aggregateId,
                String aggregateType,
                String eventType,
                int eventVersion,
                UUID idempotencyKey,
                Instant occurredAt,
                String payloadJson) {
}
