-- ==============================================================================
-- SecretVault — V14 WebAuthn Credentials & Passkeys Schema (Phase 5.8.3)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS user_webauthn_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    credential_id VARCHAR(500) NOT NULL UNIQUE,
    public_key_cose BYTEA NOT NULL,
    sign_count BIGINT NOT NULL DEFAULT 0,
    aaguid VARCHAR(64),
    attestation_format VARCHAR(64) DEFAULT 'none',
    transports VARCHAR(255),
    user_verified_capable BOOLEAN NOT NULL DEFAULT TRUE,
    backup_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    backup_state BOOLEAN NOT NULL DEFAULT FALSE,
    discoverable BOOLEAN NOT NULL DEFAULT TRUE,
    friendly_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE,
    last_used_ip VARCHAR(64),
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_webauthn_user_id ON user_webauthn_credentials(user_id);
CREATE INDEX IF NOT EXISTS idx_webauthn_credential_id ON user_webauthn_credentials(credential_id);
CREATE INDEX IF NOT EXISTS idx_webauthn_active ON user_webauthn_credentials(user_id, revoked_at);
