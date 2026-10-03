-- ==============================================================================
-- SecretVault — V17 Event-Driven Architecture, Outbox, Automation Policies, Webhooks & Incident Operations (Phase 13)
-- ==============================================================================

-- 1. Transactional Event Outbox
CREATE TABLE IF NOT EXISTS event_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'PROCESSING', 'PROCESSED', 'FAILED', 'DEAD_LETTER', 'CANCELLED'
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    locked_at TIMESTAMP WITH TIME ZONE,
    locked_by VARCHAR(128),
    last_error TEXT,
    processed_at TIMESTAMP WITH TIME ZONE,
    correlation_id VARCHAR(128),
    causation_id VARCHAR(128),
    request_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_available ON event_outbox(status, available_at);
CREATE INDEX IF NOT EXISTS idx_outbox_workspace_created ON event_outbox(workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON event_outbox(workspace_id, aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_outbox_event_type ON event_outbox(workspace_id, event_type);

-- 2. Consumer Processing Log (Consumer Deduplication & At-Least-Once Safety)
CREATE TABLE IF NOT EXISTS event_processing_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSED', -- 'PROCESSING', 'PROCESSED', 'FAILED', 'SKIPPED'
    attempt_count INTEGER NOT NULL DEFAULT 1,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error TEXT,
    correlation_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_event_consumer UNIQUE (event_id, consumer_name)
);

CREATE INDEX IF NOT EXISTS idx_proc_log_event ON event_processing_log(event_id);
CREATE INDEX IF NOT EXISTS idx_proc_log_workspace ON event_processing_log(workspace_id, consumer_name);

-- 3. Automation Policies (Configurable Event-Driven Automation Rules)
CREATE TABLE IF NOT EXISTS automation_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 100,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'WORKSPACE', -- 'WORKSPACE', 'PROJECT', 'ENVIRONMENT', 'SECRET'
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    environment_id UUID REFERENCES environments(id) ON DELETE CASCADE,
    secret_id UUID REFERENCES secrets(id) ON DELETE CASCADE,
    trigger_event_types TEXT NOT NULL, -- Comma-separated or JSON array of EventTypes
    conditions_json TEXT NOT NULL DEFAULT '[]', -- Structured AST JSON
    actions_json TEXT NOT NULL DEFAULT '[]', -- Structured Actions JSON
    dry_run BOOLEAN NOT NULL DEFAULT FALSE,
    approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    policy_version INTEGER NOT NULL DEFAULT 1,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_automation_policy_name UNIQUE (workspace_id, name)
);

CREATE INDEX IF NOT EXISTS idx_auto_policy_ws ON automation_policies(workspace_id, enabled);
CREATE INDEX IF NOT EXISTS idx_auto_policy_scope ON automation_policies(workspace_id, scope_type, project_id, environment_id);

