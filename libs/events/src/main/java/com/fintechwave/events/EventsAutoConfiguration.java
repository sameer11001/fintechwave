package com.fintechwave.events;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class EventsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DomainEventPublisher domainEventPublisher(ApplicationEventPublisher publisher) {
        return publisher::publishEvent;
    }
}
