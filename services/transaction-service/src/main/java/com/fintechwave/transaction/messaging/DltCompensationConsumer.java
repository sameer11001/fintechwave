package com.fintechwave.transaction.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class DltCompensationConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "tx.transaction-events.DLT", groupId = "tx-service-dlt-compensation", containerFactory = "kafkaListenerContainerFactory")
    public void onDltEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventType = root.path("eventType").asText();
            UUID transactionId = UUID.fromString(root.path("aggregateId").asText());

            log.error("Received event on DLT: type={} txId={}. Initiating compensation...", eventType, transactionId);

            switch (eventType) {
                case "CASH_IN_COMPLETED" -> {
                    // 1. Parse userId and transactionId
                    // 2. Check if wallet exists now via ledgerGrpcClient (retry window may have
                    // passed)
                    // 3. If missing: call Stripe Refunds API → mark tx REFUNDED
                    log.warn("CASH_IN_COMPLETED failed permanently. Requires Stripe refund for txId={}", transactionId);
                    // implementation details
                }
                case "TRANSFER_COMPLETED" -> {
                    // Receiver wallet missing or commit failed
                    // Emit TRANSFER_ROLLBACK_REQUIRED
                    log.warn("TRANSFER_COMPLETED failed permanently. Requires rollback release for txId={}",
                            transactionId);
                    // implementation details
                }
                case "CASH_OUT_COMPLETED" -> {
                    // Money physically left. Cannot auto-reverse.
                    log.error("SEV-1: CASH_OUT_COMPLETED failed in ledger. Manual reconciliation required for txId={}",
                            transactionId);
                }
                default -> log.debug("Unhandled DLT event type={} txId={}", eventType, transactionId);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing DLT compensation event: offset={} key={}", record.offset(), record.key(), e);
            throw new RuntimeException("Failed to process DLT compensation", e);
        }
    }
}
