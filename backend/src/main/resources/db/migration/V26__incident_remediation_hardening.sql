-- ==============================================================================
-- SecretVault — V26 Security Incident Remediation Hardening (Phase 13)
-- Two-Person Governance, Outbox Execution, and Concurrency Fencing
-- ==============================================================================

-- 1. Optimistic Locking on Security Incidents to prevent lost updates
ALTER TABLE security_incidents
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 2. Two-Person Approvals for High-Impact Incident Remediation
CREATE TABLE IF NOT EXISTS security_incident_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES security_incidents(id) ON DELETE CASCADE,
    action VARCHAR(64) NOT NULL,
    target_secret_id UUID,
    target_machine_identity_id UUID,
    notes TEXT,
    action_payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'EXECUTED'
    requested_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    decided_by UUID REFERENCES users(id) ON DELETE SET NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    decision_reason TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_inc_appr_ws_status ON security_incident_approvals(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_inc_appr_inc ON security_incident_approvals(incident_id);

-- 3. Durable Outbox Remediation Executions (Asynchronous, Bounded Retries, Partial Failure Tracking)
CREATE TABLE IF NOT EXISTS security_incident_remediations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES security_incidents(id) ON DELETE CASCADE,
    approval_id UUID REFERENCES security_incident_approvals(id) ON DELETE SET NULL,
    action VARCHAR(64) NOT NULL,
    target_secret_id UUID,
    target_machine_identity_id UUID,
    notes TEXT,
    actor_id UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED', -- 'QUEUED', 'CLAIMED', 'COMPLETED', 'PARTIAL_FAILURE', 'FAILED'
    worker_id VARCHAR(128),
    claim_token VARCHAR(128),
    locked_at TIMESTAMP WITH TIME ZONE,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    last_error TEXT,
    execution_details TEXT,
    requested_effects_json TEXT,
    verified_effects_json TEXT,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_inc_rem_ws_status ON security_incident_remediations(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_inc_rem_inc ON security_incident_remediations(incident_id);
CREATE INDEX IF NOT EXISTS idx_inc_rem_claim ON security_incident_remediations(status, locked_at);
