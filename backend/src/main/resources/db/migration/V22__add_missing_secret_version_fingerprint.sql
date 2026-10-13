-- ==============================================================================
-- SecretVault Schema Migration V22: Ensure secret_versions fingerprint column
-- ==============================================================================

ALTER TABLE secret_versions ADD COLUMN IF NOT EXISTS fingerprint VARCHAR(128);
CREATE INDEX IF NOT EXISTS idx_secret_versions_fp ON secret_versions(fingerprint);
