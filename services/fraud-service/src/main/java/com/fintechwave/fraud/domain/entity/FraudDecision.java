package com.fintechwave.fraud.domain.entity;

import com.fintechwave.fraud.domain.enums.FraudDecisionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "fraud_decision")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FraudDecisionType decision;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Array(length = 20)
    @Column(name = "triggered_rules", columnDefinition = "text[]")
    private List<String> triggeredRules;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Builder.Default
    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt = Instant.now();
}
