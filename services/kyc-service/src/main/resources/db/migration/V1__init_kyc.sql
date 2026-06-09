CREATE TABLE kyc_applications (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL UNIQUE,
    status           VARCHAR(30) NOT NULL DEFAULT 'PENDING_SUBMISSION',
    current_tier     VARCHAR(10) NOT NULL DEFAULT 'TIER_0',
    requested_tier   VARCHAR(10) NOT NULL DEFAULT 'TIER_1',
    rejection_reason VARCHAR(1000),
    reviewed_by      UUID,
    reviewed_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_kyc_status CHECK (
        status IN ('PENDING_SUBMISSION', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED', 'SUSPENDED')
    ),
    CONSTRAINT chk_kyc_tier CHECK (
        current_tier IN ('TIER_0', 'TIER_1', 'TIER_2', 'TIER_3')
    ),
    CONSTRAINT chk_kyc_requested_tier CHECK (
        requested_tier IN ('TIER_0', 'TIER_1', 'TIER_2', 'TIER_3')
    )
);

CREATE INDEX idx_kyc_user_id  ON kyc_applications(user_id);
CREATE INDEX idx_kyc_status   ON kyc_applications(status);

CREATE TABLE kyc_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID NOT NULL REFERENCES kyc_applications(id),
    document_type   VARCHAR(30) NOT NULL,
    storage_bucket  VARCHAR(100) NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,
    content_type    VARCHAR(100),
    file_size_bytes BIGINT,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_document_type CHECK (
        document_type IN (
            'NATIONAL_ID', 'PASSPORT', 'DRIVERS_LICENSE',
            'SELFIE', 'PROOF_OF_ADDRESS', 'SOURCE_OF_FUNDS'
        )
    )
);

CREATE INDEX idx_kyc_doc_application_id ON kyc_documents(application_id);
CREATE INDEX idx_kyc_doc_type           ON kyc_documents(document_type);

CREATE TABLE processed_events (
    idempotency_key UUID PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE kyc_outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL,
    aggregate_type  VARCHAR(50) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    event_version   INTEGER NOT NULL DEFAULT 1,
    payload         TEXT NOT NULL,
    idempotency_key UUID NOT NULL UNIQUE,
    published       BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_kyc_outbox_published   ON kyc_outbox_events(published);
CREATE INDEX idx_kyc_outbox_occurred_at ON kyc_outbox_events(occurred_at);
