CREATE TABLE notification (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key  UUID         NOT NULL UNIQUE,
    recipient_id     UUID         NOT NULL,
    channel          VARCHAR(10)  NOT NULL,   -- EMAIL | SMS | PUSH
    template_code    VARCHAR(50)  NOT NULL,   -- e.g. WALLET_PROVISIONED
    subject          TEXT,
    body             TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | SENT | FAILED
    failure_reason   TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ
);

CREATE INDEX idx_notification_recipient_id  ON notification(recipient_id);
CREATE INDEX idx_notification_idempotency   ON notification(idempotency_key);
CREATE INDEX idx_notification_created_at    ON notification(created_at DESC);
CREATE INDEX idx_notification_status        ON notification(status);

CREATE TABLE notif_processed_events (
    idempotency_key UUID PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
