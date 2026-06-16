package com.fintechwave.transaction.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.transaction.query.service.TransactionProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventViewConsumer {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final TransactionProjectionService projectionService;

    @KafkaListener(topics = "tx.transaction-events", groupId = "tx-service-view-updater", containerFactory = "kafkaListenerContainerFactory")
    public void onTransactionEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventIdStr = root.path("idempotencyKey").asText();
            if (eventIdStr == null || eventIdStr.isEmpty() || "null".equals(eventIdStr)) {
                eventIdStr = root.path("id").asText();
            }

            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent("processed:tx-view:" + eventIdStr, "1", java.time.Duration.ofDays(7));
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Event {} already processed for view, skipping", eventIdStr);
                ack.acknowledge();
                return;
            }

            String eventType = root.path("eventType").asText();
            JsonNode payload = root.path("payload");

            UUID txId = UUID.fromString(root.path("aggregateId").asText());

            if ("TRANSFER_INITIATED".equals(eventType) || "TRANSFER_COMPLETED".equals(eventType)
                    || "TRANSFER_FAILED".equals(eventType)) {
                UUID senderId = payload.has("senderId") ? UUID.fromString(payload.path("senderId").asText()) : null;
                UUID receiverId = payload.has("receiverId") ? UUID.fromString(payload.path("receiverId").asText())
                        : null;
                BigDecimal amount = payload.has("amount") ? new BigDecimal(payload.path("amount").asText("0")) : null;
                String currency = payload.path("currency").asText("USD");
                String status = eventType.replace("TRANSFER_", "");
                String reason = payload.has("failureReason") ? payload.path("failureReason").asText() : null;

                projectionService.handleTransactionEvent(txId, senderId, receiverId, amount, currency, "TRANSFER",
                        status, reason);
            } else {
                log.debug("Ignoring eventType={} for view projection", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process TransactionEvent for projection: offset={}", record.offset(), e);
            throw new RuntimeException(e);
        }
    }
}
