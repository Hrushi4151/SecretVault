-- Phase 15 Evolution: AI Conversations, Persistent Agentic Memory, and Tool Audit Schema
-- Flyway Migration V21

CREATE TABLE IF NOT EXISTS ai_conversations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    scope_type VARCHAR(64) NOT NULL DEFAULT 'WORKSPACE',
    scope_id VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    metadata_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_conv_workspace_user ON ai_conversations(workspace_id, user_id, updated_at DESC);
CREATE INDEX idx_ai_conv_workspace_scope ON ai_conversations(workspace_id, scope_type, scope_id);

CREATE TABLE IF NOT EXISTS ai_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    role VARCHAR(32) NOT NULL, -- USER, ASSISTANT, SYSTEM, TOOL
    content TEXT NOT NULL,
    intent_type VARCHAR(64),
    confidence_score DOUBLE PRECISION,
    model_provider VARCHAR(64),
    model_name VARCHAR(64),
    tokens_used INTEGER NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    evidence_json TEXT,
    metadata_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_messages_conv_created ON ai_messages(conversation_id, created_at ASC);
CREATE INDEX idx_ai_messages_workspace_created ON ai_messages(workspace_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_tool_executions (
    id UUID PRIMARY KEY,
    message_id UUID REFERENCES ai_messages(id) ON DELETE CASCADE,
    conversation_id UUID REFERENCES ai_conversations(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    tool_name VARCHAR(128) NOT NULL,
    arguments_json TEXT,
    result_summary_json TEXT,
    execution_time_ms BIGINT NOT NULL DEFAULT 0,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_tool_exec_conv ON ai_tool_executions(conversation_id, created_at ASC);
CREATE INDEX idx_ai_tool_exec_workspace ON ai_tool_executions(workspace_id, tool_name, created_at DESC);
