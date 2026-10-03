-- ==============================================================================
-- SecretVault — V15 Privileged Access Security: Break-Glass, Dual Approval, Temporary Elevation (Phase 5.8.4)
-- ==============================================================================

-- 1. Privileged Access Policies (Governance Rules for Sensitive Operations)
CREATE TABLE IF NOT EXISTS privileged_access_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE', -- 'WORKSPACE', 'PROJECT', 'ENVIRONMENT', 'SECRET'
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    environment_id UUID REFERENCES environments(id) ON DELETE CASCADE,
    secret_id UUID REFERENCES secrets(id) ON DELETE CASCADE,
    action VARCHAR(64), -- Null for scope-wide default, or specific PrivilegedAction (e.g. 'SECRET_REVEAL')
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    require_step_up BOOLEAN NOT NULL DEFAULT TRUE,
    allowed_step_up_factors VARCHAR(255) NOT NULL DEFAULT 'PASSWORD,TOTP,RECOVERY_CODE,WEBAUTHN',
    require_approval BOOLEAN NOT NULL DEFAULT TRUE,
    approval_quorum INTEGER NOT NULL DEFAULT 1,
    prevent_self_approval BOOLEAN NOT NULL DEFAULT TRUE,
    require_justification BOOLEAN NOT NULL DEFAULT TRUE,
    max_duration_minutes INTEGER NOT NULL DEFAULT 60,
    production_protected BOOLEAN NOT NULL DEFAULT FALSE,
    break_glass_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    break_glass_requires_reason BOOLEAN NOT NULL DEFAULT TRUE,
    break_glass_requires_audit BOOLEAN NOT NULL DEFAULT TRUE,
    emergency_duration_limit_minutes INTEGER NOT NULL DEFAULT 30,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_priv_policy_scope CHECK (
        (scope_type = 'WORKSPACE' AND project_id IS NULL AND environment_id IS NULL AND secret_id IS NULL) OR
        (scope_type = 'PROJECT' AND project_id IS NOT NULL AND environment_id IS NULL AND secret_id IS NULL) OR
        (scope_type = 'ENVIRONMENT' AND project_id IS NOT NULL AND environment_id IS NOT NULL AND secret_id IS NULL) OR
        (scope_type = 'SECRET' AND project_id IS NOT NULL AND environment_id IS NOT NULL AND secret_id IS NOT NULL)
    ),
    CONSTRAINT chk_priv_policy_quorum CHECK (approval_quorum >= 1 AND approval_quorum <= 10),
    CONSTRAINT chk_priv_policy_duration CHECK (max_duration_minutes >= 5 AND max_duration_minutes <= 1440),
    CONSTRAINT chk_priv_policy_emergency_duration CHECK (emergency_duration_limit_minutes >= 5 AND emergency_duration_limit_minutes <= 120)
);

CREATE INDEX IF NOT EXISTS idx_priv_policy_ws ON privileged_access_policies(workspace_id);
CREATE INDEX IF NOT EXISTS idx_priv_policy_scope ON privileged_access_policies(workspace_id, scope_type, project_id, environment_id, secret_id);
CREATE INDEX IF NOT EXISTS idx_priv_policy_action ON privileged_access_policies(workspace_id, action);

-- 2. Privileged Access Requests (State-Tracked Elevation Requests)
CREATE TABLE IF NOT EXISTS privileged_access_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    requester_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action VARCHAR(64) NOT NULL, -- e.g. 'SECRET_REVEAL', 'SECRET_DELETE', 'ROLE_CHANGE', 'BREAK_GLASS_REQUEST'
    scope_type VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE', -- 'WORKSPACE', 'PROJECT', 'ENVIRONMENT', 'SECRET'
    project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    environment_id UUID REFERENCES environments(id) ON DELETE SET NULL,
    secret_id UUID REFERENCES secrets(id) ON DELETE SET NULL,
    requested_permissions VARCHAR(255), -- e.g. 'secret.reveal', 'secret.delete'
    justification TEXT NOT NULL,
    duration_minutes INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'EXECUTED', 'REVOKED'
    is_break_glass BOOLEAN NOT NULL DEFAULT FALSE,
    required_quorum INTEGER NOT NULL DEFAULT 1,
    current_approvals_count INTEGER NOT NULL DEFAULT 0,
    step_up_proof VARCHAR(255),
    step_up_factor VARCHAR(64),
    approved_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    executed_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by UUID REFERENCES users(id) ON DELETE SET NULL,
    revocation_reason TEXT,
    rejection_reason TEXT,
    cancellation_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_priv_req_ws_user ON privileged_access_requests(workspace_id, requester_id);
CREATE INDEX IF NOT EXISTS idx_priv_req_target ON privileged_access_requests(workspace_id, target_user_id);
CREATE INDEX IF NOT EXISTS idx_priv_req_status ON privileged_access_requests(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_priv_req_expires ON privileged_access_requests(expires_at);
CREATE INDEX IF NOT EXISTS idx_priv_req_action ON privileged_access_requests(workspace_id, action);

-- 3. Privileged Access Approvals (Dual-Approval / Quorum Audit Ledger)
CREATE TABLE IF NOT EXISTS privileged_access_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES privileged_access_requests(id) ON DELETE CASCADE,
    approver_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    decision VARCHAR(32) NOT NULL, -- 'APPROVED', 'REJECTED'
    notes TEXT,
    step_up_factor VARCHAR(64),
    step_up_proof_token VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_priv_approval_request_approver UNIQUE (request_id, approver_id)
);

CREATE INDEX IF NOT EXISTS idx_priv_appr_request ON privileged_access_approvals(request_id);
CREATE INDEX IF NOT EXISTS idx_priv_appr_approver ON privileged_access_approvals(approver_id);

-- 4. Privileged Access Temporary Elevations (Active Authorized Capabilities)
CREATE TABLE IF NOT EXISTS privileged_access_elevations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    request_id UUID REFERENCES privileged_access_requests(id) ON DELETE SET NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action VARCHAR(64) NOT NULL,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE',
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    environment_id UUID REFERENCES environments(id) ON DELETE CASCADE,
    secret_id UUID REFERENCES secrets(id) ON DELETE CASCADE,
    granted_permission VARCHAR(64),
    is_break_glass BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'EXPIRED', 'REVOKED'
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by UUID REFERENCES users(id) ON DELETE SET NULL,
    revocation_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_priv_elev_ws_user ON privileged_access_elevations(workspace_id, user_id, status);
CREATE INDEX IF NOT EXISTS idx_priv_elev_expires ON privileged_access_elevations(expires_at);
CREATE INDEX IF NOT EXISTS idx_priv_elev_env ON privileged_access_elevations(environment_id, user_id, status);
CREATE INDEX IF NOT EXISTS idx_priv_elev_secret ON privileged_access_elevations(secret_id, user_id, status);
