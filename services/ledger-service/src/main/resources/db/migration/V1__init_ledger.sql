-- V1: Ledger Service schema — double-entry bookkeeping tables
-- See Master Plan §7 for full schema specification

CREATE TABLE ledger_account (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id     UUID,                                -- NULL for platform accounts
    account_type VARCHAR(20)  NOT NULL,               -- ASSET | LIABILITY | REVENUE | EXPENSE
    account_code VARCHAR(10)  NOT NULL,               -- e.g. '2000'
    currency     VARCHAR(3)   NOT NULL DEFAULT 'JOD',
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_account_owner_id ON ledger_account(owner_id);
CREATE INDEX idx_ledger_account_code     ON ledger_account(account_code);

CREATE TABLE ledger_entry (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   UUID         NOT NULL,
    account_id       UUID         NOT NULL REFERENCES ledger_account(id),
    entry_type       VARCHAR(6)   NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount           NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)   NOT NULL,
    idempotency_key  UUID         NOT NULL UNIQUE,
    description      TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_entry_transaction_id ON ledger_entry(transaction_id);
CREATE INDEX idx_ledger_entry_account_id     ON ledger_entry(account_id);

-- Balance can NEVER go negative — enforced at DB layer
CREATE TABLE balance (
    account_id  UUID          PRIMARY KEY REFERENCES ledger_account(id),
    amount      NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (amount >= 0),
    currency    VARCHAR(3)    NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Outbox for this service
CREATE TABLE outbox_events (
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

CREATE INDEX idx_outbox_published ON outbox_events(published, occurred_at);

-- Idempotency table for Kafka consumers
CREATE TABLE processed_events (
    idempotency_key UUID         PRIMARY KEY,
    processed_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
