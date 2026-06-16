package com.fintechwave.iam.repository;

import com.fintechwave.iam.domain.entity.UserProfile;
import com.fintechwave.iam.domain.enums.KycTier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByKeycloakId(UUID keycloakId);

    boolean existsByKeycloakId(UUID keycloakId);

    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE UserProfile u SET u.kycTier = :tier WHERE u.keycloakId = :userId")
    int updateKycTier(@Param("userId") UUID userId, @Param("tier") KycTier tier);

    @Modifying
    @Query("UPDATE UserProfile u SET u.stripeCustomerId = :stripeCustomerId WHERE u.keycloakId = :userId")
    int updateStripeCustomerId(@Param("userId") UUID userId, @Param("stripeCustomerId") String stripeCustomerId);
}
