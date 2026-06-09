package com.fintechwave.kyc.domain.entity;

import com.fintechwave.kyc.domain.enums.KycStatus;
import com.fintechwave.kyc.domain.enums.KycTier;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "kyc_applications", indexes = {
        @Index(name = "idx_kyc_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_kyc_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private KycStatus status = KycStatus.PENDING_SUBMISSION;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_tier", nullable = false, length = 10)
    @Builder.Default
    private KycTier currentTier = KycTier.TIER_0;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_tier", nullable = false, length = 10)
    @Builder.Default
    private KycTier requestedTier = KycTier.TIER_1;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<KycDocument> documents = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;
}
