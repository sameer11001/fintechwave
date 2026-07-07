package com.fintechwave.kyc.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.core.messaging.IdempotencyGuard;
import com.fintechwave.kyc.service.IKycApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredConsumer {

    private final IKycApplicationService kycApplicationService;
    private final ObjectMapper objectMapper;
    private final IdempotencyGuard idempotencyGuard;

    @KafkaListener(topics = "iam.user-events", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onUserRegistered(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);

            String eventIdStr = event.path("idempotencyKey").asText();
            if (eventIdStr == null || eventIdStr.isEmpty() || "null".equals(eventIdStr)) {
                eventIdStr = event.path("id").asText();
            }

            if (idempotencyGuard.isAlreadyProcessed("user-registered-kyc", eventIdStr)) {
                log.debug("Event {} already processed, skipping", eventIdStr);
                ack.acknowledge();
                return;
            }

            String eventType = event.path("eventType").asText();
            if (!"USER_REGISTERED".equals(eventType)) {
                ack.acknowledge();
                return;
            }

            UUID userId = UUID.fromString(event.path("payload").path("keycloakId").asText());
            kycApplicationService.createKycShell(userId);

            log.info("UserRegistered processed: userId={}", userId);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process UserRegistered event", e);
        }
    }
}
