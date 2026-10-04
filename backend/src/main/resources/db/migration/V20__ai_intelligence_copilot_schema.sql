-- Phase 15: AI Intelligence Co-Pilot & DevSecOps Security Operations Platform
-- Flyway Migration V20

CREATE TABLE IF NOT EXISTS ai_inquiries (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    prompt TEXT NOT NULL,
    intent_type VARCHAR(64) NOT NULL,
    model_provider VARCHAR(64) NOT NULL,
    model_name VARCHAR(64) NOT NULL,
    response_text TEXT NOT NULL,
    confidence_score DOUBLE PRECISION NOT NULL DEFAULT 0.95,
    token_count INTEGER NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    sanitized_context_summary_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_inquiries_workspace_created ON ai_inquiries(workspace_id, created_at DESC);
CREATE INDEX idx_ai_inquiries_user_created ON ai_inquiries(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_rca_reports (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    inquiry_id UUID REFERENCES ai_inquiries(id) ON DELETE SET NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    root_cause_summary VARCHAR(512) NOT NULL,
    detailed_explanation TEXT NOT NULL,
    confidence_score DOUBLE PRECISION NOT NULL DEFAULT 0.95,
    telemetry_evidence_json TEXT NOT NULL DEFAULT '[]',
    remediation_strategy VARCHAR(512),
    drift_hash_mismatch BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_rca_workspace_target ON ai_rca_reports(workspace_id, target_type, target_id);
CREATE INDEX idx_ai_rca_workspace_created ON ai_rca_reports(workspace_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_remediation_plans (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    rca_report_id UUID REFERENCES ai_rca_reports(id) ON DELETE SET NULL,
    plan_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    confidence_score DOUBLE PRECISION NOT NULL DEFAULT 0.95,
    target_resource_type VARCHAR(64) NOT NULL,
    target_resource_id VARCHAR(128) NOT NULL,
    remediation_steps_json TEXT NOT NULL DEFAULT '[]',
    payload_diff_json TEXT,
    blast_radius_json TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
    created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    executed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    execution_result_json TEXT,
    feedback_rating INTEGER,
    feedback_comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_plans_workspace_status ON ai_remediation_plans(workspace_id, status);
CREATE INDEX idx_ai_plans_target ON ai_remediation_plans(workspace_id, target_resource_type, target_resource_id);
CREATE INDEX idx_ai_plans_expires ON ai_remediation_plans(expires_at);

CREATE TABLE IF NOT EXISTS ai_token_budgets (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE UNIQUE,
    monthly_token_budget INTEGER NOT NULL DEFAULT 1000000,
    tokens_consumed_this_month INTEGER NOT NULL DEFAULT 0,
    rate_limit_per_minute INTEGER NOT NULL DEFAULT 60,
    inquiries_this_minute INTEGER NOT NULL DEFAULT 0,
    minute_window_start TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    month_window_start TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
