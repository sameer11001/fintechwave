package com.fintechwave.iam.query.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "user_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileView {

    @Id
    private UUID id;

    @Indexed(unique = true)
    private UUID keycloakId;

    @Indexed
    private String email;

    private String firstName;
    private String lastName;
    private String status;
    private String kycTier;

    // Derived or aggregated fields can be added here
    private String riskTier;

    private Instant createdAt;
    private Instant updatedAt;
}
