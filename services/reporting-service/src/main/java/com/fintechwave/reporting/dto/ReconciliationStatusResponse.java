package com.fintechwave.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ReconciliationStatusResponse(
    BigDecimal assetFloatBalance,
    BigDecimal userLiabilities,
    BigDecimal divergenceDiscrepancy,
    String reconStatus,
    Instant lastRun
) {}
