package com.fintechwave.kyc.dto.response;

import com.fintechwave.kyc.domain.entity.KycDocument;
import com.fintechwave.kyc.domain.enums.DocumentType;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record KycDocumentResponse(
        UUID id,
        DocumentType documentType,
        String contentType,
        Long fileSizeBytes,
        Instant uploadedAt,
        String downloadUrl) {
    public static KycDocumentResponse from(KycDocument doc, String downloadUrl) {
        return KycDocumentResponse.builder()
                .id(doc.getId())
                .documentType(doc.getDocumentType())
                .contentType(doc.getContentType())
                .fileSizeBytes(doc.getFileSizeBytes())
                .uploadedAt(doc.getUploadedAt())
                .downloadUrl(downloadUrl)
                .build();
    }

    public static KycDocumentResponse from(KycDocument doc) {
        return from(doc, null);
    }
}
