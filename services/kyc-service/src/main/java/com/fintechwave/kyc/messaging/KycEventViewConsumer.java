package com.fintechwave.kyc.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.kyc.query.service.KycProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class KycEventViewConsumer {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final KycProjectionService projectionService;

    @KafkaListener(topics = "kyc.verification-events", groupId = "kyc-service-view-updater", containerFactory = "kafkaListenerContainerFactory")
    public void onKycEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventIdStr = root.path("idempotencyKey").asText();
            if (eventIdStr == null || eventIdStr.isEmpty() || "null".equals(eventIdStr)) {
                eventIdStr = root.path("id").asText();
            }

            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent("processed:kyc-view:" + eventIdStr, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Event {} already processed for view, skipping", eventIdStr);
                ack.acknowledge();
                return;
            }

            String eventType = root.path("eventType").asText();
            JsonNode payload = root.path("payload");
            UUID appId = UUID.fromString(root.path("aggregateId").asText());

            switch (eventType) {
                case "KYC_CREATED" -> {
                    UUID userId = UUID.fromString(payload.path("userId").asText());
                    String status = payload.path("status").asText("PENDING_SUBMISSION");
                    String currentTier = payload.path("currentTier").asText("TIER_0");
                    String requestedTier = payload.path("requestedTier").asText("TIER_1");
                    projectionService.handleKycCreated(appId, userId, status, currentTier, requestedTier);
                }
                case "KYC_SUBMITTED" -> {
                    UUID userId = UUID.fromString(payload.path("userId").asText());
                    String requestedTier = payload.path("requestedTier").asText("TIER_1");
                    projectionService.handleKycSubmitted(appId, userId, "UNDER_REVIEW", requestedTier);
                }
                case "KYC_VERIFIED" -> {
                    UUID userId = UUID.fromString(payload.path("userId").asText());
                    String verifiedTier = payload.path("verifiedTier").asText("TIER_1");
                    projectionService.handleKycVerified(appId, userId, "VERIFIED", verifiedTier);
                }
                case "KYC_REJECTED" -> {
                    UUID userId = UUID.fromString(payload.path("userId").asText());
                    String rejectionReason = payload.path("rejectionReason").asText("Please resubmit your documents.");
                    projectionService.handleKycRejected(appId, userId, "REJECTED", rejectionReason);
                }
                default -> log.debug("Ignoring eventType={} for KYC view projection", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process KycEvent for projection: offset={}", record.offset(), e);
            throw new RuntimeException(e);
        }
    }
}
