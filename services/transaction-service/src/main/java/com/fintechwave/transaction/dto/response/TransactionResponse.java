package com.fintechwave.transaction.dto.response;

import com.fintechwave.transaction.domain.enums.TransactionStatus;
import com.fintechwave.transaction.domain.enums.TransactionType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record TransactionResponse(
                UUID id,
                TransactionType transactionType,
                TransactionStatus status,
                UUID senderId,
                UUID receiverId,
                BigDecimal amount,
                String currency,
                BigDecimal feeAmount,
                String stripePaymentIntentId,
                String description,
                UUID idempotencyKey,
                Instant createdAt,
                Instant updatedAt) {
}
