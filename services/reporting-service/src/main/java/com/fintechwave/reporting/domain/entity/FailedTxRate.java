package com.fintechwave.reporting.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "failed_tx_rate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailedTxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "report_date", nullable = false, unique = true)
    private LocalDate reportDate;

    @Column(name = "total_count", nullable = false)
    private long totalCount;

    @Column(name = "failed_count", nullable = false)
    private long failedCount;

    @Column(name = "failure_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal failureRate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
