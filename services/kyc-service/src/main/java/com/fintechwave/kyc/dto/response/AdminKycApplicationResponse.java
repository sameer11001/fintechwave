package com.fintechwave.kyc.dto.response;

import com.fintechwave.kyc.domain.entity.KycApplication;
import com.fintechwave.kyc.domain.enums.KycStatus;
import com.fintechwave.kyc.domain.enums.KycTier;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record AdminKycApplicationResponse(
        UUID id,
        UUID userId,
        KycStatus status,
        KycTier currentTier,
        KycTier requestedTier,
        /** Null when not rejected. Never contains PII. */
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt,
        List<KycDocumentResponse> documents
) {
    public static AdminKycApplicationResponse from(KycApplication app, List<KycDocumentResponse> documents) {
        return AdminKycApplicationResponse.builder()
                .id(app.getId())
                .userId(app.getUserId())
                .status(app.getStatus())
                .currentTier(app.getCurrentTier())
                .requestedTier(app.getRequestedTier())
                .rejectionReason(app.getRejectionReason())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .documents(documents)
                .build();
    }
}
