-- ==============================================================================
-- SecretVault — V11 Schema Migration
-- Phase 8: Sync Engine & Drift Detection System
-- ==============================================================================

-- 1. Drift Records Table (Persistent, fingerprinted, deduplicated drift ledger)
CREATE TABLE IF NOT EXISTS drift_records (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    environment_id UUID NOT NULL,
    integration_id UUID NOT NULL,
    mapping_id UUID NOT NULL,
    secret_id UUID,
    secret_name VARCHAR(255) NOT NULL,
    provider_secret_identifier VARCHAR(255),
    drift_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    desired_fingerprint VARCHAR(128),
    observed_fingerprint VARCHAR(128),
    fingerprint VARCHAR(64) NOT NULL,
    first_detected_at TIMESTAMPTZ NOT NULL,
    last_detected_at TIMESTAMPTZ NOT NULL,
    occurrence_count INT NOT NULL DEFAULT 1,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID,
    resolution_reason TEXT,
    error_code VARCHAR(64),
    details_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_drift_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_drift_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_drift_environment FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE,
    CONSTRAINT fk_drift_integration FOREIGN KEY (integration_id) REFERENCES provider_integrations(id) ON DELETE CASCADE,
    CONSTRAINT fk_drift_mapping FOREIGN KEY (mapping_id) REFERENCES provider_resource_mappings(id) ON DELETE CASCADE,
    CONSTRAINT fk_drift_secret FOREIGN KEY (secret_id) REFERENCES secrets(id) ON DELETE SET NULL,
    CONSTRAINT fk_drift_resolved_by FOREIGN KEY (resolved_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_drift_records_ws_fp UNIQUE (workspace_id, fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_drift_ws ON drift_records(workspace_id);
CREATE INDEX IF NOT EXISTS idx_drift_ws_status ON drift_records(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_drift_mapping ON drift_records(mapping_id);
CREATE INDEX IF NOT EXISTS idx_drift_proj_env ON drift_records(project_id, environment_id);
CREATE INDEX IF NOT EXISTS idx_drift_sev ON drift_records(workspace_id, severity);
CREATE INDEX IF NOT EXISTS idx_drift_type ON drift_records(workspace_id, drift_type);

-- 2. Sync Jobs Table (Lifecycle tracking for dry-run and live synchronization execution)
CREATE TABLE IF NOT EXISTS sync_jobs (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    scope VARCHAR(32) NOT NULL,
    scope_resource_id UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    dry_run BOOLEAN NOT NULL DEFAULT FALSE,
    reconciliation_policy VARCHAR(64) NOT NULL DEFAULT 'SAFE_RECONCILIATION',
    requested_by UUID NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    total_operations INT NOT NULL DEFAULT 0,
    successful_operations INT NOT NULL DEFAULT 0,
    failed_operations INT NOT NULL DEFAULT 0,
    blocked_operations INT NOT NULL DEFAULT 0,
    drift_count INT NOT NULL DEFAULT 0,
    error_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sync_job_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT fk_sync_job_user FOREIGN KEY (requested_by) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sync_jobs_ws ON sync_jobs(workspace_id);
CREATE INDEX IF NOT EXISTS idx_sync_jobs_ws_status ON sync_jobs(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_sync_jobs_created ON sync_jobs(workspace_id, created_at DESC);

-- 3. Sync Operations Table (Individual secret-level operations within a sync job)
CREATE TABLE IF NOT EXISTS sync_operations (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    secret_id UUID,
    secret_name VARCHAR(255) NOT NULL,
    mapping_id UUID NOT NULL,
    integration_id UUID NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    desired_fingerprint VARCHAR(128),
    observed_fingerprint VARCHAR(128),
    reason TEXT,
    error_code VARCHAR(64),
    error_message TEXT,
    executed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sync_op_job FOREIGN KEY (job_id) REFERENCES sync_jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_sync_op_secret FOREIGN KEY (secret_id) REFERENCES secrets(id) ON DELETE SET NULL,
    CONSTRAINT fk_sync_op_mapping FOREIGN KEY (mapping_id) REFERENCES provider_resource_mappings(id) ON DELETE CASCADE,
    CONSTRAINT fk_sync_op_integration FOREIGN KEY (integration_id) REFERENCES provider_integrations(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sync_op_job ON sync_operations(job_id);
CREATE INDEX IF NOT EXISTS idx_sync_op_secret ON sync_operations(secret_id);
CREATE INDEX IF NOT EXISTS idx_sync_op_mapping ON sync_operations(mapping_id);
