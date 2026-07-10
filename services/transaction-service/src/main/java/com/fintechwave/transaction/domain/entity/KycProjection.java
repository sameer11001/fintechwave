package com.fintechwave.transaction.domain.entity;

import com.fintechwave.transaction.domain.enums.KycStatus;
import com.fintechwave.transaction.domain.enums.KycTier;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tx_kyc_projections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycProjection {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_tier", nullable = false, length = 10)
    private KycTier currentTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private KycStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
