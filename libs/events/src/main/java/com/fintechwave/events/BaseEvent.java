package com.fintechwave.events;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract class BaseEvent implements DomainEvent {

    private final UUID eventId;
    private final String eventType;
    private final int eventVersion;
    private final UUID aggregateId;
    private final String aggregateType;
    private final Instant occurredAt;
    private final UUID idempotencyKey;
    private final Object payload;

    protected BaseEvent(
            String eventType,
            int eventVersion,
            UUID aggregateId,
            String aggregateType,
            Object payload) {

        this.eventId = UUID.randomUUID();
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.occurredAt = Instant.now();
        this.idempotencyKey = UUID.randomUUID();
        this.payload = payload;
    }
}
