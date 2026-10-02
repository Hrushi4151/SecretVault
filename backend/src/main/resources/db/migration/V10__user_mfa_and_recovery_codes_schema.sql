-- ==============================================================================
-- SecretVault — V10 User MFA & Recovery Codes Persistence Schema (Phase 5.7.2)
-- ==============================================================================

-- 1. User MFA Configuration Table
CREATE TABLE IF NOT EXISTS user_mfa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    ciphertext BYTEA NOT NULL,
    encrypted_dek BYTEA NOT NULL,
    iv BYTEA NOT NULL,
    auth_tag BYTEA NOT NULL,
    key_reference VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    enrolled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_mfa_failed_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT chk_user_mfa_status CHECK (status IN ('PENDING_VERIFICATION', 'ENABLED', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_user_mfa_user_id ON user_mfa(user_id);
CREATE INDEX IF NOT EXISTS idx_user_mfa_status ON user_mfa(status);

-- 2. MFA Recovery Codes Table (One-Way Hashed Storage Only)
CREATE TABLE IF NOT EXISTS mfa_recovery_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_mfa_id UUID NOT NULL REFERENCES user_mfa(id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    code_index INTEGER NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mfa_recovery_code_index UNIQUE (user_mfa_id, code_index),
    CONSTRAINT chk_mfa_recovery_code_index CHECK (code_index >= 0),
    CONSTRAINT chk_mfa_recovery_code_used CHECK ((used = FALSE AND used_at IS NULL) OR (used = TRUE AND used_at IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_mfa_recovery_codes_user_mfa_id ON mfa_recovery_codes(user_mfa_id);
CREATE INDEX IF NOT EXISTS idx_mfa_recovery_codes_unused ON mfa_recovery_codes(user_mfa_id, used);
