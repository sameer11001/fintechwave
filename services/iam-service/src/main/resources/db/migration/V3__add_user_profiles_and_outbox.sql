CREATE TABLE IF NOT EXISTS user_profiles (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_id              UUID         NOT NULL UNIQUE,
    email                    VARCHAR(255) NOT NULL,
    first_name               VARCHAR(100),
    last_name                VARCHAR(100),
    phone_hash               VARCHAR(64),
    status                   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    kyc_tier                 VARCHAR(10)  NOT NULL DEFAULT 'TIER_0',
    stripe_customer_id       VARCHAR(255),
    stripe_payment_method_id VARCHAR(255),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ,
    version                  BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_keycloak_id ON user_profiles(keycloak_id);
CREATE INDEX IF NOT EXISTS idx_user_profiles_email       ON user_profiles(email);

-- Outbox pattern — domain events written here before Kafka publish
CREATE TABLE IF NOT EXISTS outbox_events (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id     UUID         NOT NULL,
    aggregate_type   VARCHAR(50)  NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    event_version    INT          NOT NULL DEFAULT 1,
    idempotency_key  UUID         NOT NULL UNIQUE,
    topic            VARCHAR(100) NOT NULL,
    payload          TEXT         NOT NULL,
    published        BOOLEAN      NOT NULL DEFAULT FALSE,
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_published ON outbox_events(published, occurred_at);
