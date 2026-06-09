CREATE TABLE transaction_summary (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID         NOT NULL UNIQUE,
    user_id         UUID         NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,    
    status          VARCHAR(20)  NOT NULL,    
    amount          NUMERIC(19,4) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    counterparty_id UUID,                   
    description     TEXT,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_tx_summary_user_id     ON transaction_summary(user_id, occurred_at DESC);
CREATE INDEX idx_tx_summary_tx_id       ON transaction_summary(transaction_id);

CREATE TABLE daily_volume (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_date     DATE         NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    total_count     BIGINT       NOT NULL DEFAULT 0,
    total_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency        VARCHAR(3)   NOT NULL DEFAULT 'USD',
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (report_date, transaction_type, currency)
);

CREATE INDEX idx_daily_volume_date ON daily_volume(report_date DESC);

CREATE TABLE balance_snapshot (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL,
    account_id      UUID         NOT NULL,
    balance         NUMERIC(19,4) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    snapshot_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_balance_snapshot_user_id ON balance_snapshot(user_id, snapshot_at DESC);

CREATE TABLE kyc_status_summary (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL UNIQUE,
    kyc_status      VARCHAR(20)  NOT NULL,   
    submitted_at    TIMESTAMPTZ,
    decided_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_kyc_status_summary_status ON kyc_status_summary(kyc_status);

CREATE TABLE failed_tx_rate (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_date     DATE         NOT NULL,
    total_count     BIGINT       NOT NULL DEFAULT 0,
    failed_count    BIGINT       NOT NULL DEFAULT 0,
    failure_rate    NUMERIC(5,4) NOT NULL DEFAULT 0,   -- 0.0000 to 1.0000
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (report_date)
);

CREATE INDEX idx_failed_tx_rate_date ON failed_tx_rate(report_date DESC);

CREATE TABLE report_processed_events (
    idempotency_key UUID PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
