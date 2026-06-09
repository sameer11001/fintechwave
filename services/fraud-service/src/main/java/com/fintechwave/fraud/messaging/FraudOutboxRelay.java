package com.fintechwave.fraud.messaging;

import com.fintechwave.fraud.domain.entity.OutboxEvent;
import com.fintechwave.fraud.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fraud outbox relay — publishes pending fraud.risk-events to Kafka every second.
 * Guarantees at-least-once delivery of TransactionApproved / TransactionFlagged events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudOutboxRelay {

    private static final String TOPIC = "fraud.risk-events";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository
                .findTop50ByPublishedFalseOrderByOccurredAtAsc();

        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), event.getPayload());
                event.setPublished(true);
                outboxEventRepository.save(event);
                log.debug("Fraud event relayed: type={} txId={}", event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to relay fraud event: id={} type={}", event.getId(), event.getEventType(), e);
            }
        }

        log.info("Fraud outbox relay: {} events published", pending.size());
    }
}
