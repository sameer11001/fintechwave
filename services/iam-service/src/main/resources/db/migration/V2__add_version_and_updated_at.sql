-- Add optimistic locking version column
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Add updated_at for Spring Auditing (@LastModifiedDate)
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

-- Back-fill updated_at from created_at for existing rows
UPDATE users SET updated_at = created_at WHERE updated_at IS NULL;

-- Ensure email index exists (JPA @Index declaration mirrors this)
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
