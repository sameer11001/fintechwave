package com.fintechwave.events;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {

    /** Unique ID for this specific event occurrence (UUIDv4). */
    UUID getEventId();

    /**
     * Discriminator string matching the Event Ownership Table (e.g.
     * "USER_REGISTERED").
     */
    String getEventType();

    /** Schema version — increment when the payload shape changes. Start at 1. */
    int getEventVersion();

    /**
     * ID of the aggregate that produced this event (e.g. userId, transactionId).
     */
    UUID getAggregateId();

    /** Type of the aggregate (e.g. "USER", "TRANSACTION", "WALLET"). */
    String getAggregateType();

    /** Wall-clock time the event occurred (UTC). */
    Instant getOccurredAt();

    /**
     * Idempotency key — stored by every consumer in {@code processed_events}.
     * Duplicate events hit the UNIQUE constraint and are silently discarded.
     */
    UUID getIdempotencyKey();
}
