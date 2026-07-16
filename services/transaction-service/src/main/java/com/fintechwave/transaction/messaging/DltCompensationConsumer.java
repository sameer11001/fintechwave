package com.fintechwave.transaction.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.transaction.saga.TransactionSagaManager;
import com.fintechwave.transaction.service.StripeRefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DltCompensationConsumer {

    private final ObjectMapper objectMapper;
    private final StripeRefundService stripeRefundService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionSagaManager sagaManager;

    @KafkaListener(topics = "tx.transaction-events.DLT", groupId = "tx-service-dlt-compensation", containerFactory = "kafkaListenerContainerFactory")
    public void onDltEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventType = root.path("eventType").asText();
            UUID transactionId = UUID.fromString(root.path("aggregateId").asText());

            log.error("DLT event received: type={} txId={} — initiating last-resort compensation",
                    eventType, transactionId);

            switch (eventType) {
                case "CASH_IN_COMPLETED" -> {
                    // Cash-in ledger commit failed permanently → refund the Stripe charge
                    String paymentIntentId = root.path("payload").path("stripePaymentIntentId").asText();
                    if (paymentIntentId != null && !paymentIntentId.isBlank()) {
                        stripeRefundService.processRefund(paymentIntentId);
                        sagaManager.onStripeRefundInitiated(transactionId,
                                "DLT fallback: CASH_IN_COMPLETED failed permanently — Stripe refund issued");
                        log.warn("DLT[CASH_IN_COMPLETED]: Stripe refund triggered txId={}", transactionId);
                    } else {
                        log.error("DLT[CASH_IN_COMPLETED]: cannot refund txId={} — stripePaymentIntentId missing",
                                transactionId);
                        sagaManager.onStripeRefundInitiated(transactionId,
                                "DLT fallback: stripePaymentIntentId missing — manual review required");
                    }
                }

                case "TRANSFER_COMPLETED" -> {
                    // P2P ledger commit failed permanently → publish rollback to ledger
                    String rollbackReason = "DLT fallback: TRANSFER_COMPLETED failed permanently — LEDGER_ROLLBACK_REQUESTED";
                    publishCompensationEvent("LEDGER_ROLLBACK_REQUESTED", transactionId, root.path("payload"));
                    sagaManager.onDltCompensationPublished(transactionId, rollbackReason);
                    log.warn("DLT[TRANSFER_COMPLETED]: ledger rollback published txId={}", transactionId);
                }

                case "CASH_OUT_COMPLETED" -> {
                    // Cash-out ledger commit failed permanently → cannot auto-compensate (money
                    // left account)
                    // Mark for manual reconciliation and update saga state
                    String reconcileReason = "DLT fallback: CASH_OUT_COMPLETED failed — SEV-1 manual reconciliation required";
                    publishCompensationEvent("MANUAL_RECONCILIATION_REQUIRED", transactionId,
                            objectMapper.createObjectNode().put("reason", reconcileReason));
                    sagaManager.onDltCompensationPublished(transactionId, reconcileReason);
                    log.error("DLT[CASH_OUT_COMPLETED]: SEV-1 manual reconciliation required txId={}", transactionId);
                }

                default -> log.debug("DLT: unhandled event type={} txId={} — no compensation action", eventType,
                        transactionId);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("DLT consumer error: offset={} key={}", record.offset(), record.key(), e);
            throw new RuntimeException("Failed to process DLT compensation event", e);
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
            log.info("DLT compensation event published: type={} txId={}", eventType, transactionId);
        } catch (Exception e) {
            log.error("Failed to publish DLT compensation event type={} txId={}", eventType, transactionId, e);
        }
    }
}
