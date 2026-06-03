package com.fintechwave.iam.domain.enums;

public enum KycTier {
    /** Email verified (Keycloak) — no wallet. */
    TIER_0,
    /** National ID photo submitted and approved — basic wallet, low limits. */
    TIER_1,
    /** Facial biometric approved — standard wallet. */
    TIER_2,
    /** Enhanced due diligence approved — high-value wallet. */
    TIER_3
}
