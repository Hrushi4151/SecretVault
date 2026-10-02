-- ==============================================================================
-- SecretVault — V8 Security Intelligence & Security Center Schema (Phase 6)
-- ==============================================================================

-- 1. Security Events (Structured, Sanitized Audit & Telemetry Stream)
CREATE TABLE IF NOT EXISTS security_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    environment_id UUID REFERENCES environments(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL DEFAULT 'INFO', -- 'INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    outcome VARCHAR(32) NOT NULL DEFAULT 'SUCCESS', -- 'SUCCESS', 'FAILURE', 'DENIED'
    source VARCHAR(64) NOT NULL DEFAULT 'SYSTEM', -- 'SYSTEM', 'API', 'AUTH', 'AUDIT_SYNC', 'ANALYSIS'
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    request_id VARCHAR(128),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata_json TEXT -- strictly sanitized allowlisted key-values, no plaintexts/tokens
);

CREATE INDEX IF NOT EXISTS idx_sec_events_ws_time ON security_events(workspace_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_sec_events_ws_type ON security_events(workspace_id, event_type);
CREATE INDEX IF NOT EXISTS idx_sec_events_ws_actor ON security_events(workspace_id, actor_user_id);
CREATE INDEX IF NOT EXISTS idx_sec_events_ws_outcome ON security_events(workspace_id, outcome);
CREATE INDEX IF NOT EXISTS idx_sec_events_ws_sev ON security_events(workspace_id, severity);
CREATE INDEX IF NOT EXISTS idx_sec_events_project ON security_events(project_id);
CREATE INDEX IF NOT EXISTS idx_sec_events_env ON security_events(environment_id);

-- 2. Security Findings (Deduplicated, Fingerprinted Risk & Vulnerability Items)
CREATE TABLE IF NOT EXISTS security_findings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    environment_id UUID REFERENCES environments(id) ON DELETE CASCADE,
    category VARCHAR(64) NOT NULL, -- e.g. 'EXCESSIVE_PRIVILEGE', 'DORMANT_PRIVILEGED_ACCESS'
    severity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM', -- 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    confidence VARCHAR(32) NOT NULL DEFAULT 'HIGH', -- 'LOW', 'MEDIUM', 'HIGH'
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'FALSE_POSITIVE'
    title VARCHAR(255) NOT NULL,
    safe_description TEXT NOT NULL,
    remediation_guidance TEXT NOT NULL,
    evidence_json TEXT NOT NULL, -- JSON structured evidence factors, IDs, lineage
    fingerprint VARCHAR(64) NOT NULL, -- SHA-256 stable deduplication hash
    occurrence_count INTEGER NOT NULL DEFAULT 1,
    first_observed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_observed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assignee_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_reason TEXT,
    resolved_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,

    -- Fingerprint Deduplication Invariant
    CONSTRAINT uq_sec_findings_ws_fp UNIQUE (workspace_id, fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_sec_findings_ws_status ON security_findings(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_sec_findings_ws_sev ON security_findings(workspace_id, severity);
CREATE INDEX IF NOT EXISTS idx_sec_findings_ws_cat ON security_findings(workspace_id, category);
CREATE INDEX IF NOT EXISTS idx_sec_findings_ws_time ON security_findings(workspace_id, last_observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_sec_findings_project ON security_findings(project_id);
CREATE INDEX IF NOT EXISTS idx_sec_findings_env ON security_findings(environment_id);
