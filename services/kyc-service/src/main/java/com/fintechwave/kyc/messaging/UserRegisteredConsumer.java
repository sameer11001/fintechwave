package com.fintechwave.kyc.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.kyc.service.IKycApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(topics = "iam.user-events", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onUserRegistered(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);

            String eventType = event.path("eventType").asText();
            if (!"USER_REGISTERED".equals(eventType)) {
                ack.acknowledge();
                return;
            }

            String idempotencyKeyStr = event.path("idempotencyKey").asText();

            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent("processed:kyc-user:" + idempotencyKeyStr, "1", java.time.Duration.ofDays(7));
            if (Boolean.FALSE.equals(isNew)) {
                log.warn("Duplicate UserRegistered event, skipping: idempotencyKey={}", idempotencyKeyStr);
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
