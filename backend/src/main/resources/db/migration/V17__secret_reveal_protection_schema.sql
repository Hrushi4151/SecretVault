-- ==============================================================================
-- SecretVault — V17 Production-Grade Secret Reveal Protection (Phase 5.8.5)
-- ==============================================================================

-- 1. Secret Reveal Policies (Fine-Grained Governance for Plaintext Secret Reveal Operations)
CREATE TABLE IF NOT EXISTS secret_reveal_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE', -- 'WORKSPACE', 'PROJECT', 'ENVIRONMENT', 'SECRET'
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    environment_id UUID REFERENCES environments(id) ON DELETE CASCADE,
    secret_id UUID REFERENCES secrets(id) ON DELETE CASCADE,
    policy_level VARCHAR(32) NOT NULL DEFAULT 'DEFAULT', -- 'DEFAULT', 'SENSITIVE', 'HIGHLY_SENSITIVE', 'PRODUCTION_CRITICAL'
    require_step_up BOOLEAN NOT NULL DEFAULT FALSE,
    allowed_step_up_factors VARCHAR(255) NOT NULL DEFAULT 'PASSWORD,TOTP,RECOVERY_CODE,WEBAUTHN',
    require_webauthn_only BOOLEAN NOT NULL DEFAULT FALSE,
    require_reason BOOLEAN NOT NULL DEFAULT FALSE,
    min_reason_length INTEGER NOT NULL DEFAULT 10,
    max_reason_length INTEGER NOT NULL DEFAULT 500,
    require_privileged_or_jit BOOLEAN NOT NULL DEFAULT FALSE,
    max_display_duration_seconds INTEGER NOT NULL DEFAULT 60,
    copy_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    clipboard_timeout_seconds INTEGER NOT NULL DEFAULT 15,
    bulk_reveal_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    max_bulk_count INTEGER NOT NULL DEFAULT 50,
    rate_limit_per_minute INTEGER NOT NULL DEFAULT 30,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_rev_policy_scope CHECK (
        (scope_type = 'WORKSPACE' AND project_id IS NULL AND environment_id IS NULL AND secret_id IS NULL) OR
        (scope_type = 'PROJECT' AND project_id IS NOT NULL AND environment_id IS NULL AND secret_id IS NULL) OR
        (scope_type = 'ENVIRONMENT' AND project_id IS NOT NULL AND environment_id IS NOT NULL AND secret_id IS NULL) OR
        (scope_type = 'SECRET' AND project_id IS NOT NULL AND environment_id IS NOT NULL AND secret_id IS NOT NULL)
    ),
    CONSTRAINT chk_rev_policy_reason_len CHECK (min_reason_length >= 1 AND max_reason_length <= 2000 AND min_reason_length <= max_reason_length),
    CONSTRAINT chk_rev_policy_display_duration CHECK (max_display_duration_seconds >= 5 AND max_display_duration_seconds <= 300),
    CONSTRAINT chk_rev_policy_clipboard_timeout CHECK (clipboard_timeout_seconds >= 1 AND clipboard_timeout_seconds <= 120),
    CONSTRAINT chk_rev_policy_bulk_count CHECK (max_bulk_count >= 1 AND max_bulk_count <= 200)
);

CREATE INDEX IF NOT EXISTS idx_rev_policy_ws ON secret_reveal_policies(workspace_id);
CREATE INDEX IF NOT EXISTS idx_rev_policy_scope ON secret_reveal_policies(workspace_id, scope_type, project_id, environment_id, secret_id);
