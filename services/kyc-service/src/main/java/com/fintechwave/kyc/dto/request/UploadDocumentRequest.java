package com.fintechwave.kyc.dto.request;

import com.fintechwave.kyc.domain.enums.DocumentType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UploadDocumentRequest(
                @NotNull DocumentType documentType,
                @NotNull UUID mediaId) {
}
