package com.fintechwave.transaction.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fintechwave.transaction.domain.enums.TransactionSagaStep;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_saga_states", indexes = {
        @Index(name = "idx_saga_step", columnList = "current_step"),
        @Index(name = "idx_saga_sender", columnList = "sender_id"),
        @Index(name = "idx_saga_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionSagaState {

    @Id
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 30)
    private TransactionSagaStep currentStep;

    @Column(name = "saga_type", nullable = false, length = 10)
    private String sagaType;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "receiver_id")
    private UUID receiverId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
