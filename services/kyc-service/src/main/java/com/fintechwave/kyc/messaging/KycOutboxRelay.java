package com.fintechwave.kyc.messaging;

import com.fintechwave.kyc.domain.entity.OutboxEvent;
import com.fintechwave.kyc.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class KycOutboxRelay {

    private static final String TOPIC_KYC_EVENTS = "kyc.verification-events";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository.findTop50ByPublishedFalseOrderByOccurredAtAsc();

        if (pending.isEmpty()) {
            return;
        }

        int published = 0;
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(TOPIC_KYC_EVENTS, event.getAggregateId().toString(), event.getPayload()).get();
                event.setPublished(true);
                outboxEventRepository.save(event);
                published++;
                log.debug("Outbox event relayed: eventType={} aggregateId={}", event.getEventType(),
                        event.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to relay KYC outbox event: id={} eventType={} — will retry on next cycle",
                        event.getId(), event.getEventType(), e);
            }
        }

        if (published > 0) {
            log.info("KYC outbox relay completed: {}/{} events published", published, pending.size());
        }
    }
}
