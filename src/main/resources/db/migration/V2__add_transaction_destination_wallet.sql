ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS destination_wallet_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_transactions_destination_wallet_id
    ON transactions (destination_wallet_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_transactions_destination_wallet'
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT fk_transactions_destination_wallet
            FOREIGN KEY (destination_wallet_id) REFERENCES wallets (id);
    END IF;
END $$;
