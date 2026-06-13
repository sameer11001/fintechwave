package com.fintechwave.reporting.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.reporting.domain.entity.*;
import com.fintechwave.reporting.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

    private final TransactionSummaryRepository txSummaryRepo;
    private final DailyVolumeRepository dailyVolumeRepo;
    private final FailedTxRateRepository failedTxRateRepo;
    private final ProcessedEventRepository processedEventRepo;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"tx.transaction-events"},
            groupId = "reporting-service-tx",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onTransactionEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
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

            switch (eventType) {
                case "TRANSFER_COMPLETED", "TRANSFER_REVERSED" ->
                        upsertTxSummary(payload, "P2P_TRANSFER",
                                "TRANSFER_COMPLETED".equals(eventType) ? "COMPLETED" : "REVERSED", false);

                case "TRANSFER_FAILED" ->
                        upsertTxSummary(payload, "P2P_TRANSFER", "FAILED", true);

                case "CASH_IN_COMPLETED" ->
                        upsertTxSummary(payload, "CASH_IN", "COMPLETED", false);

                case "CASH_IN_FAILED" ->
                        upsertTxSummary(payload, "CASH_IN", "FAILED", true);

                case "CASH_OUT_COMPLETED" ->
                        upsertTxSummary(payload, "CASH_OUT", "COMPLETED", false);

                case "CASH_OUT_FAILED" ->
                        upsertTxSummary(payload, "CASH_OUT", "FAILED", true);

                case "BILL_PAY_COMPLETED" ->
                        upsertTxSummary(payload, "BILL_PAY", "COMPLETED", false);

                case "BILL_PAY_FAILED" ->
                        upsertTxSummary(payload, "BILL_PAY", "FAILED", true);

                default -> log.debug("Reporting: no projection for transaction eventType={}", eventType);
            }

            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Reporting transaction consumer error: topic={} offset={}", record.topic(), record.offset(), ex);
            throw new RuntimeException("Reporting transaction event processing failed", ex);
        }
    }

    private void upsertTxSummary(JsonNode payload, String txType, String status, boolean failed) {
        UUID transactionId = UUID.fromString(payload.path("transactionId").asText());
        if (txSummaryRepo.existsByTransactionId(transactionId)) {
            txSummaryRepo.findByTransactionId(transactionId).ifPresent(s -> {
                s.setStatus(status);
                txSummaryRepo.save(s);
            });
            return;
        }

        String userIdStr = payload.has("userId") ? payload.path("userId").asText() : payload.path("senderId").asText();
        UUID userId      = UUID.fromString(userIdStr);
        BigDecimal amount = new BigDecimal(payload.path("amount").asText("0"));
        String currency  = payload.path("currency").asText("USD");
        UUID counterparty = payload.has("receiverId")
                ? UUID.fromString(payload.path("receiverId").asText()) : null;

        txSummaryRepo.save(TransactionSummary.builder()
                .transactionId(transactionId)
                .userId(userId)
                .transactionType(txType)
                .status(status)
                .amount(amount)
                .currency(currency)
                .counterpartyId(counterparty)
                .occurredAt(Instant.now())
                .build());

        if (!failed) {
            upsertDailyVolume(txType, amount, currency);
        }

        updateFailedTxRate(failed);
    }

    private void upsertDailyVolume(String txType, BigDecimal amount, String currency) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        dailyVolumeRepo
                .findByReportDateAndTransactionTypeAndCurrency(today, txType, currency)
                .ifPresentOrElse(vol -> {
                    vol.setTotalCount(vol.getTotalCount() + 1);
                    vol.setTotalAmount(vol.getTotalAmount().add(amount));
                    vol.setUpdatedAt(Instant.now());
                    dailyVolumeRepo.save(vol);
                }, () -> dailyVolumeRepo.save(DailyVolume.builder()
                        .reportDate(today)
                        .transactionType(txType)
                        .totalCount(1L)
                        .totalAmount(amount)
                        .currency(currency)
                        .updatedAt(Instant.now())
                        .build()));
    }

    private void updateFailedTxRate(boolean failed) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        failedTxRateRepo.findByReportDate(today).ifPresentOrElse(rate -> {
            rate.setTotalCount(rate.getTotalCount() + 1);
            if (failed) rate.setFailedCount(rate.getFailedCount() + 1);
            rate.setFailureRate(BigDecimal.valueOf(rate.getFailedCount())
                    .divide(BigDecimal.valueOf(rate.getTotalCount()), 4, RoundingMode.HALF_UP));
            rate.setUpdatedAt(Instant.now());
            failedTxRateRepo.save(rate);
        }, () -> failedTxRateRepo.save(FailedTxRate.builder()
                .reportDate(today)
                .totalCount(1L)
                .failedCount(failed ? 1L : 0L)
                .failureRate(failed ? BigDecimal.ONE : BigDecimal.ZERO)
                .updatedAt(Instant.now())
                .build()));
    }
}
