-- ==============================================================================
-- SecretVault — V12 User Sessions & Session Security Schema (Phase 5.8.1)
-- ==============================================================================

-- 1. User Sessions Table
CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_identifier VARCHAR(64) NOT NULL UNIQUE,
    auth_method VARCHAR(64) NOT NULL DEFAULT 'PASSWORD',
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    device_name VARCHAR(255),
    browser VARCHAR(64),
    operating_system VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_active ON user_sessions(user_id, revoked_at, expires_at);
CREATE INDEX IF NOT EXISTS idx_user_sessions_identifier ON user_sessions(session_identifier);
CREATE INDEX IF NOT EXISTS idx_user_sessions_expires_at ON user_sessions(expires_at);

-- 2. Link Refresh Tokens to User Sessions
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS session_id UUID REFERENCES user_sessions(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_session_id ON refresh_tokens(session_id);
