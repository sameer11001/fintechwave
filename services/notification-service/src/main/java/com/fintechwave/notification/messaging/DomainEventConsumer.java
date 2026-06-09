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
            UUID   idempotencyKey  = UUID.fromString(root.path("idempotencyKey").asText());

            log.debug("Notification consumer received: eventType={} topic={}", eventType, record.topic());

            switch (eventType) {
                case "KYC_VERIFIED" -> {
                    UUID userId = UUID.fromString(root.path("payload").path("userId").asText());
                    notificationService.send(
                            idempotencyKey, userId, NotificationChannel.EMAIL,
                            "KYC_VERIFIED",
                            "Your KYC verification is approved",
                            "Congratulations! Your identity has been verified and your wallet is ready."
                    );
                }
                case "KYC_REJECTED" -> {
                    UUID userId = UUID.fromString(root.path("payload").path("userId").asText());
                    String reason = root.path("payload").path("rejectionReason").asText("Please resubmit your documents.");
                    notificationService.send(
                            idempotencyKey, userId, NotificationChannel.EMAIL,
                            "KYC_REJECTED",
                            "Your KYC verification was not approved",
                            "Unfortunately your verification was rejected. Reason: " + reason
                    );
                }
                case "WALLET_PROVISIONED" -> {
                    UUID userId = UUID.fromString(root.path("payload").path("userId").asText());
                    notificationService.send(
                            idempotencyKey, userId, NotificationChannel.EMAIL,
                            "WALLET_PROVISIONED",
                            "Welcome to FintechWave!",
                            "Your wallet has been created and is ready to use."
                    );
                }
                case "TRANSFER_COMPLETED" -> {
                    UUID senderId   = UUID.fromString(root.path("payload").path("senderId").asText());
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
                    UUID userId = UUID.fromString(root.path("payload").path("userId").asText());
                    notificationService.send(
                            idempotencyKey, userId, NotificationChannel.EMAIL,
                            "TRANSFER_FAILED",
                            "Transfer Failed",
                            "Your transfer could not be completed. Any reserved funds have been returned."
                    );
                }
                case "TRANSACTION_FLAGGED" -> {
                    UUID userId = UUID.fromString(root.path("payload").path("userId").asText());
                    notificationService.send(
                            idempotencyKey, userId, NotificationChannel.EMAIL,
                            "TRANSACTION_FLAGGED",
                            "Security Alert — Transaction Under Review",
                            "A transaction on your account has been flagged for review. If this was not you, contact support immediately."
                    );
                }
                case "CASH_IN_COMPLETED" -> {
                    UUID userId = UUID.fromString(root.path("payload").path("userId").asText());
                    String amount = root.path("payload").path("amount").asText();
                    String ccy = root.path("payload").path("currency").asText();
                    notificationService.send(idempotencyKey, userId, NotificationChannel.EMAIL,
                            "CASH_IN_COMPLETED", "Top-Up Successful",
                            "Your wallet has been credited with " + amount + " " + ccy + ".");
                }
                case "CASH_OUT_COMPLETED" -> {
                    UUID userId = UUID.fromString(root.path("payload").path("userId").asText());
                    String amount = root.path("payload").path("amount").asText();
                    String ccy = root.path("payload").path("currency").asText();
                    notificationService.send(idempotencyKey, userId, NotificationChannel.EMAIL,
                            "CASH_OUT_COMPLETED", "Withdrawal Processed",
                            "Your withdrawal of " + amount + " " + ccy + " has been sent to your card.");
                }
                case "BILL_PAY_COMPLETED" -> {
                    UUID userId = UUID.fromString(root.path("payload").path("userId").asText());
                    String amount = root.path("payload").path("amount").asText();
                    notificationService.send(idempotencyKey, userId, NotificationChannel.EMAIL,
                            "BILL_PAY_COMPLETED", "Bill Payment Successful",
                            "Your bill payment of " + amount + " has been processed successfully.");
                }
                case "CASH_IN_FAILED", "CASH_OUT_FAILED", "BILL_PAY_FAILED" -> {
                    UUID userId = UUID.fromString(root.path("payload").path("userId").asText());
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
}
