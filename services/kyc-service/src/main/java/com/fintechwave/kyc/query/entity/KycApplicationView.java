package com.fintechwave.kyc.query.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "kyc_applications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycApplicationView {

    @Id
    private UUID id;

    @Indexed(unique = true)
    private UUID userId;

    private String status;
    private String currentTier;
    private String requestedTier;
    private String rejectionReason;

    private Instant createdAt;
    private Instant updatedAt;
}
