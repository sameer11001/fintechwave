package com.fintechwave.reporting.dto;

public record DashboardSummaryResponse(
    double totalAum,
    double dailyVolume,
    long activeUsersCount,
    long pendingKycApprovals
) {}
