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

    @KafkaListener(topics = "tx.transaction-events", groupId = "ledger-service-tx", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onTransactionEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventType = root.path("eventType").asText();
            UUID transactionId = UUID.fromString(root.path("aggregateId").asText());
            JsonNode payload = root.path("payload");
            if ("TRANSFER_COMPLETED".equals(eventType)) {
                UUID receiverId = UUID.fromString(payload.path("receiverId").asText());
                BigDecimal amount = new BigDecimal(payload.path("amount").asText());
                String currency = payload.path("currency").asText("USD");

                UUID receiverWalletId = ledgerService.getWalletBalance(receiverId).getAccountId();
                ledgerService.commit(transactionId, receiverWalletId, amount, currency);
                log.info("Ledger committed funds for P2P txId={}", transactionId);
            } else if ("TRANSFER_FAILED".equals(eventType)) {
                UUID senderId = UUID.fromString(payload.path("senderId").asText());
                BigDecimal amount = new BigDecimal(payload.path("amount").asText());
                String currency = payload.path("currency").asText("USD");

                UUID senderWalletId = ledgerService.getWalletBalance(senderId).getAccountId();
                ledgerService.release(transactionId, senderWalletId, amount, currency);
                log.info("Ledger released funds for failed P2P txId={}", transactionId);
            } else if ("CASH_IN_COMPLETED".equals(eventType)) {
                UUID userId = UUID.fromString(payload.path("userId").asText());
                BigDecimal amount = new BigDecimal(payload.path("amount").asText());
                String currency = payload.path("currency").asText("USD");

                UUID userWalletId = ledgerService.getWalletBalance(userId).getAccountId();
                Account platformFloat = ledgerService.getOrCreatePlatformAccount(AccountCode.PLATFORM_FLOAT, currency);

                ledgerService.commitDoubleEntry(new DoubleEntryRequest(
                        transactionId,
                        List.of(
                                new DoubleEntryRequest.EntryLine(platformFloat.getId(), "DEBIT", amount, currency,
                                        UUID.nameUUIDFromBytes((transactionId.toString() + "-debit").getBytes()),
                                        "CASH-IN: debit platform"),
                                new DoubleEntryRequest.EntryLine(userWalletId, "CREDIT", amount, currency,
                                        UUID.nameUUIDFromBytes((transactionId.toString() + "-credit").getBytes()),
                                        "CASH-IN: credit wallet"))));
                log.info("Ledger processed CASH_IN_COMPLETED txId={}", transactionId);
            }
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Transaction event consumer error: offset={} key={}", record.offset(), record.key(), ex);
            throw new RuntimeException("Transaction event processing failed", ex);
        }
    }
}