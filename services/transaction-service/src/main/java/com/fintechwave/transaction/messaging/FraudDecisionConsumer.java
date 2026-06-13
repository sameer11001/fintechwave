package com.fintechwave.transaction.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.transaction.service.ITransactionService;
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
public class FraudDecisionConsumer {

    private final ITransactionService transactionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "fraud.risk-events", groupId = "transaction-service-fraud", containerFactory = "kafkaListenerContainerFactory")
    public void onFraudEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventType = root.path("eventType").asText();
            UUID transactionId = UUID.fromString(root.path("aggregateId").asText());

            if ("TRANSACTION_APPROVED".equals(eventType)) {
                log.info("Received TRANSACTION_APPROVED for txId={}", transactionId);
                transactionService.handleFraudDecision(transactionId, true);
            } else if ("TRANSACTION_FLAGGED".equals(eventType)) {
                log.info("Received TRANSACTION_FLAGGED for txId={}", transactionId);
                transactionService.handleFraudDecision(transactionId, false);
            } else {
                log.debug("Ignored fraud event type={} for txId={}", eventType, transactionId);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing fraud decision event: offset={} key={}", record.offset(), record.key(), e);
            throw new RuntimeException("Failed to process fraud decision", e);
        }
    }
}
