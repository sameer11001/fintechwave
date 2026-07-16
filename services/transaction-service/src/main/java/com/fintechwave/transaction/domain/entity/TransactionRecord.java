package com.fintechwave.transaction.domain.entity;

import com.fintechwave.core.exception.InvalidStateTransitionException;
import com.fintechwave.transaction.domain.enums.TransactionStatus;
import com.fintechwave.transaction.domain.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Core transaction aggregate.
 * Tracks the full lifecycle of a money movement through the state machine.
 *
 * Design notes:
 * - idempotencyKey ensures the same transaction is not created twice
 * - stripePaymentIntentId / stripePayoutId are populated by the Stripe adapter
 * - feeAmount is computed by FeeCalculationService and persisted for audit
 * - receiverId is null for CASH_IN / CASH_OUT (platform is counterparty)
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_sender_id", columnList = "sender_id"),
        @Index(name = "idx_tx_receiver_id", columnList = "receiver_id"),
        @Index(name = "idx_tx_status", columnList = "status"),
        @Index(name = "idx_tx_type", columnList = "transaction_type"),
        @Index(name = "idx_tx_idempotency_key", columnList = "idempotency_key", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private TransactionStatus status = TransactionStatus.INITIATED;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "receiver_id")
    private UUID receiverId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "stripe_payout_id", length = 255)
    private String stripePayoutId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(length = 500)
    private String description;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    public void transition(TransactionStatus newStatus) {
        if (!isValidTransition(this.status, newStatus)) {
            throw new InvalidStateTransitionException(
                    "TransactionRecord", this.status, newStatus);
        }
        this.status = newStatus;
    }

    private static boolean isValidTransition(TransactionStatus from, TransactionStatus to) {
        return switch (from) {
            case INITIATED -> to == TransactionStatus.RESERVED || to == TransactionStatus.FAILED
                    || to == TransactionStatus.PENDING_LEDGER || to == TransactionStatus.FRAUD_CHECK;
            case FRAUD_CHECK ->
                to == TransactionStatus.RESERVED || to == TransactionStatus.FLAGGED || to == TransactionStatus.FAILED;
            case RESERVED -> to == TransactionStatus.COMPLETED || to == TransactionStatus.FAILED || to == TransactionStatus.PENDING_LEDGER;
            case PENDING_LEDGER -> to == TransactionStatus.COMPLETED || to == TransactionStatus.FAILED;
            case FLAGGED -> to == TransactionStatus.COMPLETED || to == TransactionStatus.FAILED;
            case FAILED -> to == TransactionStatus.REVERSED || to == TransactionStatus.REFUNDED;
            case COMPLETED, REVERSED, REFUNDED, COMMITTED -> false; // terminal
        };
    }
}
