package com.fintechwave.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

public record NetFlowResponse(
    List<String> labels,
    List<BigDecimal> cashIn,
    List<BigDecimal> cashOut,
    List<BigDecimal> net
) {}
