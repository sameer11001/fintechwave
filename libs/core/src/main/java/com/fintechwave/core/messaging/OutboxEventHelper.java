package com.fintechwave.core.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.events.GenericDomainEvent;

import java.util.UUID;

public final class OutboxEventHelper {

    private OutboxEventHelper() {
    }

    /**
     * Serializes a {@link GenericDomainEvent} to JSON, rethrowing serialization
     * failures as an unchecked {@code RuntimeException} with a consistent message.
     */
    public static String toJson(ObjectMapper mapper, GenericDomainEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Outbox event serialization failed for eventType=" + event.getEventType(), e);
        }
    }

    /**
     * Builds a new {@link GenericDomainEvent} from the given parameters.
     * Convenience shorthand used in every service's publishOutboxEvent method.
     */
    public static GenericDomainEvent buildDomainEvent(
            String eventType, int version,
            UUID aggregateId, String aggregateType,
            Object payload) {
        return new GenericDomainEvent(eventType, version, aggregateId, aggregateType, payload);
    }
}
