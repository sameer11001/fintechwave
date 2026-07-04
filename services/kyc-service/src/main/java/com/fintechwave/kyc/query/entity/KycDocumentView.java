package com.fintechwave.kyc.query.entity;

import java.time.Instant;
import java.util.UUID;

public record KycDocumentView(
                UUID id,
                String documentType,
                String storageBucket,
                String storageKey,
                String contentType,
                Long fileSizeBytes,
                Instant uploadedAt) {
}
