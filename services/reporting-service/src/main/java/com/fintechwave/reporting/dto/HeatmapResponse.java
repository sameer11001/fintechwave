package com.fintechwave.reporting.dto;

import java.util.List;

public record HeatmapResponse(
    List<String> yLabels, // Days of week
    List<Integer> xLabels, // Hours of day
    double[][] data // Matrix
) {}
