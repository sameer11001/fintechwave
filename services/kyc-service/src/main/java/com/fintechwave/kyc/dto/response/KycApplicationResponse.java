package com.fintechwave.kyc.dto.response;

import com.fintechwave.kyc.domain.entity.KycApplication;
import com.fintechwave.kyc.domain.enums.KycStatus;
import com.fintechwave.kyc.domain.enums.KycTier;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record KycApplicationResponse(
        UUID id,
        UUID userId,
        KycStatus status,
        KycTier currentTier,
        KycTier requestedTier,
        /** Null when not rejected. Never contains PII. */
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static KycApplicationResponse from(KycApplication app) {
        return KycApplicationResponse.builder()
                .id(app.getId())
                .userId(app.getUserId())
                .status(app.getStatus())
                .currentTier(app.getCurrentTier())
                .requestedTier(app.getRequestedTier())
                .rejectionReason(app.getRejectionReason())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
