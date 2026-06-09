package com.fintechwave.reporting.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_processed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @Builder.Default
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();
}
