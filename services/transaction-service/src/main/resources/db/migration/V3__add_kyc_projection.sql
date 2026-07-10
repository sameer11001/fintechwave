CREATE TABLE tx_kyc_projections (
    user_id     UUID PRIMARY KEY,
    current_tier VARCHAR(10)  NOT NULL DEFAULT 'TIER_0',
    status       VARCHAR(30)  NOT NULL DEFAULT 'PENDING_SUBMISSION',
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_kyc_tier   CHECK (current_tier IN ('TIER_0', 'TIER_1', 'TIER_2', 'TIER_3')),
    CONSTRAINT chk_kyc_status CHECK (status IN (
        'PENDING_SUBMISSION', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED'
    ))
);
