package com.fintechwave.reporting.dto;

import java.util.List;

public record RevenueTrendResponse(
    String period,
    List<String> labels,
    List<Double> revenueInThousands
) {}
