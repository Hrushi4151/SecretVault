-- ==============================================================================
-- SecretVault — V7 Granular Access Control, JIT Temporary Access & Access Reviews (Phase 5)
-- ==============================================================================

-- 1. Granular Access Grants (Resource-Level Explicit Permissions)
CREATE TABLE IF NOT EXISTS access_grants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scope_type VARCHAR(32) NOT NULL, -- 'WORKSPACE', 'PROJECT', 'ENVIRONMENT', 'SECRET'
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    environment_id UUID REFERENCES environments(id) ON DELETE CASCADE,
    secret_id UUID REFERENCES secrets(id) ON DELETE CASCADE,
    permission VARCHAR(64) NOT NULL, -- e.g. 'secret.reveal', 'secret.update', 'environment.promote'
    granted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Scope Integrity Check Constraint
    CONSTRAINT chk_access_grant_scope CHECK (
        (scope_type = 'WORKSPACE' AND project_id IS NULL AND environment_id IS NULL AND secret_id IS NULL) OR
        (scope_type = 'PROJECT' AND project_id IS NOT NULL AND environment_id IS NULL AND secret_id IS NULL) OR
        (scope_type = 'ENVIRONMENT' AND project_id IS NOT NULL AND environment_id IS NOT NULL AND secret_id IS NULL) OR
        (scope_type = 'SECRET' AND project_id IS NOT NULL AND environment_id IS NOT NULL AND secret_id IS NOT NULL)
    ),
    CONSTRAINT uq_access_grant UNIQUE (workspace_id, user_id, scope_type, project_id, environment_id, secret_id, permission)
);

CREATE INDEX IF NOT EXISTS idx_access_grants_ws_user ON access_grants(workspace_id, user_id);
CREATE INDEX IF NOT EXISTS idx_access_grants_env_user ON access_grants(environment_id, user_id);
CREATE INDEX IF NOT EXISTS idx_access_grants_secret_user ON access_grants(secret_id, user_id);
CREATE INDEX IF NOT EXISTS idx_access_grants_permission ON access_grants(permission);

-- 2. Just-In-Time (JIT) Temporary Access Requests
CREATE TABLE IF NOT EXISTS jit_access_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    environment_id UUID NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    secret_id UUID REFERENCES secrets(id) ON DELETE CASCADE,
    requested_permission VARCHAR(64) NOT NULL, -- e.g. 'secret.reveal'
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes >= 5 AND duration_minutes <= 240),
    reason TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'REVOKED', 'CANCELLED'
    approver_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewer_notes TEXT,
    approved_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_jit_ws_user ON jit_access_requests(workspace_id, user_id);
CREATE INDEX IF NOT EXISTS idx_jit_env_status ON jit_access_requests(environment_id, status);
CREATE INDEX IF NOT EXISTS idx_jit_expires_at ON jit_access_requests(expires_at);
CREATE INDEX IF NOT EXISTS idx_jit_status ON jit_access_requests(status);

-- 3. Access Review Campaigns (Governance & Certification)
CREATE TABLE IF NOT EXISTS access_review_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE', -- 'WORKSPACE', 'PROJECT', 'ENVIRONMENT'
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    environment_id UUID REFERENCES environments(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'IN_PROGRESS', 'COMPLETED', 'EXPIRED'
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    due_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    total_items_count INTEGER NOT NULL DEFAULT 0,
    decided_items_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_campaigns_ws ON access_review_campaigns(workspace_id);
CREATE INDEX IF NOT EXISTS idx_campaigns_status ON access_review_campaigns(status);

-- 4. Access Review Items (Individual Certification Decisions)
CREATE TABLE IF NOT EXISTS access_review_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES access_review_campaigns(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_email VARCHAR(255) NOT NULL,
    user_full_name VARCHAR(255) NOT NULL,
    resource_type VARCHAR(32) NOT NULL, -- 'WORKSPACE', 'PROJECT', 'ENVIRONMENT', 'SECRET'
    resource_name VARCHAR(255) NOT NULL,
    resource_id UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL, -- 'WORKSPACE_ROLE', 'PROJECT_ACCESS', 'ENVIRONMENT_ACCESS', 'GRANULAR_GRANT', 'JIT_GRANT'
    source_reference_id UUID, -- Exact ID of the referenced grant or access record
    permission_summary VARCHAR(255) NOT NULL, -- e.g. 'WRITE (DEVELOPER)', 'secret.reveal'
    decision VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'KEEP', 'REVOKE'
    decision_reason TEXT,
    decided_by UUID REFERENCES users(id) ON DELETE SET NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_review_items_campaign ON access_review_items(campaign_id);
CREATE INDEX IF NOT EXISTS idx_review_items_decision ON access_review_items(campaign_id, decision);
CREATE INDEX IF NOT EXISTS idx_review_items_user ON access_review_items(user_id);
