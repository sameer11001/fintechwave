-- FintechWave Transaction Service — Initial Schema
-- V1: transactions, outbox events, idempotency guard

-- ─── Transactions ─────────────────────────────────────────────────────────────
CREATE TABLE transactions (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_type          VARCHAR(20) NOT NULL,
    status                    VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    sender_id                 UUID NOT NULL,
    receiver_id               UUID,                          -- NULL for CASH_IN / CASH_OUT
    amount                    NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency                  VARCHAR(3) NOT NULL DEFAULT 'USD',
    fee_amount                NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    stripe_payment_intent_id  VARCHAR(255),
    stripe_payout_id          VARCHAR(255),
    idempotency_key           UUID NOT NULL UNIQUE,
    description               VARCHAR(500),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                   BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_tx_type CHECK (
        transaction_type IN ('P2P', 'CASH_IN', 'CASH_OUT', 'BILL_PAY')
    ),
    CONSTRAINT chk_tx_status CHECK (
        status IN (
            'INITIATED', 'FRAUD_CHECK', 'RESERVED', 'COMMITTED',
            'COMPLETED', 'FLAGGED', 'FAILED', 'REVERSED'
        )
    )
);

CREATE INDEX idx_tx_sender_id        ON transactions(sender_id);
CREATE INDEX idx_tx_receiver_id      ON transactions(receiver_id);
CREATE INDEX idx_tx_status           ON transactions(status);
CREATE INDEX idx_tx_type             ON transactions(transaction_type);
CREATE INDEX idx_tx_idempotency_key  ON transactions(idempotency_key);

-- ─── Outbox Events ────────────────────────────────────────────────────────────
CREATE TABLE tx_outbox_events (
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

CREATE INDEX idx_tx_outbox_published   ON tx_outbox_events(published);
CREATE INDEX idx_tx_outbox_occurred_at ON tx_outbox_events(occurred_at);

-- ─── Idempotency Guard ────────────────────────────────────────────────────────
CREATE TABLE tx_processed_events (
    idempotency_key UUID PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
