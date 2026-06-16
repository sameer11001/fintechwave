package com.fintechwave.reporting.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.reporting.service.SearchIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class KycEventConsumer {

    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final SearchIndexingService searchIndexingService;

    @KafkaListener(topics = {
            "kyc.verification-events" }, groupId = "reporting-service-kyc", containerFactory = "kafkaListenerContainerFactory")
    public void onKycEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());

            String eventIdStr = root.path("idempotencyKey").asText();
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent("processed:report-kyc:" + eventIdStr, "1", java.time.Duration.ofDays(7));
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Event {} already processed, skipping", eventIdStr);
                ack.acknowledge();
                return;
            }

            String eventType = root.path("eventType").asText();
            JsonNode payload = root.path("payload");

            if ("KYC_VERIFIED".equals(eventType)) {
                UUID userId = UUID.fromString(payload.path("userId").asText());
                String kycTier = payload.has("kycTier") ? payload.path("kycTier").asText() : "TIER_1";
                searchIndexingService.indexKycUpdate(userId, kycTier);
            } else {
                log.debug("Reporting: ignoring kyc eventType={} as it doesn't affect ES index", eventType);
            }

            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Reporting kyc consumer error: topic={} offset={}", record.topic(), record.offset(), ex);
            throw new RuntimeException("Reporting kyc event processing failed", ex);
        }
    }
}
