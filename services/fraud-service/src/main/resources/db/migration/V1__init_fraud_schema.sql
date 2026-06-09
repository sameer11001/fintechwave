CREATE TABLE fraud_rule (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_code   VARCHAR(50)  NOT NULL UNIQUE,
    description TEXT         NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    threshold   NUMERIC(19,4),
    window_sec  INTEGER,
    action      VARCHAR(20)  NOT NULL DEFAULT 'FLAG',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_fraud_rule_enabled ON fraud_rule(enabled);

CREATE TABLE fraud_decision (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    decision        VARCHAR(20)  NOT NULL,  
    risk_score      INTEGER      NOT NULL DEFAULT 0,
    triggered_rules TEXT[],                 
    amount          NUMERIC(19,4) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    decided_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_fraud_decision_transaction_id ON fraud_decision(transaction_id);
CREATE INDEX idx_fraud_decision_user_id        ON fraud_decision(user_id);
CREATE INDEX idx_fraud_decision_decided_at     ON fraud_decision(decided_at DESC);

CREATE TABLE fraud_processed_events (
    idempotency_key UUID PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fraud_outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID         NOT NULL,
    aggregate_type  VARCHAR(50)  NOT NULL DEFAULT 'TRANSACTION',
    event_type      VARCHAR(80)  NOT NULL,
    payload         TEXT         NOT NULL,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_fraud_outbox_unpublished ON fraud_outbox_events(published, occurred_at) WHERE published = FALSE;

INSERT INTO fraud_rule (rule_code, description, enabled, threshold, window_sec, action) VALUES
('VELOCITY_TX_COUNT_60S',   'Max 10 transactions per 60 seconds',      TRUE, 10,      60,    'FLAG'),
('VELOCITY_TX_VOLUME_1H',   'Max $500 transaction volume per hour',     TRUE, 500.00,  3600,  'FLAG'),
('VELOCITY_TX_VOLUME_24H',  'Max $2000 transaction volume per 24 hours',TRUE, 2000.00, 86400, 'FLAG'),
('VELOCITY_AUTH_FAIL_15M',  'Max 5 failed auth attempts per 15 minutes',TRUE, 5,       900,   'BLOCK');
