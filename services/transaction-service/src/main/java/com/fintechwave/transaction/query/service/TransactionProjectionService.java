package com.fintechwave.transaction.query.service;

import com.fintechwave.transaction.dto.response.TransactionResponse;
import com.fintechwave.transaction.exception.TransactionNotFoundException;
import com.fintechwave.transaction.query.entity.TransactionHistoryView;
import com.fintechwave.transaction.query.repository.TransactionHistoryViewRepository;
import com.fintechwave.transaction.domain.enums.TransactionType;
import com.fintechwave.transaction.domain.enums.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionProjectionService {

    private final TransactionHistoryViewRepository repository;
    private final StringRedisTemplate redisTemplate;

    private static final String HISTORY_CACHE_PREFIX = "fintechwave:tx-service:history:";

    public void handleTransactionEvent(UUID txId, UUID senderId, UUID receiverId, BigDecimal amount, String currency,
            String type, String status, String failureReason) {
        TransactionHistoryView view = repository.findById(txId).orElseGet(() -> TransactionHistoryView.builder()
                .id(txId)
                .senderId(senderId)
                .receiverId(receiverId)
                .amount(amount)
                .currency(currency)
                .type(type)
                .createdAt(Instant.now())
                .build());

        view.setStatus(status);
        if (failureReason != null) {
            view.setFailureReason(failureReason);
        }
        view.setUpdatedAt(Instant.now());

        repository.save(view);

        // Invalidate user history caches
        if (senderId != null)
            redisTemplate.delete(HISTORY_CACHE_PREFIX + senderId);
        if (receiverId != null)
            redisTemplate.delete(HISTORY_CACHE_PREFIX + receiverId);

        log.info("Projected transaction {} with status {}", txId, status);
    }

    public List<TransactionHistoryView> getUserHistory(UUID userId) {
        try {
            return repository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId);
        } catch (Exception e) {
            log.error("Failed to fetch tx history for userId={}", userId, e);
            return List.of();
        }
    }

    public Page<TransactionResponse> getUserTransactions(UUID userId, Pageable pageable) {
        return repository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId, pageable)
                .map(this::mapToResponse);
    }

    public TransactionResponse getTransactionById(UUID txId) {
        TransactionHistoryView view = repository.findById(txId)
                .orElseThrow(() -> new TransactionNotFoundException(txId));
        return mapToResponse(view);
    }

    private TransactionResponse mapToResponse(TransactionHistoryView view) {
        return TransactionResponse.builder()
                .id(view.getId())
                .transactionType(TransactionType.valueOf(view.getType()))
                .status(TransactionStatus.valueOf(view.getStatus()))
                .senderId(view.getSenderId())
                .receiverId(view.getReceiverId())
                .amount(view.getAmount())
                .currency(view.getCurrency())
                .createdAt(view.getCreatedAt())
                .updatedAt(view.getUpdatedAt())
                .build();
    }
}
