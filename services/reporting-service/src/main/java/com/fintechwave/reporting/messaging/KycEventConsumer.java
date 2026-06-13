package com.fintechwave.reporting.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.reporting.domain.entity.KycStatusSummary;
import com.fintechwave.reporting.domain.entity.ProcessedEvent;
import com.fintechwave.reporting.repository.KycStatusSummaryRepository;
import com.fintechwave.reporting.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class KycEventConsumer {

    private final KycStatusSummaryRepository kycStatusRepo;
    private final ProcessedEventRepository processedEventRepo;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"kyc.verification-events"},
            groupId = "reporting-service-kyc",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onKycEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventType       = root.path("eventType").asText();
            UUID   idempotencyKey  = UUID.fromString(root.path("idempotencyKey").asText());

            try {
                processedEventRepo.save(ProcessedEvent.builder()
                        .idempotencyKey(idempotencyKey)
                        .processedAt(Instant.now())
                        .build());
            } catch (DataIntegrityViolationException ex) {
                log.debug("Reporting: duplicate event skipped eventType={} key={}", eventType, idempotencyKey);
                ack.acknowledge();
                return;
            }

            JsonNode payload = root.path("payload");

            switch (eventType) {
                case "KYC_SUBMITTED" -> {
                    UUID userId = UUID.fromString(payload.path("userId").asText());
                    upsertKycStatus(userId, "PENDING", Instant.now(), null);
                }

                case "KYC_VERIFIED" -> {
                    UUID userId = UUID.fromString(payload.path("userId").asText());
                    upsertKycStatus(userId, "VERIFIED", null, Instant.now());
                }

                case "KYC_REJECTED" -> {
                    UUID userId = UUID.fromString(payload.path("userId").asText());
                    upsertKycStatus(userId, "REJECTED", null, Instant.now());
                }

                default -> log.debug("Reporting: no projection for kyc eventType={}", eventType);
            }

            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Reporting kyc consumer error: topic={} offset={}", record.topic(), record.offset(), ex);
            throw new RuntimeException("Reporting kyc event processing failed", ex);
        }
    }

    private void upsertKycStatus(UUID userId, String status, Instant submittedAt, Instant decidedAt) {
        kycStatusRepo.findByUserId(userId).ifPresentOrElse(summary -> {
            summary.setKycStatus(status);
            if (submittedAt != null) summary.setSubmittedAt(submittedAt);
            if (decidedAt != null)   summary.setDecidedAt(decidedAt);
            summary.setUpdatedAt(Instant.now());
            kycStatusRepo.save(summary);
        }, () -> kycStatusRepo.save(KycStatusSummary.builder()
                .userId(userId)
                .kycStatus(status)
                .submittedAt(submittedAt)
                .decidedAt(decidedAt)
                .updatedAt(Instant.now())
                .build()));
    }
}
