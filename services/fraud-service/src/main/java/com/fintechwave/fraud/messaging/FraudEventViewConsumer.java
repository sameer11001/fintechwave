package com.fintechwave.fraud.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.fraud.query.service.FraudProjectionService;
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
public class FraudEventViewConsumer {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final FraudProjectionService projectionService;

    @KafkaListener(topics = "fraud.risk-events", groupId = "fraud-service-view-updater", containerFactory = "kafkaListenerContainerFactory")
    public void onFraudEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventIdStr = root.path("idempotencyKey").asText();
            if (eventIdStr == null || eventIdStr.isEmpty() || "null".equals(eventIdStr)) {
                eventIdStr = root.path("id").asText();
            }

            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent("processed:fraud-view:" + eventIdStr, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Event {} already processed for view, skipping", eventIdStr);
                ack.acknowledge();
                return;
            }

            String eventType = root.path("eventType").asText();
            JsonNode payload = root.path("payload");

            UUID txId = UUID.fromString(root.path("aggregateId").asText());

            if ("TRANSACTION_APPROVED".equals(eventType) || "TRANSACTION_FLAGGED".equals(eventType)) {
                UUID userId = UUID.fromString(payload.path("userId").asText());
                Integer riskScore = payload.has("riskScore") ? payload.path("riskScore").asInt() : null;
                String status = eventType.replace("TRANSACTION_", "");

                projectionService.handleFraudEvaluation(userId, txId, riskScore, status);
            } else {
                log.debug("Ignoring eventType={} for view projection", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process FraudEvent for projection: offset={}", record.offset(), e);
            throw new RuntimeException(e);
        }
    }
}
