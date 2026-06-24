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
public class LedgerResultConsumer {

    private final ITransactionService transactionService;
    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @KafkaListener(topics = "ledger.transaction-results", groupId = "tx-service-ledger-results", containerFactory = "kafkaListenerContainerFactory")
    public void onLedgerResult(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(record.value());

            String eventType = root.path("eventType").asText();
            UUID transactionId = UUID.fromString(root.path("aggregateId").asText());

            if ("LEDGER_COMMITTED".equals(eventType)) {
                transactionService.markLedgerCommitted(transactionId);
            } else {
                log.debug("Ignoring ledger result type={}", eventType);
            }

            ack.acknowledge();
        } catch (IllegalArgumentException e) {
            log.error("Poison pill or invalid format in ledger result event (skipping): offset={} key={}", record.offset(), record.key(), e);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing ledger result event: offset={} key={}", record.offset(), record.key(), e);
            throw new RuntimeException("Failed to process ledger result", e);
        }
    }
}
