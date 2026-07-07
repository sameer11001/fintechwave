package com.fintechwave.reporting.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.core.messaging.IdempotencyGuard;
import com.fintechwave.reporting.service.SearchIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final ObjectMapper objectMapper;
    private final IdempotencyGuard idempotencyGuard;
    private final StringRedisTemplate redisTemplate;
    private final SearchIndexingService searchIndexingService;

    @KafkaListener(topics = "iam.user-events", groupId = "reporting-service-users", containerFactory = "kafkaListenerContainerFactory")
    public void onUserEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventIdStr = root.path("idempotencyKey").asText();
            if (eventIdStr == null || eventIdStr.isEmpty() || "null".equals(eventIdStr)) {
                eventIdStr = root.path("id").asText();
            }

            if (idempotencyGuard.isAlreadyProcessed("report-user", eventIdStr)) {
                log.debug("Event {} already processed for search, skipping", eventIdStr);
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

                searchIndexingService.indexUserRegistration(userId, keycloakId, email, firstName, lastName, status);

                if ("ACTIVE".equals(status)) {
                    redisTemplate.opsForValue().increment("reporting:active_users_count");
                }
            } else if ("USER_STATUS_CHANGED".equals(eventType)) {
                String newStatus = payload.path("status").asText();
                if ("SUSPENDED".equals(newStatus) || "DEACTIVATED".equals(newStatus)) {
                    redisTemplate.opsForValue().decrement("reporting:active_users_count");
                } else if ("ACTIVE".equals(newStatus)) {
                    redisTemplate.opsForValue().increment("reporting:active_users_count");
                }
            } else {
                log.debug("Ignoring eventType={} for search indexing", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process UserEvent for search indexing: offset={}", record.offset(), e);
            throw new RuntimeException(e);
        }
    }
}
