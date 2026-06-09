package com.fintechwave.fraud.domain.entity;

import com.fintechwave.fraud.domain.enums.RuleAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Configurable fraud rule. Hot-reloaded via @RefreshScope on FraudProperties.
 * Loaded from DB at startup and refreshed on /actuator/refresh.
 */
@Entity
@Table(name = "fraud_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudRule {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "rule_code", nullable = false, unique = true)
    private String ruleCode;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    private BigDecimal threshold;

    @Column(name = "window_sec")
    private Integer windowSec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleAction action;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
