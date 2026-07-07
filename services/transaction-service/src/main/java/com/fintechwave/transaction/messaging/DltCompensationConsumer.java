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
import org.springframework.kafka.core.KafkaTemplate;
import com.fintechwave.transaction.service.StripeRefundService;

@Component
@RequiredArgsConstructor
@Slf4j
public class DltCompensationConsumer {

    private final ObjectMapper objectMapper;
    private final StripeRefundService stripeRefundService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = "tx.transaction-events.DLT", groupId = "tx-service-dlt-compensation", containerFactory = "kafkaListenerContainerFactory")
    public void onDltEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventType = root.path("eventType").asText();
            UUID transactionId = UUID.fromString(root.path("aggregateId").asText());

            log.error("Received event on DLT: type={} txId={}. Initiating compensation...", eventType, transactionId);

            switch (eventType) {
                case "CASH_IN_COMPLETED" -> {
                    log.warn("CASH_IN_COMPLETED failed permanently. Requires Stripe refund for txId={}", transactionId);
                    String paymentIntentId = root.path("payload").path("stripePaymentIntentId").asText();
                    if (paymentIntentId != null && !paymentIntentId.isEmpty()) {
                        stripeRefundService.processRefund(paymentIntentId);
                    } else {
                        log.error("Cannot process Stripe refund for txId={} because stripePaymentIntentId is missing",
                                transactionId);
                    }
                }
                case "TRANSFER_COMPLETED" -> {
                    log.warn("TRANSFER_COMPLETED failed permanently. Requires rollback release for txId={}",
                            transactionId);
                    publishCompensationEvent("LEDGER_ROLLBACK_REQUESTED", transactionId, root.path("payload"));
                }
                case "CASH_OUT_COMPLETED" -> {
                    log.error("SEV-1: CASH_OUT_COMPLETED failed in ledger. Manual reconciliation required for txId={}",
                            transactionId);
                    publishCompensationEvent("MANUAL_RECONCILIATION_REQUIRED", transactionId,
                            objectMapper.createObjectNode().put("reason", "CASH_OUT_COMPLETED DLT fallback"));
                }
                default -> log.debug("Unhandled DLT event type={} txId={}", eventType, transactionId);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing DLT compensation event: offset={} key={}", record.offset(), record.key(), e);
            throw new RuntimeException("Failed to process DLT compensation", e);
        }
    }

    private void publishCompensationEvent(String eventType, UUID transactionId, JsonNode payload) {
        try {
            var eventNode = objectMapper.createObjectNode();
            eventNode.put("eventType", eventType);
            eventNode.put("aggregateId", transactionId.toString());
            eventNode.set("payload", payload);

            kafkaTemplate.send("tx.transaction-events", transactionId.toString(),
                    objectMapper.writeValueAsString(eventNode));
            log.info("Published compensation event {} for txId={}", eventType, transactionId);
        } catch (Exception e) {
            log.error("Failed to publish compensation event {} for txId={}", eventType, transactionId, e);
        }
    }
}
