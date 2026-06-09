package com.fintechwave.events;

import java.util.UUID;

public class GenericDomainEvent extends BaseEvent {
    
    public GenericDomainEvent(
            String eventType,
            int eventVersion,
            UUID aggregateId,
            String aggregateType,
            Object payload) {
        super(eventType, eventVersion, aggregateId, aggregateType, payload);
    }
}
