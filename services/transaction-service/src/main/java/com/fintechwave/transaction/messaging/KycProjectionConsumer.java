package com.fintechwave.transaction.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.core.messaging.IdempotencyGuard;
import com.fintechwave.transaction.domain.entity.KycProjection;
import com.fintechwave.transaction.domain.enums.KycStatus;
import com.fintechwave.transaction.domain.enums.KycTier;
import com.fintechwave.transaction.repository.KycProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class KycProjectionConsumer {

    private final ObjectMapper objectMapper;
    private final IdempotencyGuard idempotencyGuard;
    private final KycProjectionRepository kycProjectionRepository;

    @KafkaListener(topics = "kyc.verification-events", groupId = "transaction-service-kyc-projection", containerFactory = "kafkaListenerContainerFactory")
    public void onKycEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventIdStr = root.path("idempotencyKey").asText();
            if (eventIdStr == null || eventIdStr.isEmpty() || "null".equals(eventIdStr)) {
                eventIdStr = root.path("id").asText();
            }

            if (idempotencyGuard.isAlreadyProcessed("tx-kyc-view", eventIdStr)) {
                log.debug("Event {} already processed for KYC projection, skipping", eventIdStr);
                ack.acknowledge();
                return;
            }

            String eventType = root.path("eventType").asText();
            JsonNode payload = root.path("payload");

            switch (eventType) {
                case "KYC_CREATED" -> handleKycCreated(payload);
                case "KYC_VERIFIED" -> handleKycVerified(payload);
                case "KYC_REJECTED" -> handleKycRejected(payload);
                default -> log.debug("Ignoring eventType={} for KYC projection", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process KycEvent for projection: offset={}", record.offset(), e);
            throw new RuntimeException("Failed to process KYC event", e);
        }
    }

    private void handleKycCreated(JsonNode payload) {
        UUID userId = UUID.fromString(payload.path("userId").asText());
        KycProjection projection = kycProjectionRepository.findByUserId(userId)
                .orElseGet(() -> KycProjection.builder()
                        .userId(userId)
                        .currentTier(KycTier.TIER_0)
                        .status(KycStatus.PENDING_SUBMISSION)
                        .build());
        projection.setUpdatedAt(Instant.now());
        kycProjectionRepository.save(projection);
    }

    private void handleKycVerified(JsonNode payload) {
        UUID userId = UUID.fromString(payload.path("userId").asText());
        String verifiedTier = payload.path("verifiedTier").asText();

        KycProjection projection = kycProjectionRepository.findByUserId(userId)
                .orElseGet(() -> KycProjection.builder().userId(userId).build());

        projection.setCurrentTier(KycTier.valueOf(verifiedTier));
        projection.setStatus(KycStatus.VERIFIED);
        projection.setUpdatedAt(Instant.now());
        kycProjectionRepository.save(projection);
    }

    private void handleKycRejected(JsonNode payload) {
        UUID userId = UUID.fromString(payload.path("userId").asText());

        KycProjection projection = kycProjectionRepository.findByUserId(userId)
                .orElseGet(() -> KycProjection.builder()
                        .userId(userId)
                        .currentTier(KycTier.TIER_0)
                        .build());

        projection.setStatus(KycStatus.REJECTED);
        projection.setUpdatedAt(Instant.now());
        kycProjectionRepository.save(projection);
    }
}
