-- ==============================================================================
-- Migration V15: Secret Rotation, Leases, Consumers & Dependency Graph Schema
-- ==============================================================================

-- 1. Rotation Policies Table
CREATE TABLE IF NOT EXISTS rotation_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    secret_id UUID NOT NULL REFERENCES secrets(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT true,
    strategy VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    interval_seconds BIGINT NOT NULL DEFAULT 2592000, -- 30 days default
    min_interval_seconds BIGINT NOT NULL DEFAULT 3600, -- 1 hour min
    max_secret_age_seconds BIGINT,
    rotation_window_seconds BIGINT NOT NULL DEFAULT 86400, -- 24 hours
    cron_expression VARCHAR(64),
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    max_retries INT NOT NULL DEFAULT 3,
    retry_backoff_seconds INT NOT NULL DEFAULT 300,
    validation_type VARCHAR(32) NOT NULL DEFAULT 'AUTHENTICATION',
    rollout_strategy VARCHAR(32) NOT NULL DEFAULT 'STAGED',
    secret_type VARCHAR(32) NOT NULL DEFAULT 'PASSWORD',
    secret_generator_config JSONB,
    provider_id UUID REFERENCES provider_integrations(id) ON DELETE SET NULL,
    grace_period_seconds BIGINT NOT NULL DEFAULT 1800, -- 30 minutes
    auto_revoke_previous BOOLEAN NOT NULL DEFAULT true,
    auto_rollback_on_failure BOOLEAN NOT NULL DEFAULT true,
    require_approval BOOLEAN NOT NULL DEFAULT false,
    require_jit_approval BOOLEAN NOT NULL DEFAULT false,
    notification_enabled BOOLEAN NOT NULL DEFAULT true,
    next_rotation_due_at TIMESTAMP WITH TIME ZONE,
    last_rotated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rotation_policy_secret UNIQUE (secret_id)
);

CREATE INDEX IF NOT EXISTS idx_rot_pol_ws ON rotation_policies(workspace_id);
CREATE INDEX IF NOT EXISTS idx_rot_pol_due ON rotation_policies(enabled, next_rotation_due_at);

-- 2. Rotation Jobs Table
CREATE TABLE IF NOT EXISTS rotation_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    secret_id UUID NOT NULL REFERENCES secrets(id) ON DELETE CASCADE,
    policy_id UUID REFERENCES rotation_policies(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    trigger_type VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    target_version_number INT,
    previous_version_number INT,
    generated_version_id UUID REFERENCES secret_versions(id) ON DELETE SET NULL,
    idempotency_key VARCHAR(128),
    error_code VARCHAR(64),
    error_message TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    staged_at TIMESTAMP WITH TIME ZONE,
    activated_at TIMESTAMP WITH TIME ZONE,
    grace_period_ends_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    rolled_back_at TIMESTAMP WITH TIME ZONE,
    initiated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    machine_identity_id UUID REFERENCES machine_identities(id) ON DELETE SET NULL,
    emergency_reason TEXT,
    version INT NOT NULL DEFAULT 0, -- Optimistic locking
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rot_job_ws ON rotation_jobs(workspace_id);
CREATE INDEX IF NOT EXISTS idx_rot_job_sec ON rotation_jobs(secret_id, status);
CREATE INDEX IF NOT EXISTS idx_rot_job_status ON rotation_jobs(status, next_retry_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_rot_job_idemp ON rotation_jobs(workspace_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- 3. Rotation Attempts Table
CREATE TABLE IF NOT EXISTS rotation_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES rotation_jobs(id) ON DELETE CASCADE,
    attempt_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    error_message TEXT,
    duration_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rot_att_job ON rotation_attempts(job_id);

-- 4. Rotation Validations Table
CREATE TABLE IF NOT EXISTS rotation_validations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES rotation_jobs(id) ON DELETE CASCADE,
    validation_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    validator_target TEXT,
    response_code INT,
    latency_ms BIGINT,
    error_message TEXT,
    details JSONB,
    validated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rot_val_job ON rotation_validations(job_id);

-- 5. Secret Consumers Table (Registered Workload Consumers)
CREATE TABLE IF NOT EXISTS secret_consumers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    environment_id UUID NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    consumer_type VARCHAR(32) NOT NULL DEFAULT 'APPLICATION',
    machine_identity_id UUID REFERENCES machine_identities(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    runtime_framework VARCHAR(64), -- e.g. "Spring Boot 3.3.4", "Java 21", "Node.js"
    sdk_version VARCHAR(32),
    instance_id VARCHAR(128),
    hostname VARCHAR(128),
    supports_dynamic_refresh BOOLEAN NOT NULL DEFAULT true,
    requires_restart BOOLEAN NOT NULL DEFAULT false,
    last_heartbeat_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_refresh_acknowledged_at TIMESTAMP WITH TIME ZONE,
    current_acknowledged_version INT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_consumer_name_env UNIQUE (environment_id, name, instance_id)
);

CREATE INDEX IF NOT EXISTS idx_sec_cons_ws ON secret_consumers(workspace_id);
CREATE INDEX IF NOT EXISTS idx_sec_cons_env ON secret_consumers(environment_id, status);
CREATE INDEX IF NOT EXISTS idx_sec_cons_hb ON secret_consumers(last_heartbeat_at);

-- 6. Secret Dependencies Table (Graph of which consumer uses which secret)
CREATE TABLE IF NOT EXISTS secret_dependencies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    consumer_id UUID NOT NULL REFERENCES secret_consumers(id) ON DELETE CASCADE,
    secret_id UUID NOT NULL REFERENCES secrets(id) ON DELETE CASCADE,
    alias_name VARCHAR(128),
    is_required BOOLEAN NOT NULL DEFAULT true,
    last_consumed_version INT,
    last_accessed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_consumer_secret_dep UNIQUE (consumer_id, secret_id)
);

CREATE INDEX IF NOT EXISTS idx_sec_dep_sec ON secret_dependencies(secret_id);
CREATE INDEX IF NOT EXISTS idx_sec_dep_cons ON secret_dependencies(consumer_id);

-- 7. Secret Leases Table (Time-bounded authorized secret consumption)
CREATE TABLE IF NOT EXISTS secret_leases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    environment_id UUID NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    secret_id UUID NOT NULL REFERENCES secrets(id) ON DELETE CASCADE,
    secret_version_number INT NOT NULL,
    machine_identity_id UUID REFERENCES machine_identities(id) ON DELETE SET NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    consumer_id UUID REFERENCES secret_consumers(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ttl_seconds BIGINT NOT NULL DEFAULT 900, -- 15 minutes
    max_lifetime_seconds BIGINT NOT NULL DEFAULT 14400, -- 4 hours max
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_renewed_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by UUID REFERENCES users(id) ON DELETE SET NULL,
    revocation_reason TEXT,
    ip_address VARCHAR(45),
    user_agent VARCHAR(256),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sec_lease_ws ON secret_leases(workspace_id);
CREATE INDEX IF NOT EXISTS idx_sec_lease_sec ON secret_leases(secret_id, status);
CREATE INDEX IF NOT EXISTS idx_sec_lease_exp ON secret_leases(status, expires_at);
CREATE INDEX IF NOT EXISTS idx_sec_lease_mach ON secret_leases(machine_identity_id, status);
