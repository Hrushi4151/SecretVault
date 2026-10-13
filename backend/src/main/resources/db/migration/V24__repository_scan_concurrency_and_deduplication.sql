-- ==============================================================================
-- SecretVault — V24 Repository Scan Concurrency and Deduplication (Phase 13)
-- ==============================================================================

-- 1. Partial unique index to guarantee atomic delivery deduplication per repository
CREATE UNIQUE INDEX IF NOT EXISTS uq_repo_scans_repo_idempotency
    ON repository_scans(repository_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 2. Concurrency fencing columns for atomic claiming and stale worker recovery
ALTER TABLE repository_scans ADD COLUMN IF NOT EXISTS claim_token VARCHAR(128);
ALTER TABLE repository_scans ADD COLUMN IF NOT EXISTS locked_by VARCHAR(128);
ALTER TABLE repository_scans ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE repository_scans ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_repo_scans_claim_token ON repository_scans(claim_token);
CREATE INDEX IF NOT EXISTS idx_repo_scans_locked_at ON repository_scans(locked_at);
