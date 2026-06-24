ALTER TABLE transactions
    DROP CONSTRAINT chk_tx_status;

ALTER TABLE transactions
    ADD CONSTRAINT chk_tx_status CHECK (
        status IN (
            'INITIATED', 'FRAUD_CHECK', 'RESERVED', 'COMMITTED',
            'COMPLETED', 'FLAGGED', 'FAILED', 'REVERSED',
            'PENDING_LEDGER', 'REFUNDED'
        )
    );
