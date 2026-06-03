package com.fintechwave.iam.service;

import com.fintechwave.iam.dto.request.KeycloakUserEventRequest;
import com.fintechwave.iam.dto.request.UpdateUserProfileRequest;
import com.fintechwave.iam.dto.response.UserProfileResponse;

import java.util.UUID;

public interface IUserProfileService {

    /** Called by the Keycloak webhook — creates the profile and publishes UserRegistered to Outbox. */
    void createProfileFromKeycloak(KeycloakUserEventRequest request);

    UserProfileResponse findByKeycloakId(UUID keycloakId);

    UserProfileResponse findById(UUID userId);

    UserProfileResponse updateProfile(UUID keycloakId, UpdateUserProfileRequest request);

    /** Called by kyc-service after KYCVerified — upgrades the user's KYC tier. */
    void updateKycTier(UUID userId, String tier);

    /** Called by transaction-service on first cash-in — stores Stripe customer reference. */
    void updateStripeCustomerId(UUID userId, String stripeCustomerId);
}
