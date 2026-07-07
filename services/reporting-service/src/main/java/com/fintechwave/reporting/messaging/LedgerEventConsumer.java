package com.fintechwave.reporting.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.reporting.service.LedgerIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerEventConsumer {

    private final ObjectMapper objectMapper;
    private final com.fintechwave.core.messaging.IdempotencyGuard idempotencyGuard;
    private final LedgerIndexingService ledgerIndexingService;

    @KafkaListener(topics = "ledger.transaction-results", groupId = "reporting-service-ledger", containerFactory = "kafkaListenerContainerFactory")
    public void onLedgerEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventType = root.path("eventType").asText();

            // Idempotency guard
            String eventId = root.path("idempotencyKey").asText();
            if (eventId == null || eventId.isEmpty() || "null".equals(eventId)) {
                eventId = root.path("id").asText();
            }
            if (idempotencyGuard.isAlreadyProcessed("report-ledger", eventId)) {
                ack.acknowledge();
                return;
            }

            if ("LEDGER_COMMITTED".equals(eventType)) {
                JsonNode payload = root.path("payload");
                String transactionId = payload.path("transactionId").asText();
                JsonNode entries = payload.path("entries");

                for (JsonNode entry : entries) {
                    String accountCode = entry.path("accountCode").asText();
                    String accountType = entry.path("accountType").asText();
                    String entryType = entry.path("entryType").asText();
                    BigDecimal amount = new BigDecimal(entry.path("amount").asText("0"));
                    String currency = entry.path("currency").asText("JOD");
                    String description = entry.path("description").asText("");
                    String idempotencyKey = entry.path("idempotencyKey").asText();

                    // Index into fintechwave-ledger-entries
                    ledgerIndexingService.indexEntry(
                            idempotencyKey, transactionId, accountCode, accountType,
                            entryType, amount, currency, description, Instant.now());

                    // Upsert wallet balance
                    if ("2000".equals(accountCode)) {
                        String ownerId = entry.path("ownerId").asText(null);
                        if (ownerId != null) {
                            ledgerIndexingService.upsertWalletBalance(ownerId, entryType, amount, currency);
                        }
                    }
                }
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Ledger event consumer error: offset={}", record.offset(), e);
            throw new RuntimeException("Ledger event processing failed", e);
        }
    }
}
