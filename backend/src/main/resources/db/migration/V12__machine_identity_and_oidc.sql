-- ==============================================================================
-- SecretVault — V12 Machine Identities, OIDC Providers, Trust Policies & Workload Auth (Phase 9)
-- ==============================================================================

-- 1. Machine Identities (First-Class Non-Human Workload Actors)
CREATE TABLE IF NOT EXISTS machine_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    type VARCHAR(32) NOT NULL DEFAULT 'CI_CD', -- 'SERVICE_ACCOUNT', 'CI_CD', 'WORKLOAD', 'AUTOMATION'
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'DISABLED', 'EXPIRED', 'REVOKED'
    expires_at TIMESTAMP WITH TIME ZONE,
    last_authenticated_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    disabled_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_machine_type CHECK (type IN ('SERVICE_ACCOUNT', 'CI_CD', 'WORKLOAD', 'AUTOMATION')),
    CONSTRAINT chk_machine_status CHECK (status IN ('ACTIVE', 'DISABLED', 'EXPIRED', 'REVOKED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_machine_identity_ws_name_active 
ON machine_identities(workspace_id, LOWER(name)) 
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_machine_identities_ws_status ON machine_identities(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_machine_identities_expires ON machine_identities(expires_at);
CREATE INDEX IF NOT EXISTS idx_machine_identities_type ON machine_identities(type);

-- 2. OIDC Providers (Configured External Token Issuers)
CREATE TABLE IF NOT EXISTS oidc_providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    issuer VARCHAR(512) NOT NULL,
    discovery_url VARCHAR(512),
    jwks_url VARCHAR(512),
    audience VARCHAR(256) NOT NULL,
    provider_type VARCHAR(32) NOT NULL DEFAULT 'GENERIC', -- 'GITHUB_ACTIONS', 'GITLAB_CI', 'GENERIC'
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'DISABLED'
    allowed_algorithms VARCHAR(256) NOT NULL DEFAULT 'RS256,ES256',
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    last_jwks_refresh_at TIMESTAMP WITH TIME ZONE,
    last_authenticated_at TIMESTAMP WITH TIME ZONE,
    success_count BIGINT NOT NULL DEFAULT 0,
    failure_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_oidc_provider_type CHECK (provider_type IN ('GITHUB_ACTIONS', 'GITLAB_CI', 'GENERIC')),
    CONSTRAINT chk_oidc_provider_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_oidc_provider_ws_name 
ON oidc_providers(workspace_id, LOWER(name));

CREATE UNIQUE INDEX IF NOT EXISTS uq_oidc_provider_ws_issuer 
ON oidc_providers(workspace_id, LOWER(issuer));

CREATE INDEX IF NOT EXISTS idx_oidc_providers_ws ON oidc_providers(workspace_id);
CREATE INDEX IF NOT EXISTS idx_oidc_providers_status ON oidc_providers(status);

-- 3. OIDC Trust Policies (Binding OIDC Claims to Machine Identities)
CREATE TABLE IF NOT EXISTS oidc_trust_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    machine_identity_id UUID NOT NULL REFERENCES machine_identities(id) ON DELETE CASCADE,
    oidc_provider_id UUID NOT NULL REFERENCES oidc_providers(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 0,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trust_policies_machine ON oidc_trust_policies(machine_identity_id);
CREATE INDEX IF NOT EXISTS idx_trust_policies_provider ON oidc_trust_policies(oidc_provider_id);
CREATE INDEX IF NOT EXISTS idx_trust_policies_ws ON oidc_trust_policies(workspace_id);

-- 4. OIDC Claim Rules (Atomic Claim Predicates for a Trust Policy)
CREATE TABLE IF NOT EXISTS oidc_claim_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trust_policy_id UUID NOT NULL REFERENCES oidc_trust_policies(id) ON DELETE CASCADE,
    claim_name VARCHAR(128) NOT NULL,
    operator VARCHAR(32) NOT NULL DEFAULT 'EQUALS', -- 'EQUALS', 'NOT_EQUALS', 'IN', 'NOT_IN', 'PREFIX', 'SUFFIX', 'CONTAINS', 'REGEX'
    expected_value TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_claim_rule_operator CHECK (operator IN ('EQUALS', 'NOT_EQUALS', 'IN', 'NOT_IN', 'PREFIX', 'SUFFIX', 'CONTAINS', 'REGEX'))
);

CREATE INDEX IF NOT EXISTS idx_claim_rules_policy ON oidc_claim_rules(trust_policy_id);
CREATE INDEX IF NOT EXISTS idx_claim_rules_name ON oidc_claim_rules(claim_name);

-- 5. Machine Access Grants (Granular Resource Permissions for Machine Identities)
CREATE TABLE IF NOT EXISTS machine_access_grants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    machine_identity_id UUID NOT NULL REFERENCES machine_identities(id) ON DELETE CASCADE,
    scope_type VARCHAR(32) NOT NULL, -- 'WORKSPACE', 'PROJECT', 'ENVIRONMENT', 'SECRET'
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    environment_id UUID REFERENCES environments(id) ON DELETE CASCADE,
    secret_id UUID REFERENCES secrets(id) ON DELETE CASCADE,
    secret_pattern VARCHAR(256), -- Optional explicit secret key name or pattern (e.g. 'DB_PASSWORD')
    permission VARCHAR(64) NOT NULL, -- e.g. 'secret.read', 'secret.reveal', 'provider.sync'
    effect VARCHAR(16) NOT NULL DEFAULT 'ALLOW', -- 'ALLOW', 'DENY'
    granted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_machine_grant_scope CHECK (
        (scope_type = 'WORKSPACE' AND project_id IS NULL AND environment_id IS NULL AND secret_id IS NULL) OR
        (scope_type = 'PROJECT' AND project_id IS NOT NULL AND environment_id IS NULL AND secret_id IS NULL) OR
        (scope_type = 'ENVIRONMENT' AND project_id IS NOT NULL AND environment_id IS NOT NULL AND secret_id IS NULL) OR
        (scope_type = 'SECRET' AND project_id IS NOT NULL AND environment_id IS NOT NULL AND (secret_id IS NOT NULL OR secret_pattern IS NOT NULL))
    ),
    CONSTRAINT chk_machine_grant_effect CHECK (effect IN ('ALLOW', 'DENY'))
);

CREATE INDEX IF NOT EXISTS idx_machine_grants_ws_machine ON machine_access_grants(workspace_id, machine_identity_id);
CREATE INDEX IF NOT EXISTS idx_machine_grants_env_machine ON machine_access_grants(environment_id, machine_identity_id);
CREATE INDEX IF NOT EXISTS idx_machine_grants_secret_machine ON machine_access_grants(secret_id, machine_identity_id);
CREATE INDEX IF NOT EXISTS idx_machine_grants_perm ON machine_access_grants(permission);

-- 6. Machine Sessions (Short-Lived, Hashed Workload Access Tokens)
CREATE TABLE IF NOT EXISTS machine_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    machine_identity_id UUID NOT NULL REFERENCES machine_identities(id) ON DELETE CASCADE,
    oidc_provider_id UUID REFERENCES oidc_providers(id) ON DELETE SET NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    token_prefix VARCHAR(32) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    source_ip VARCHAR(64),
    user_agent VARCHAR(256),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_machine_sessions_hash ON machine_sessions(token_hash);
CREATE INDEX IF NOT EXISTS idx_machine_sessions_machine ON machine_sessions(machine_identity_id);
CREATE INDEX IF NOT EXISTS idx_machine_sessions_expires ON machine_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_machine_sessions_ws ON machine_sessions(workspace_id);
