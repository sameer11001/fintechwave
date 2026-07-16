CREATE TABLE transaction_saga_states (
    transaction_id  UUID          NOT NULL PRIMARY KEY,
    current_step    VARCHAR(30)   NOT NULL,
    saga_type       VARCHAR(10)   NOT NULL,
    sender_id       UUID          NOT NULL,
    receiver_id     UUID,
    amount          NUMERIC(19,4) NOT NULL,
    fee_amount      NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency        VARCHAR(3)    NOT NULL,
    failure_reason  VARCHAR(500),
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_saga_step       ON transaction_saga_states (current_step);
CREATE INDEX idx_saga_sender     ON transaction_saga_states (sender_id);
CREATE INDEX idx_saga_type       ON transaction_saga_states (saga_type);
CREATE INDEX idx_saga_created_at ON transaction_saga_states (created_at);
