package com.fintechwave.fraud.query.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Document(collection = "fraud_risk_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRiskProfileView {

    @Id
    private UUID userId;

    private Integer riskScore;
    
    @Builder.Default
    private List<UUID> flaggedTransactions = new ArrayList<>();

    private Instant updatedAt;
}
