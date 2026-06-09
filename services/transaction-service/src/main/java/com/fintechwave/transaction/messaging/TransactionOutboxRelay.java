package com.fintechwave.transaction.messaging;

import com.fintechwave.transaction.domain.entity.OutboxEvent;
import com.fintechwave.transaction.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Transaction outbox relay — publishes pending events to Kafka every second.
 * Guarantees at-least-once delivery for all transaction events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionOutboxRelay {

    private static final String TOPIC = "tx.transaction-events";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository.findTop50ByPublishedFalseOrderByOccurredAtAsc();

        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), event.getPayload()).get();
                event.setPublished(true);
                outboxEventRepository.save(event);
                log.debug("Transaction event relayed: type={} txId={}", event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to relay transaction event: id={} type={}", event.getId(), event.getEventType(), e);
                throw new RuntimeException("Kafka send failed", e);
            }
        }

        log.info("Transaction outbox relay: {} events published", pending.size());
    }
}
