package com.fintechwave.iam.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.iam.service.IUserProfileService;
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
public class KYCVerifiedConsumer {

    private final IUserProfileService userProfileService;
    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @KafkaListener(topics = { "kyc.verification-events" }, groupId = "user-service")
    public void onDomainEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());

            String eventIdStr = root.path("idempotencyKey").asText();
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent("processed:user-kyc:" + eventIdStr, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Event {} already processed, skipping", eventIdStr);
                ack.acknowledge();
                return;
            }

            String eventType = root.path("eventType").asText();

            log.debug("User Service consumer received: eventType={} topic={}", eventType, record.topic());

            if ("KYC_VERIFIED".equals(eventType)) {
                String userIdStr = root.path("payload").path("userId").asText();
                String verifiedTier = root.path("payload").path("verifiedTier").asText();

                if (userIdStr != null && !userIdStr.isBlank() && verifiedTier != null && !verifiedTier.isBlank()) {
                    try {
                        UUID userId = UUID.fromString(userIdStr);
                        userProfileService.updateKycTier(userId, verifiedTier);
                        log.info("Successfully updated KYC tier for user {} to {}", userId, verifiedTier);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid userId format: {}", userIdStr);
                    }
                } else {
                    log.warn("Missing userId or verifiedTier in KYC_VERIFIED event payload");
                }
            }

            ack.acknowledge();
        } catch (Exception ex) {
            log.error("User Service consumer error: topic={} offset={}", record.topic(), record.offset(), ex);
            throw new RuntimeException("Domain event processing failed", ex);
        }
    }
}
