package com.fintechwave.kyc.dto.response;

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
                Instant updatedAt) {
}
