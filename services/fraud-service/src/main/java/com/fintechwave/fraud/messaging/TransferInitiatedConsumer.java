package com.fintechwave.fraud.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.fraud.service.IFraudService;
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
public class TransferInitiatedConsumer {

    private final IFraudService fraudService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "tx.transaction-events", groupId = "fraud-service", containerFactory = "kafkaListenerContainerFactory")
    public void onTransactionEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());

            String eventType = root.path("eventType").asText();

            if (!"TRANSFER_INITIATED".equals(eventType)) {
                log.debug("Ignoring event type={} on tx.transaction-events", eventType);
                ack.acknowledge();
                return;
            }

            UUID transactionId = UUID.fromString(root.path("aggregateId").asText());
            UUID idempotencyKey = UUID.fromString(root.path("idempotencyKey").asText());

            JsonNode payload = root.path("payload");
            UUID userId = UUID.fromString(payload.path("senderId").asText());
            BigDecimal amount = new BigDecimal(payload.path("amount").asText());
            String currency = payload.path("currency").asText("USD");

            log.info("TransferInitiated received: txId={} userId={} amount={} {}",
                    transactionId, userId, amount, currency);

            fraudService.evaluate(transactionId, userId, amount, currency, idempotencyKey);
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("TransferInitiated consumer error: offset={} key={}", record.offset(), record.key(), ex);
            // Do NOT acknowledge — let Kafka retry
            throw new RuntimeException("TransferInitiated processing failed", ex);
        }
    }
}
