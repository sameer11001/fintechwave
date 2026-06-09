package com.fintechwave.iam.domain.enums;

public enum UserStatus {

    ACTIVE,
    INACTIVE,
    SUSPENDED,
    PENDING_VERIFICATION;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
