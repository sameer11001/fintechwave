package com.fintechwave.kyc.dto.request;

import com.fintechwave.kyc.domain.enums.KycTier;
import jakarta.validation.constraints.NotNull;

public record SubmitKycRequest(
                @NotNull(message = "Requested tier is required") KycTier requestedTier) {
}
