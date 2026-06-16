package com.fintechwave.notification.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.notification.domain.enums.NotificationChannel;
import com.fintechwave.notification.service.INotificationService;
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
public class FraudEventConsumer {

    private final INotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @KafkaListener(
            topics = {"fraud.risk-events"},
            groupId = "notification-service-fraud",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onFraudEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());

            String eventIdStr = root.path("idempotencyKey").asText();
            Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent("processed:notif-fraud:" + eventIdStr, "1", java.time.Duration.ofDays(7));
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Event {} already processed, skipping", eventIdStr);
                ack.acknowledge();
                return;
            }

            String eventType       = root.path("eventType").asText();
            String idempotencyKeyStr = root.path("idempotencyKey").asText();

            if (idempotencyKeyStr == null || idempotencyKeyStr.isBlank()) {
                log.warn("Notification consumer: missing idempotencyKey for eventType={} topic={} — skipping",
                        eventType, record.topic());
                ack.acknowledge();
                return;
            }

            UUID idempotencyKey;
            try {
                idempotencyKey = UUID.fromString(idempotencyKeyStr);
            } catch (IllegalArgumentException e) {
                log.warn("Notification consumer: invalid idempotencyKey='{}' for eventType={} — skipping",
                        idempotencyKeyStr, eventType);
                ack.acknowledge();
                return;
            }

            log.debug("Notification consumer received: eventType={} topic={}", eventType, record.topic());

            if ("TRANSACTION_FLAGGED".equals(eventType)) {
                UUID userId = parseUUID(root.path("payload").path("userId").asText(), eventType);
                if (userId != null) {
                    notificationService.send(
                            idempotencyKey, userId, NotificationChannel.EMAIL,
                            "TRANSACTION_FLAGGED",
                            "Security Alert \u2014 Transaction Under Review",
                            "A transaction on your account has been flagged for review. If this was not you, contact support immediately."
                    );
                }
            } else {
                log.debug("No notification mapping for eventType={}", eventType);
            }

            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Notification consumer error: topic={} offset={}", record.topic(), record.offset(), ex);
            throw new RuntimeException("Notification event processing failed", ex);
        }
    }

    private UUID parseUUID(String value, String eventType) {
        if (value == null || value.isBlank()) {
            log.warn("Notification consumer: missing UUID field for eventType={}", eventType);
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("Notification consumer: invalid UUID '{}' for eventType={} — skipping", value, eventType);
            return null;
        }
    }
}
