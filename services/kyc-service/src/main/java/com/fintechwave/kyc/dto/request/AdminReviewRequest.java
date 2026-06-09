package com.fintechwave.kyc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminReviewRequest(
                /** "APPROVED" or "REJECTED" */
                @NotBlank(message = "Decision is required") String decision,

                @Size(max = 1000) String rejectionReason) {
}
