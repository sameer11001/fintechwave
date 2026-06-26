package com.fintechwave.iam.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.iam.domain.entity.OutboxEvent;
import com.fintechwave.iam.domain.entity.UserProfile;
import com.fintechwave.iam.domain.enums.KycTier;
import com.fintechwave.iam.domain.enums.UserStatus;
import com.fintechwave.iam.dto.request.KeycloakUserEventRequest;
import com.fintechwave.iam.dto.request.UpdateUserProfileRequest;
import com.fintechwave.iam.dto.response.UserProfileResponse;
import com.fintechwave.iam.exception.KycNotFoundException;
import com.fintechwave.iam.exception.UserNotFoundException;
import com.fintechwave.iam.repository.OutboxEventRepository;
import com.fintechwave.iam.repository.UserProfileRepository;
import com.fintechwave.iam.service.IUserProfileService;
import com.fintechwave.iam.service.KeycloakAdminClient;
import com.fintechwave.iam.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import com.fintechwave.events.GenericDomainEvent;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserProfileServiceImpl implements IUserProfileService {

    private static final String TOPIC_USER_EVENTS = "iam.user-events";

    private final UserProfileRepository userProfileRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final KeycloakAdminClient keycloakAdminClient;

    @Override
    @Transactional
    public void createProfileFromKeycloak(KeycloakUserEventRequest request) {
        UUID keycloakId = UUID.fromString(request.userId());

        if (userProfileRepository.existsByKeycloakId(keycloakId)) {
            log.warn("UserProfile already exists for keycloakId={} — idempotent skip", keycloakId);
            return;
        }

        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            log.info("Email not in webhook payload — fetching from Keycloak Admin API for userId={}", request.userId());
            email = keycloakAdminClient.fetchEmailByUserId(request.userId());
        }

        UserProfile profile = UserProfile.builder()
                .keycloakId(keycloakId)
                .email(email)
                .status(UserStatus.ACTIVE)
                .kycTier(KycTier.TIER_0)
                .build();

        UserProfile saved = userProfileRepository.save(profile);
        outboxEventRepository.save(buildOutboxEvent(saved));
        log.info("UserProfile created: id={} keycloakId={} email={}", saved.getId(), keycloakId, email);
    }

    @Override
    public UserProfileResponse findByKeycloakId(UUID keycloakId) {
        UserProfile profile = userProfileRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException(keycloakId));
        return toResponse(profile);
    }

    @Override
    public UserProfileResponse findById(UUID userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return toResponse(profile);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UUID keycloakId, UpdateUserProfileRequest request) {
        UserProfile profile = userProfileRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException(keycloakId));

        if (request.firstName() != null)
            profile.setFirstName(request.firstName());
        if (request.lastName() != null)
            profile.setLastName(request.lastName());
        if (request.phone() != null)
            profile.setPhoneHash(HashUtil.sha256(request.phone().trim()));

        UserProfile updated = userProfileRepository.save(profile);

        // Sync with Keycloak synchronously
        keycloakAdminClient.updateUserProfile(
                keycloakId.toString(),
                request.firstName(),
                request.lastName(),
                request.phone()
        );

        return toResponse(updated);
    }

    @Override
    @Transactional
    public void updateKycTier(UUID keycloakId, String tier) {
        KycTier kycTier;
        try {
            kycTier = KycTier.valueOf(tier.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new KycNotFoundException(tier);
        }
        int rows = userProfileRepository.updateKycTier(keycloakId, kycTier);
        if (rows == 0)
            throw new UserNotFoundException(keycloakId);
        log.info("KYC tier updated: keycloakId={} tier={}", keycloakId, tier);
    }

    @Override
    @Transactional
    public void updateStripeCustomerId(UUID keycloakId, String stripeCustomerId) {
        int rows = userProfileRepository.updateStripeCustomerId(keycloakId, stripeCustomerId);
        if (rows == 0)
            throw new UserNotFoundException(keycloakId);
        log.info("Stripe customer linked: keycloakId={}", keycloakId);
    }

    private UserProfileResponse toResponse(UserProfile p) {
        return UserProfileResponse.builder()
                .id(p.getId())
                .keycloakId(p.getKeycloakId())
                .email(p.getEmail())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .phoneHash(p.getPhoneHash())
                .status(p.getStatus().name())
                .kycTier(p.getKycTier().name())
                .stripeLinked(p.getStripeCustomerId() != null)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private OutboxEvent buildOutboxEvent(UserProfile profile) {
        try {
            GenericDomainEvent domainEvent = new GenericDomainEvent(
                    "USER_REGISTERED",
                    1,
                    profile.getId(),
                    "USER",
                    Map.of(
                            "userId", profile.getId().toString(),
                            "keycloakId", profile.getKeycloakId().toString(),
                            "email", profile.getEmail(),
                            "kycTier", profile.getKycTier().name()));

            String payloadJson = objectMapper.writeValueAsString(domainEvent);

            return OutboxEvent.builder()
                    .aggregateId(domainEvent.getAggregateId())
                    .aggregateType(domainEvent.getAggregateType())
                    .eventType(domainEvent.getEventType())
                    .eventVersion(domainEvent.getEventVersion())
                    .idempotencyKey(domainEvent.getIdempotencyKey())
                    .topic(TOPIC_USER_EVENTS)
                    .payload(payloadJson)
                    .build();
        } catch (Exception e) {
            log.error("Failed to build USER_REGISTERED outbox event for userId={}", profile.getId(), e);
            throw new RuntimeException("Failed to serialize outbox event for user: " + profile.getId(), e);
        }
    }
}
