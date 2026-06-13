package com.fintechwave.reporting.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.reporting.domain.entity.BalanceSnapshot;
import com.fintechwave.reporting.domain.entity.ProcessedEvent;
import com.fintechwave.reporting.repository.BalanceSnapshotRepository;
import com.fintechwave.reporting.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerEventConsumer {

    private final BalanceSnapshotRepository balanceSnapshotRepo;
    private final ProcessedEventRepository processedEventRepo;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"ledger.wallet-events"},
            groupId = "reporting-service-ledger",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onLedgerEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventType       = root.path("eventType").asText();
            UUID   idempotencyKey  = UUID.fromString(root.path("idempotencyKey").asText());

            try {
                processedEventRepo.save(ProcessedEvent.builder()
                        .idempotencyKey(idempotencyKey)
                        .processedAt(Instant.now())
                        .build());
            } catch (DataIntegrityViolationException ex) {
                log.debug("Reporting: duplicate event skipped eventType={} key={}", eventType, idempotencyKey);
                ack.acknowledge();
                return;
            }

            JsonNode payload = root.path("payload");

            if ("LEDGER_ENTRY_COMMITTED".equals(eventType)) {
                UUID userId    = UUID.fromString(payload.path("userId").asText());
                UUID accountId = UUID.fromString(payload.path("accountId").asText());
                BigDecimal balance  = new BigDecimal(payload.path("balance").asText("0"));
                String currency     = payload.path("currency").asText("USD");

                balanceSnapshotRepo.save(BalanceSnapshot.builder()
                        .userId(userId)
                        .accountId(accountId)
                        .balance(balance)
                        .currency(currency)
                        .snapshotAt(Instant.now())
                        .build());
            } else {
                log.debug("Reporting: no projection for ledger eventType={}", eventType);
            }

            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Reporting ledger consumer error: topic={} offset={}", record.topic(), record.offset(), ex);
            throw new RuntimeException("Reporting ledger event processing failed", ex);
        }
    }
}
