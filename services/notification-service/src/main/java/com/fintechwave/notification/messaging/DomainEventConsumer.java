package com.fintechwave.notification.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.notification.domain.enums.NotificationChannel;
import com.fintechwave.notification.service.INotificationService;
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
public class DomainEventConsumer {

    private final INotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"kyc.verification-events", "ledger.wallet-events",
                      "tx.transaction-events", "fraud.risk-events"},
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDomainEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventType       = root.path("eventType").asText();
            String idempotencyKeyStr = root.path("idempotencyKey").asText();

            // Guard: if idempotencyKey is missing/blank, skip gracefully
            if (idempotencyKeyStr == null || idempotencyKeyStr.isBlank()) {
                log.warn("Notification consumer: missing idempotencyKey for eventType={} topic={} — skipping",
                        eventType, record.topic());
                ack.acknowledge();
                return;
            }

            UUID idempotencyKey;
            try {
                idempotencyKey = UUID.fromString(idempotencyKeyStr);
            } catch (IllegalArgumentException e) {
                log.warn("Notification consumer: invalid idempotencyKey='{}' for eventType={} — skipping",
                        idempotencyKeyStr, eventType);
                ack.acknowledge();
                return;
            }

            log.debug("Notification consumer received: eventType={} topic={}", eventType, record.topic());

            switch (eventType) {
                case "KYC_VERIFIED" -> {
                    UUID userId = parseUUID(root.path("payload").path("userId").asText(), eventType);
                    if (userId == null) break;
                    notificationService.send(
                            idempotencyKey, userId, NotificationChannel.EMAIL,
                            "KYC_VERIFIED",
                            "Your KYC verification is approved",
                            "Congratulations! Your identity has been verified and your wallet is ready."
                    );
                }
                case "KYC_REJECTED" -> {
                    UUID userId = parseUUID(root.path("payload").path("userId").asText(), eventType);
                    if (userId == null) break;
                    String reason = root.path("payload").path("rejectionReason").asText("Please resubmit your documents.");
                    notificationService.send(
                            idempotencyKey, userId, NotificationChannel.EMAIL,
                            "KYC_REJECTED",
                            "Your KYC verification was not approved",
                            "Unfortunately your verification was rejected. Reason: " + reason
                    );
                }
                case "WALLET_PROVISIONED" -> {
                    UUID userId = parseUUID(root.path("payload").path("userId").asText(), eventType);
                    if (userId == null) break;
                    notificationService.send(
                            idempotencyKey, userId, NotificationChannel.EMAIL,
                            "WALLET_PROVISIONED",
                            "Welcome to FintechWave!",
                            "Your wallet has been created and is ready to use."
                    );
                }
                case "TRANSFER_COMPLETED" -> {
                    UUID senderId   = parseUUID(root.path("payload").path("senderId").asText(), eventType);
                    if (senderId == null) break;
                    String amount   = root.path("payload").path("amount").asText();
                    String currency = root.path("payload").path("currency").asText();
                    notificationService.send(
                            idempotencyKey, senderId, NotificationChannel.EMAIL,
                            "TRANSFER_COMPLETED",
                            "Transfer Confirmed",
                            "Your transfer of " + amount + " " + currency + " has been completed successfully."
                    );
                }
                case "TRANSFER_FAILED" -> {
                    // TRANSFER_FAILED payload contains 'senderId', not 'userId'
                    UUID senderId = parseUUID(root.path("payload").path("senderId").asText(), eventType);
                    if (senderId == null) break;
                    notificationService.send(
                            idempotencyKey, senderId, NotificationChannel.EMAIL,
                            "TRANSFER_FAILED",
                            "Transfer Failed",
                            "Your transfer could not be completed. Any reserved funds have been returned."
                    );
                }
                case "TRANSACTION_FLAGGED" -> {
                    UUID userId = parseUUID(root.path("payload").path("userId").asText(), eventType);
                    if (userId == null) break;
                    notificationService.send(
                            idempotencyKey, userId, NotificationChannel.EMAIL,
                            "TRANSACTION_FLAGGED",
                            "Security Alert \u2014 Transaction Under Review",
                            "A transaction on your account has been flagged for review. If this was not you, contact support immediately."
                    );
                }
                case "CASH_IN_COMPLETED" -> {
                    UUID userId = parseUUID(root.path("payload").path("userId").asText(), eventType);
                    if (userId == null) break;
                    String amount = root.path("payload").path("amount").asText();
                    String ccy = root.path("payload").path("currency").asText();
                    notificationService.send(idempotencyKey, userId, NotificationChannel.EMAIL,
                            "CASH_IN_COMPLETED", "Top-Up Successful",
                            "Your wallet has been credited with " + amount + " " + ccy + ".");
                }
                case "CASH_OUT_COMPLETED" -> {
                    UUID userId = parseUUID(root.path("payload").path("userId").asText(), eventType);
                    if (userId == null) break;
                    String amount = root.path("payload").path("amount").asText();
                    String ccy = root.path("payload").path("currency").asText();
                    notificationService.send(idempotencyKey, userId, NotificationChannel.EMAIL,
                            "CASH_OUT_COMPLETED", "Withdrawal Processed",
                            "Your withdrawal of " + amount + " " + ccy + " has been sent to your card.");
                }
                case "BILL_PAY_COMPLETED" -> {
                    UUID userId = parseUUID(root.path("payload").path("userId").asText(), eventType);
                    if (userId == null) break;
                    String amount = root.path("payload").path("amount").asText();
                    notificationService.send(idempotencyKey, userId, NotificationChannel.EMAIL,
                            "BILL_PAY_COMPLETED", "Bill Payment Successful",
                            "Your bill payment of " + amount + " has been processed successfully.");
                }
                case "CASH_IN_FAILED", "CASH_OUT_FAILED", "BILL_PAY_FAILED" -> {
                    UUID userId = parseUUID(root.path("payload").path("userId").asText(), eventType);
                    if (userId == null) break;
                    notificationService.send(idempotencyKey, userId, NotificationChannel.EMAIL,
                            eventType,
                            "Transaction Failed",
                            "Your recent transaction could not be completed. Any reserved funds have been returned.");
                }
                default -> log.debug("No notification mapping for eventType={}", eventType);
            }

            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Notification consumer error: topic={} offset={}", record.topic(), record.offset(), ex);
            throw new RuntimeException("Notification event processing failed", ex);
        }
    }

    /**
     * Safely parses a UUID string. Logs a warning and returns null if the value is
     * blank or malformed, preventing a poisoned Kafka message from blocking the partition.
     */
    private UUID parseUUID(String value, String eventType) {
        if (value == null || value.isBlank()) {
            log.warn("Notification consumer: missing UUID field for eventType={}", eventType);
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("Notification consumer: invalid UUID '{}' for eventType={} — skipping", value, eventType);
            return null;
        }
    }
}
