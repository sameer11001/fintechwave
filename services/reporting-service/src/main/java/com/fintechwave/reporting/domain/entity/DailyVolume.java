package com.fintechwave.reporting.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_volume")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyVolume {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "transaction_type", nullable = false)
    private String transactionType;

    @Column(name = "total_count", nullable = false)
    private long totalCount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
