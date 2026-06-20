package com.fintechwave.ledger.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.ledger.service.ILedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class KYCVerifiedConsumer {

    private final ILedgerService ledgerService;
    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @KafkaListener(topics = "kyc.verification-events", groupId = "ledger-service-kyc", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onKYCVerified(ConsumerRecord<String, String> record, org.springframework.kafka.support.Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());

            String eventIdStr = root.path("idempotencyKey").asText();
            Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent("processed:ledger-kyc:" + eventIdStr, "1", java.time.Duration.ofDays(7));
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Event {} already processed, skipping", eventIdStr);
                ack.acknowledge();
                return;
            }

            String eventType = root.path("eventType").asText();

            switch (eventType) {
                case "KYC_VERIFIED" -> {
                    JsonNode payload = root.path("payload");
                    UUID userId = UUID.fromString(payload.path("userId").asText());
                    String currency = payload.path("currency").asText("JOD");

                    log.info("KYCVerified received: userId={} — provisioning wallet", userId);
                    ledgerService.provisionWallet(userId, currency);
                }
                case "KYC_CREATED", "KYC_SUBMITTED", "KYC_REJECTED" -> {
                    log.debug("Ignoring known event type={} on kyc.verification-events", eventType);
                }
                default -> {
                    log.error("UNKNOWN OR MISSING event type='{}' received on kyc.verification-events. Message ignored but requires investigation!", eventType);
                }
            }
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("KYCVerified consumer error: offset={} key={}", record.offset(), record.key(), ex);
            throw new RuntimeException("KYCVerified processing failed", ex);
        }
    }
}
