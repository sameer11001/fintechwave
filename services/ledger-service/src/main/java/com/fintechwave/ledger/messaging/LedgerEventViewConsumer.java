package com.fintechwave.ledger.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.ledger.query.service.WalletProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerEventViewConsumer {

    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final WalletProjectionService projectionService;

    @KafkaListener(topics = "ledger.wallet-events", groupId = "ledger-service-view-updater", containerFactory = "kafkaListenerContainerFactory")
    public void onLedgerEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventIdStr = root.path("idempotencyKey").asText();
            if (eventIdStr == null || eventIdStr.isEmpty() || "null".equals(eventIdStr)) {
                eventIdStr = root.path("id").asText();
            }

            Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent("processed:ledger-view:" + eventIdStr, "1", java.time.Duration.ofDays(7));
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Event {} already processed for view, skipping", eventIdStr);
                ack.acknowledge();
                return;
            }

            String eventType = root.path("eventType").asText();
            JsonNode payload = root.path("payload");

            if ("LEDGER_ENTRY_COMMITTED".equals(eventType) || "WALLET_CREATED".equals(eventType)) {
                UUID userId    = UUID.fromString(payload.path("userId").asText());
                UUID accountId = UUID.fromString(payload.path("accountId").asText());
                BigDecimal balance  = new BigDecimal(payload.path("balance").asText("0"));
                String currency     = payload.path("currency").asText("USD");

                projectionService.handleBalanceUpdate(userId, accountId, balance, currency);
            } else {
                log.debug("Ignoring eventType={} for view projection", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process LedgerEvent for projection: offset={}", record.offset(), e);
            throw new RuntimeException(e);
        }
    }
}
