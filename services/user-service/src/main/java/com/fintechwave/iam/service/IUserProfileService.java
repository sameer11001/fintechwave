package com.fintechwave.iam.service;

import com.fintechwave.iam.dto.request.KeycloakUserEventRequest;
import com.fintechwave.iam.dto.request.UpdateUserProfileRequest;
import com.fintechwave.iam.dto.response.UserProfileResponse;

import java.util.UUID;

public interface IUserProfileService {

    void createProfileFromKeycloak(KeycloakUserEventRequest request);

    UserProfileResponse updateProfile(UUID keycloakId, UpdateUserProfileRequest request);

    void updateKycTier(UUID keycloakId, String tier);

    /**
     * Called by transaction-service on first cash-in — stores Stripe customer
     * reference.
     */
    void updateStripeCustomerId(UUID keycloakId, String stripeCustomerId);
}
