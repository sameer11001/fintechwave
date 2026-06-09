package com.fintechwave.reporting.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyc_status_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycStatusSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "kyc_status", nullable = false)
    private String kycStatus;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
