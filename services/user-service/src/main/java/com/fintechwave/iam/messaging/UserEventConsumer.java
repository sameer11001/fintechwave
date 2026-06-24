package com.fintechwave.iam.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.iam.query.service.UserProfileProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final UserProfileProjectionService projectionService;

    @KafkaListener(topics = "iam.user-events", groupId = "user-service-view-updater", containerFactory = "kafkaListenerContainerFactory")
    public void onUserEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventIdStr = root.path("idempotencyKey").asText();

            if (eventIdStr == null || eventIdStr.isEmpty() || "null".equals(eventIdStr)) {
                eventIdStr = root.path("id").asText(); // fallback to outbox event ID
            }

            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent("processed:user-view:" + eventIdStr, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Event {} already processed for view, skipping", eventIdStr);
                ack.acknowledge();
                return;
            }

            String eventType = root.path("eventType").asText();
            JsonNode payload = root.path("payload");

            if ("USER_REGISTERED".equals(eventType)) {
                UUID userId = UUID.fromString(payload.path("userId").asText());
                String email = payload.path("email").asText();
                String firstName = payload.path("firstName").asText("");
                String lastName = payload.path("lastName").asText("");
                UUID keycloakId = UUID.fromString(payload.path("keycloakId").asText());
                String status = payload.path("status").asText("ACTIVE");

                projectionService.handleUserRegistered(userId, email, firstName, lastName, keycloakId, status);
            } else if ("KYC_VERIFIED".equals(eventType)) {
                UUID userId = UUID.fromString(payload.path("userId").asText());
                String kycTier = payload.path("kycTier").asText();
                projectionService.handleKycVerified(userId, kycTier);
            } else {
                log.debug("Ignoring eventType={} for view projection", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process UserEvent for projection: offset={}", record.offset(), e);
            throw new RuntimeException(e);
        }
    }
}
