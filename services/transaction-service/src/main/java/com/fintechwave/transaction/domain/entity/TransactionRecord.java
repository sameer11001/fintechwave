package com.fintechwave.transaction.domain.entity;

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
    private TransactionStatus status = TransactionStatus.INITIATED;

    /** Initiating user (wallet owner who is deducted or who initiates). */
    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    /**
     * Receiving user — null for CASH_IN / CASH_OUT where platform is counterparty.
     */
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

    /**
     * Stripe Payment Intent ID — set for CASH_IN after createCardPaymentIntent().
     */
    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    /**
     * Stripe Payout ID — set for CASH_OUT after initiateInstantPayout().
     */
    @Column(name = "stripe_payout_id", length = 255)
    private String stripePayoutId;

    /**
     * Unique key for idempotency — same key = same transaction (deduplicated at
     * insert).
     */
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
}
