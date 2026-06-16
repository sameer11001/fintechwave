package com.fintechwave.ledger.query.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Document(collection = "wallet_summaries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletSummaryView {

    @Id
    private UUID userId;

    // Currency -> Balance mapping
    private Map<String, java.math.BigDecimal> balances;

    // AccountId -> Currency mapping
    private Map<UUID, String> accounts;

    private Instant updatedAt;
}
