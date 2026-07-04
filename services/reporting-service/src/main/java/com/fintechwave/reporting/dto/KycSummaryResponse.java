package com.fintechwave.reporting.dto;

public record KycSummaryResponse(
    long pending,
    long approved,
    long rejected
) {}
