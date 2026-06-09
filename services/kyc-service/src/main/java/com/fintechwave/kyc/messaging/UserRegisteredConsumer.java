package com.fintechwave.kyc.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.kyc.domain.entity.ProcessedEvent;
import com.fintechwave.kyc.repository.ProcessedEventRepository;
import com.fintechwave.kyc.service.IKycApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumes UserRegistered events from iam.user-events topic.
 * Creates a KYC shell for each new user — idempotent via ProcessedEvent guard.
 *
 * Event contract from Event Ownership Table:
 * Topic: iam.user-events
 * Producer: user-service
 * Consumer: kyc-service (this class)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredConsumer {

    private final IKycApplicationService kycApplicationService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "iam.user-events", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onUserRegistered(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);

            // Only process UserRegistered events
            String eventType = event.path("eventType").asText();
            if (!"USER_REGISTERED".equals(eventType)) {
                ack.acknowledge();
                return;
            }

            UUID idempotencyKey = UUID.fromString(event.path("idempotencyKey").asText());

            if (processedEventRepository.existsByIdempotencyKey(idempotencyKey)) {
                log.warn("Duplicate UserRegistered event, skipping: idempotencyKey={}", idempotencyKey);
                ack.acknowledge();
                return;
            }

            UUID userId = UUID.fromString(event.path("payload").path("keycloakId").asText());
            kycApplicationService.createKycShell(userId);

            processedEventRepository.save(
                    ProcessedEvent.builder()
                            .idempotencyKey(idempotencyKey)
                            .processedAt(Instant.now())
                            .build());

            log.info("UserRegistered processed: userId={}", userId);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process UserRegistered event", e);
            // Do not acknowledge — will be retried by Kafka
        }
    }
}
