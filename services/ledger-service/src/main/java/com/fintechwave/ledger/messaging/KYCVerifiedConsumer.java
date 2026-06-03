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

    @KafkaListener(topics = "kyc.verification-events", groupId = "ledger-service-kyc", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onKYCVerified(ConsumerRecord<String, String> record) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String eventType = payload.path("eventType").asText();

            if (!"KYC_VERIFIED".equals(eventType)) {
                log.debug("Ignoring event type={} on kyc.verification-events", eventType);
                return;
            }

            UUID userId = UUID.fromString(payload.path("payload").path("userId").asText());
            String currency = payload.path("payload").path("currency").asText("JOD");

            log.info("KYCVerified received: userId={} — provisioning wallet", userId);
            ledgerService.provisionWallet(userId, currency);

        } catch (Exception ex) {
            log.error("KYCVerified consumer error: offset={} key={}", record.offset(), record.key(), ex);
            throw new RuntimeException("KYCVerified processing failed", ex);
        }
    }
}
