package com.fintechwave.iam.messaging;

import com.fintechwave.iam.domain.entity.OutboxEvent;
import com.fintechwave.iam.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository.findUnpublished();
        if (pending.isEmpty())
            return;

        List<UUID> published = new ArrayList<>();

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload()).get();
                published.add(event.getId());
                log.debug("Outbox event published: eventId={} type={}", event.getId(), event.getEventType());
            } catch (Exception ex) {
                log.error("Outbox relay failed: eventId={} topic={} error={}",
                        event.getId(), event.getTopic(), ex.getMessage());
            }
        }

        if (!published.isEmpty()) {
            published.forEach(outboxEventRepository::markPublished);
            log.info("Outbox relay: marked {} event(s) as published", published.size());
        }
    }
}
