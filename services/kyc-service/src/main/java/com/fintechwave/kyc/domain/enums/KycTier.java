package com.fintechwave.kyc.domain.enums;

/**
 * KYC Tiers — compliance gate for wallet provisioning.
 * No wallet is created below TIER_1.
 *
 * Tier 0 → Email verified (Keycloak) — no wallet
 * Tier 1 → National ID photo — basic wallet, low limits
 * Tier 2 → Facial biometric — standard wallet
 * Tier 3 → Enhanced due diligence — high-value wallet
 */
public enum KycTier {
    TIER_0,
    TIER_1,
    TIER_2,
    TIER_3
}
