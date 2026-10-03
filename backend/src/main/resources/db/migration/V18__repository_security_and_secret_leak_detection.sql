-- ==============================================================================
-- SecretVault — V18 Repository Security & Secret Leak Detection Platform (Phase 12)
-- ==============================================================================

-- 1. Connected Git / Source Repositories
CREATE TABLE IF NOT EXISTS repositories (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL, -- 'GITHUB', 'GITLAB', 'BITBUCKET', 'LOCAL', 'ARCHIVE'
    external_repository_id VARCHAR(128),
    owner VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    default_branch VARCHAR(128) NOT NULL DEFAULT 'main',
    visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE', -- 'PUBLIC', 'PRIVATE', 'INTERNAL'
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'ARCHIVED', 'DISABLED'
    clone_url VARCHAR(1024),
    auth_credential_encrypted TEXT,
    last_scan_at TIMESTAMP WITH TIME ZONE,
    last_successful_scan_at TIMESTAMP WITH TIME ZONE,
    last_commit_sha VARCHAR(64),
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_repo_workspace_provider UNIQUE (workspace_id, provider, owner, name)
);

CREATE INDEX IF NOT EXISTS idx_repos_workspace ON repositories(workspace_id);
CREATE INDEX IF NOT EXISTS idx_repos_provider ON repositories(workspace_id, provider);
CREATE INDEX IF NOT EXISTS idx_repos_visibility ON repositories(workspace_id, visibility);

-- 2. Repository Security Policies
CREATE TABLE IF NOT EXISTS repository_security_policies (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    repository_id UUID REFERENCES repositories(id) ON DELETE CASCADE,
    scan_on_push BOOLEAN NOT NULL DEFAULT TRUE,
    scan_pr BOOLEAN NOT NULL DEFAULT TRUE,
    scan_history BOOLEAN NOT NULL DEFAULT TRUE,
    entropy_detection_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    live_validation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    fail_ci_severity VARCHAR(32) NOT NULL DEFAULT 'HIGH', -- 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'
    max_history_depth INTEGER NOT NULL DEFAULT 1000,
    excluded_paths_json TEXT,
    allowed_detectors_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_repo_policies_ws ON repository_security_policies(workspace_id);
CREATE INDEX IF NOT EXISTS idx_repo_policies_repo ON repository_security_policies(repository_id);

-- 3. Repository Scans Ledger
CREATE TABLE IF NOT EXISTS repository_scans (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    scan_type VARCHAR(32) NOT NULL, -- 'FULL', 'INCREMENTAL', 'COMMIT', 'BRANCH', 'PR', 'DIRECTORY', 'ARCHIVE'
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED', -- 'QUEUED', 'CLONING', 'INDEXING', 'SCANNING', 'CLASSIFYING', 'VALIDATING', 'FINALIZING', 'COMPLETED', 'FAILED', 'CANCELLED'
    commit_sha VARCHAR(64),
    base_sha VARCHAR(64),
    head_sha VARCHAR(64),
    branch VARCHAR(128),
    files_scanned INTEGER NOT NULL DEFAULT 0,
    commits_scanned INTEGER NOT NULL DEFAULT 0,
    findings_count INTEGER NOT NULL DEFAULT 0,
    high_risk_count INTEGER NOT NULL DEFAULT 0,
    skipped_files_count INTEGER NOT NULL DEFAULT 0,
    duration_ms BIGINT,
    error_message TEXT,
    correlation_id VARCHAR(128),
    idempotency_key VARCHAR(128),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scans_workspace_created ON repository_scans(workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_scans_repository ON repository_scans(repository_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_scans_status ON repository_scans(status);

-- 4. Secret Findings (Cluster / Deduplicated Findings)
CREATE TABLE IF NOT EXISTS secret_findings (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    scan_id UUID REFERENCES repository_scans(id) ON DELETE SET NULL,
    fingerprint VARCHAR(128) NOT NULL,
    detector_type VARCHAR(64) NOT NULL,
    secret_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM', -- 'INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    confidence VARCHAR(32) NOT NULL DEFAULT 'HIGH', -- 'LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH'
    status VARCHAR(32) NOT NULL DEFAULT 'DETECTED', -- 'DETECTED', 'TRIAGED', 'CONFIRMED', 'FALSE_POSITIVE', 'REMEDIATION_PENDING', 'REMEDIATING', 'ROTATION_PENDING', 'ROTATED', 'REVOKED', 'RESOLVED', 'REOPENED', 'IGNORED', 'EXPIRED'
    visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    branch VARCHAR(128),
    commit_sha VARCHAR(64),
    file_path VARCHAR(1024) NOT NULL,
    line_number INTEGER,
    column_number INTEGER,
    masked_evidence VARCHAR(255) NOT NULL,
    entropy DOUBLE PRECISION,
    validation_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN', -- 'UNKNOWN', 'ACTIVE', 'INACTIVE', 'EXPIRED', 'INVALID', 'VALIDATION_FAILED'
    remediation_status VARCHAR(32) NOT NULL DEFAULT 'NONE', -- 'NONE', 'PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'
    secret_id UUID REFERENCES secrets(id) ON DELETE SET NULL,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_finding_ws_repo_fp_file UNIQUE (workspace_id, repository_id, fingerprint, file_path)
);

CREATE INDEX IF NOT EXISTS idx_findings_workspace ON secret_findings(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_findings_repo ON secret_findings(repository_id, status);
CREATE INDEX IF NOT EXISTS idx_findings_fingerprint ON secret_findings(fingerprint);
CREATE INDEX IF NOT EXISTS idx_findings_severity ON secret_findings(workspace_id, severity);
CREATE INDEX IF NOT EXISTS idx_findings_secret_id ON secret_findings(secret_id);

-- 5. Secret Finding Occurrences
CREATE TABLE IF NOT EXISTS secret_finding_occurrences (
    id UUID PRIMARY KEY,
    finding_id UUID NOT NULL REFERENCES secret_findings(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    commit_sha VARCHAR(64) NOT NULL,
    branch VARCHAR(128),
    file_path VARCHAR(1024) NOT NULL,
    line_number INTEGER,
    column_number INTEGER,
    detector VARCHAR(64) NOT NULL,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_occurrences_finding ON secret_finding_occurrences(finding_id);
CREATE INDEX IF NOT EXISTS idx_occurrences_repo ON secret_finding_occurrences(repository_id, commit_sha);

-- 6. Finding Allowlists
CREATE TABLE IF NOT EXISTS finding_allowlists (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    repository_id UUID REFERENCES repositories(id) ON DELETE CASCADE,
    fingerprint VARCHAR(128),
    detector_type VARCHAR(64),
    file_pattern VARCHAR(512),
    reason TEXT NOT NULL,
    created_by UUID,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_allowlists_ws ON finding_allowlists(workspace_id);
CREATE INDEX IF NOT EXISTS idx_allowlists_repo ON finding_allowlists(repository_id);
CREATE INDEX IF NOT EXISTS idx_allowlists_fp ON finding_allowlists(fingerprint);

-- 7. Remediation Jobs
CREATE TABLE IF NOT EXISTS finding_remediation_jobs (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    finding_id UUID NOT NULL REFERENCES secret_findings(id) ON DELETE CASCADE,
    remediation_action VARCHAR(64) NOT NULL, -- 'MARK_FALSE_POSITIVE', 'IGNORE', 'ROTATE_SECRET', 'REVOKE_SECRET', 'REWRITE_HISTORY'
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'
    actor_id UUID,
    details_json TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_remediation_ws ON finding_remediation_jobs(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_remediation_finding ON finding_remediation_jobs(finding_id);