-- 4. Automation Executions (Audit & Traceability of Policy Runs)
CREATE TABLE IF NOT EXISTS automation_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    policy_id UUID REFERENCES automation_policies(id) ON DELETE SET NULL,
    policy_version INTEGER NOT NULL,
    event_id UUID NOT NULL,
    trigger_event_type VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED', -- 'PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'AWAITING_APPROVAL', 'DENIED', 'SKIPPED', 'LOOP_ABORTED'
    dry_run BOOLEAN NOT NULL DEFAULT FALSE,
    evaluation_result_json TEXT,
    actions_executed_json TEXT,
    error_message TEXT,
    execution_depth INTEGER NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    causation_id VARCHAR(128),
    correlation_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auto_exec_ws ON automation_executions(workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_auto_exec_policy ON automation_executions(policy_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_auto_exec_event ON automation_executions(event_id);

-- 5. Automation Approvals (Four-Eyes Governance for Sensitive Actions)
CREATE TABLE IF NOT EXISTS automation_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    execution_id UUID NOT NULL REFERENCES automation_executions(id) ON DELETE CASCADE,
    policy_id UUID REFERENCES automation_policies(id) ON DELETE SET NULL,
    action_type VARCHAR(64) NOT NULL,
    action_payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'
    requested_by VARCHAR(128) NOT NULL,
    decided_by UUID REFERENCES users(id) ON DELETE SET NULL,
    rejection_reason TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auto_appr_ws_status ON automation_approvals(workspace_id, status);
CREATE INDEX IF NOT EXISTS idx_auto_appr_exec ON automation_approvals(execution_id);

-- 6. Notifications (In-App & Multi-Channel Alert Records)
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    recipient_id UUID REFERENCES users(id) ON DELETE CASCADE, -- NULL for workspace broadcast
    severity VARCHAR(32) NOT NULL DEFAULT 'INFO', -- 'INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    event_id UUID,
    action_url VARCHAR(512),
    channel VARCHAR(32) NOT NULL DEFAULT 'IN_APP', -- 'IN_APP', 'EMAIL', 'WEBHOOK', 'SLACK'
    status VARCHAR(32) NOT NULL DEFAULT 'UNREAD', -- 'UNREAD', 'READ', 'ACKNOWLEDGED', 'EXPIRED'
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_notif_user_status ON notifications(workspace_id, recipient_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notif_severity ON notifications(workspace_id, severity, created_at DESC);

-- 7. Notification Preferences (User & Workspace Level Customization)
CREATE TABLE IF NOT EXISTS notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    channel_in_app BOOLEAN NOT NULL DEFAULT TRUE,
    channel_webhook BOOLEAN NOT NULL DEFAULT TRUE,
    channel_email BOOLEAN NOT NULL DEFAULT FALSE,
    min_severity VARCHAR(32) NOT NULL DEFAULT 'INFO',
    muted_event_types_json TEXT NOT NULL DEFAULT '[]',
    quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    quiet_hours_start VARCHAR(8), -- e.g. "22:00"
    quiet_hours_end VARCHAR(8),   -- e.g. "07:00"
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notif_pref_user UNIQUE (workspace_id, user_id)
);

-- 8. Webhook Endpoints (Secure Outbound Event Destinations)
CREATE TABLE IF NOT EXISTS webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    destination_url VARCHAR(1024) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    subscribed_events_json TEXT NOT NULL DEFAULT '["*"]',
    signing_secret_encrypted TEXT NOT NULL,
    secret_prefix VARCHAR(16) NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_failure_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_webhook_endpoint_name UNIQUE (workspace_id, name)
);

CREATE INDEX IF NOT EXISTS idx_webhook_ep_ws ON webhook_endpoints(workspace_id, enabled);

-- 9. Webhook Deliveries (Delivery Log with SSRF & Signature Verification Tracking)
CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    webhook_id UUID NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    event_id UUID NOT NULL,
    attempt INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'SUCCESS', 'FAILED', 'DEAD_LETTER', 'BLOCKED_SSRF'
    http_status INTEGER,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    error_message TEXT,
    payload_digest VARCHAR(128),
    next_retry_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_webhook_del_ws ON webhook_deliveries(workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_webhook_del_endpoint ON webhook_deliveries(webhook_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_webhook_del_retry ON webhook_deliveries(status, next_retry_at);

-- 10. Security Incidents (First-Class Security Event & Compromise Records)
CREATE TABLE IF NOT EXISTS security_incidents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    incident_number VARCHAR(32) NOT NULL,
    severity VARCHAR(32) NOT NULL DEFAULT 'HIGH', -- 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    category VARCHAR(64) NOT NULL, -- e.g. 'SECRET_COMPROMISE', 'MACHINE_SUSPICIOUS', 'ROTATION_FAILURE', 'ANOMALY'
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'INVESTIGATING', 'CONTAINED', 'REMEDIATION', 'RESOLVED', 'CLOSED'
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    source_event_id UUID,
    assignee_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    resolved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_summary TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sec_incident_number UNIQUE (workspace_id, incident_number)
);

CREATE INDEX IF NOT EXISTS idx_sec_incident_ws_status ON security_incidents(workspace_id, status, severity);
CREATE INDEX IF NOT EXISTS idx_sec_incident_created ON security_incidents(workspace_id, created_at DESC);

-- 11. Security Incident Correlated Events
CREATE TABLE IF NOT EXISTS security_incident_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id UUID NOT NULL REFERENCES security_incidents(id) ON DELETE CASCADE,
    event_id UUID NOT NULL,
    relationship_type VARCHAR(32) NOT NULL DEFAULT 'CORRELATED', -- 'ROOT_CAUSE', 'CORRELATED', 'SIDE_EFFECT', 'REMEDIATION'
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_incident_event UNIQUE (incident_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_inc_events_inc ON security_incident_events(incident_id);

-- 12. Event Replay Requests
CREATE TABLE IF NOT EXISTS event_replay_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    target_event_id UUID,
    event_type_filter VARCHAR(128),
    aggregate_type VARCHAR(64),
    aggregate_id VARCHAR(128),
    from_timestamp TIMESTAMP WITH TIME ZONE,
    to_timestamp TIMESTAMP WITH TIME ZONE,
    reexecute_side_effects BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'
    events_replayed_count INTEGER NOT NULL DEFAULT 0,
    requested_by UUID REFERENCES users(id) ON DELETE SET NULL,
    error_message TEXT,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_event_replay_ws ON event_replay_requests(workspace_id, created_at DESC);
