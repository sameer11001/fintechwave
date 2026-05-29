package com.fintechwave.events;

@FunctionalInterface
public interface DomainEventPublisher {
    void publish(DomainEvent event);
}
