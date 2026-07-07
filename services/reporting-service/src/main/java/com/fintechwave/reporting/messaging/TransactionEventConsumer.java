package com.fintechwave.reporting.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.core.messaging.IdempotencyGuard;
import com.fintechwave.reporting.service.SearchIndexingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

    private final ObjectMapper objectMapper;
    private final IdempotencyGuard idempotencyGuard;
    private final SearchIndexingService searchIndexingService;

    @KafkaListener(topics = {
            "tx.transaction-events" }, groupId = "reporting-service-tx", containerFactory = "kafkaListenerContainerFactory")
    public void onTransactionEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());

            String eventIdStr = root.path("idempotencyKey").asText();
            if (eventIdStr == null || eventIdStr.isEmpty() || "null".equals(eventIdStr)) {
                eventIdStr = root.path("id").asText();
            }
            if (idempotencyGuard.isAlreadyProcessed("report-tx", eventIdStr)) {
                log.debug("Event {} already processed, skipping", eventIdStr);
                ack.acknowledge();
                return;
            }

            String eventType = root.path("eventType").asText();
            JsonNode payload = root.path("payload");

            switch (eventType) {
                case "TRANSFER_COMPLETED", "TRANSFER_REVERSED" ->
                    indexTx(payload, "P2P_TRANSFER", "TRANSFER_COMPLETED".equals(eventType) ? "COMPLETED" : "REVERSED");

                case "TRANSFER_FAILED" -> indexTx(payload, "P2P_TRANSFER", "FAILED");
                case "CASH_IN_COMPLETED" -> indexTx(payload, "CASH_IN", "COMPLETED");
                case "CASH_IN_FAILED" -> indexTx(payload, "CASH_IN", "FAILED");
                case "CASH_OUT_COMPLETED" -> indexTx(payload, "CASH_OUT", "COMPLETED");
                case "CASH_OUT_FAILED" -> indexTx(payload, "CASH_OUT", "FAILED");
                case "BILL_PAY_COMPLETED" -> indexTx(payload, "BILL_PAY", "COMPLETED");
                case "BILL_PAY_FAILED" -> indexTx(payload, "BILL_PAY", "FAILED");

                default -> log.debug("Reporting: no projection for transaction eventType={}", eventType);
            }

            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Reporting transaction consumer error: topic={} offset={}", record.topic(), record.offset(), ex);
            throw new RuntimeException("Reporting transaction event processing failed", ex);
        }
    }

    private void indexTx(JsonNode payload, String txType, String status) {
        UUID transactionId = UUID.fromString(payload.path("transactionId").asText());
        String userIdStr = payload.has("userId") ? payload.path("userId").asText() : payload.path("senderId").asText();
        UUID userId = UUID.fromString(userIdStr);
        BigDecimal amount = new BigDecimal(payload.path("amount").asText("0"));
        String currency = payload.path("currency").asText("USD");
        UUID counterparty = payload.has("receiverId")
                ? UUID.fromString(payload.path("receiverId").asText())
                : null;

        searchIndexingService.indexTransaction(
                transactionId,
                userId,
                counterparty,
                amount,
                currency,
                txType,
                status,
                Instant.now());
    }
}
