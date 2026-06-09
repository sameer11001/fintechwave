package com.fintechwave.fraud.dto;

import com.fintechwave.fraud.domain.enums.FraudDecisionType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class FraudDecisionResponse {
    private UUID id;
    private UUID transactionId;
    private UUID userId;
    private FraudDecisionType decision;
    private int riskScore;
    private List<String> triggeredRules;
    private BigDecimal amount;
    private String currency;
    private Instant decidedAt;
}
