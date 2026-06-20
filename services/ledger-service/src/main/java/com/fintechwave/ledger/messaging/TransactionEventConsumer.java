package com.fintechwave.ledger.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.ledger.domain.entity.Account;
import com.fintechwave.ledger.domain.enums.AccountCode;
import com.fintechwave.ledger.dto.request.DoubleEntryRequest;
import com.fintechwave.ledger.service.ILedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {
    private final ILedgerService ledgerService;
    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @KafkaListener(topics = "tx.transaction-events", groupId = "ledger-service-tx", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onTransactionEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());

            String eventIdStr = root.path("idempotencyKey").asText();
            Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent("processed:ledger-tx:" + eventIdStr, "1", java.time.Duration.ofDays(7));
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Event {} already processed, skipping", eventIdStr);
                ack.acknowledge();
                return;
            }

            String eventType = root.path("eventType").asText();
            UUID transactionId = UUID.fromString(root.path("aggregateId").asText());
            JsonNode payload = root.path("payload");

            switch (eventType) {
                case "TRANSFER_COMPLETED" -> handleTransferCompleted(transactionId, payload);
                case "TRANSFER_FAILED" -> handleTransferFailed(transactionId, payload);
                case "CASH_IN_COMPLETED" -> handleCashInCompleted(transactionId, payload);
                case "CASH_OUT_INITIATED" -> handleCashOutInitiated(transactionId, payload);
                case "CASH_OUT_COMPLETED" -> handleCashOutCompleted(transactionId, payload);
                case "CASH_OUT_FAILED" -> handleCashOutFailed(transactionId, payload);
                default -> log.error("UNKNOWN OR MISSING eventType='{}' received on tx.transaction-events. txId={} Message ignored but requires investigation!", eventType, transactionId);
            }

            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Transaction event consumer error: offset={} key={}", record.offset(), record.key(), ex);
            throw new RuntimeException("Transaction event processing failed", ex);
        }
    }

    private void handleTransferCompleted(UUID transactionId, JsonNode payload) {
        UUID receiverId = UUID.fromString(payload.path("receiverId").asText());
        BigDecimal amount = new BigDecimal(payload.path("amount").asText());
        String currency = payload.path("currency").asText("USD");

        UUID receiverWalletId = ledgerService.getWalletBalance(receiverId).getAccountId();
        ledgerService.commit(transactionId, receiverWalletId, amount, currency);
        log.info("Ledger committed funds for P2P txId={}", transactionId);
    }

    private void handleTransferFailed(UUID transactionId, JsonNode payload) {
        UUID senderId = UUID.fromString(payload.path("senderId").asText());
        BigDecimal amount = new BigDecimal(payload.path("amount").asText());
        String currency = payload.path("currency").asText("USD");

        UUID senderWalletId = ledgerService.getWalletBalance(senderId).getAccountId();
        ledgerService.release(transactionId, senderWalletId, amount, currency);
        log.info("Ledger released funds for failed P2P txId={}", transactionId);
    }

    private void handleCashInCompleted(UUID transactionId, JsonNode payload) {
        UUID userId = UUID.fromString(payload.path("userId").asText());
        BigDecimal amount = new BigDecimal(payload.path("amount").asText());
        String currency = payload.path("currency").asText("USD");

        UUID userWalletId = ledgerService.getWalletBalance(userId).getAccountId();
        Account platformFloat = ledgerService.getOrCreatePlatformAccount(AccountCode.PLATFORM_FLOAT, currency);

        ledgerService.commitDoubleEntry(new DoubleEntryRequest(
                transactionId,
                List.of(
                        new DoubleEntryRequest.EntryLine(
                                platformFloat.getId(), "DEBIT", amount, currency,
                                ikey(transactionId, "cashin-debit"),
                                "CASH-IN: debit platform float"),
                        new DoubleEntryRequest.EntryLine(
                                userWalletId, "CREDIT", amount, currency,
                                ikey(transactionId, "cashin-credit"),
                                "CASH-IN: credit user wallet"))));
        log.info("Ledger processed CASH_IN_COMPLETED txId={}", transactionId);
    }

    private void handleCashOutInitiated(UUID transactionId, JsonNode payload) {
        String currency = payload.path("currency").asText("USD");
        BigDecimal feeAmount = new BigDecimal(payload.path("feeAmount").asText("0"));

        if (feeAmount.compareTo(BigDecimal.ZERO) == 0) {
            log.info("CASH_OUT_INITIATED: zero fee, skipping fee recognition txId={}", transactionId);
            return;
        }

        Account suspense = ledgerService.getOrCreatePlatformAccount(AccountCode.SUSPENSE, currency);
        Account feeRevenue = ledgerService.getOrCreatePlatformAccount(AccountCode.CASHOUT_FEE_REVENUE, currency);

        ledgerService.commitDoubleEntry(new DoubleEntryRequest(
                transactionId,
                List.of(
                        new DoubleEntryRequest.EntryLine(
                                suspense.getId(), "DEBIT", feeAmount, currency,
                                ikey(transactionId, "cashout-fee-debit-suspense"),
                                "CASH-OUT: debit suspense for fee recognition"),
                        new DoubleEntryRequest.EntryLine(
                                feeRevenue.getId(), "CREDIT", feeAmount, currency,
                                ikey(transactionId, "cashout-fee-credit-revenue"),
                                "CASH-OUT: credit fee revenue"))));

        log.info("CASH_OUT_INITIATED: fee recognised txId={} fee={} {}", transactionId, feeAmount, currency);
    }

    private void handleCashOutCompleted(UUID transactionId, JsonNode payload) {
        BigDecimal amount = new BigDecimal(payload.path("amount").asText());
        String currency = payload.path("currency").asText("USD");

        Account suspense = ledgerService.getOrCreatePlatformAccount(AccountCode.SUSPENSE, currency);
        Account platformFloat = ledgerService.getOrCreatePlatformAccount(AccountCode.PLATFORM_FLOAT, currency);

        // Dr Suspense → Cr Platform Float (funds physically left the platform)
        ledgerService.commitDoubleEntry(new DoubleEntryRequest(
                transactionId,
                List.of(
                        new DoubleEntryRequest.EntryLine(
                                suspense.getId(), "DEBIT", amount, currency,
                                ikey(transactionId, "cashout-complete-debit"),
                                "CASH-OUT COMPLETED: debit suspense"),
                        new DoubleEntryRequest.EntryLine(
                                platformFloat.getId(), "CREDIT", amount, currency,
                                ikey(transactionId, "cashout-complete-credit"),
                                "CASH-OUT COMPLETED: credit platform float (funds left)"))));

        log.info("Ledger processed CASH_OUT_COMPLETED txId={} amount={} {}", transactionId, amount, currency);
    }

    private void handleCashOutFailed(UUID transactionId, JsonNode payload) {
        UUID userId = UUID.fromString(payload.path("userId").asText());
        BigDecimal totalRefund = new BigDecimal(payload.path("amount").asText()); // amount+fee sent by tx-service
        String currency = payload.path("currency").asText("USD");

        UUID userWalletId = ledgerService.getWalletBalance(userId).getAccountId();
        Account suspense = ledgerService.getOrCreatePlatformAccount(AccountCode.SUSPENSE, currency);

        // Dr Suspense → Cr User Wallet (full refund)
        ledgerService.commitDoubleEntry(new DoubleEntryRequest(
                transactionId,
                List.of(
                        new DoubleEntryRequest.EntryLine(
                                suspense.getId(), "DEBIT", totalRefund, currency,
                                ikey(transactionId, "cashout-fail-debit"),
                                "CASH-OUT FAILED: debit suspense for refund"),
                        new DoubleEntryRequest.EntryLine(
                                userWalletId, "CREDIT", totalRefund, currency,
                                ikey(transactionId, "cashout-fail-credit"),
                                "CASH-OUT FAILED: credit user wallet (full refund)"))));

        log.info("Ledger processed CASH_OUT_FAILED: refunded txId={} amount={} {}", transactionId, totalRefund,
                currency);
    }

    private static UUID ikey(UUID transactionId, String suffix) {
        return UUID.nameUUIDFromBytes((transactionId.toString() + "-" + suffix).getBytes());
    }
}