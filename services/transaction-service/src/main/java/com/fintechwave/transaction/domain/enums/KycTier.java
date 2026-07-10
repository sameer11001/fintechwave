package com.fintechwave.transaction.domain.enums;

public enum KycTier {
    TIER_0,
    TIER_1,
    TIER_2,
    TIER_3;

    public boolean isAtLeast(KycTier required) {
        return this.ordinal() >= required.ordinal();
    }
}
