-- ==============================================================================
-- Migration: V9__provider_integrations_schema.sql
-- Description: External platform provider integrations and resource mappings
-- ==============================================================================

-- 1. Platform Provider Integrations Table
CREATE TABLE IF NOT EXISTS provider_integrations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    provider_type VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'VALIDATING',
    configuration_json TEXT NOT NULL DEFAULT '{}',
    encrypted_credential_token TEXT NOT NULL,
    encrypted_dek TEXT NOT NULL,
    iv TEXT NOT NULL,
    auth_tag TEXT NOT NULL,
    key_reference VARCHAR(128) NOT NULL,
    redacted_credential_hint VARCHAR(64) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_validated_at TIMESTAMPTZ,
    last_error_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    last_error_message VARCHAR(255),
    CONSTRAINT uq_provider_int_ws_name UNIQUE (workspace_id, display_name)
);

CREATE INDEX IF NOT EXISTS idx_provider_int_ws_type ON provider_integrations(workspace_id, provider_type);
CREATE INDEX IF NOT EXISTS idx_provider_int_ws_status ON provider_integrations(workspace_id, status);

-- 2. Platform Provider Resource Mappings Table
CREATE TABLE IF NOT EXISTS provider_resource_mappings (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    integration_id UUID NOT NULL REFERENCES provider_integrations(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    environment_id UUID NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    provider_resource_type VARCHAR(64) NOT NULL,
    provider_resource_id VARCHAR(255) NOT NULL,
    provider_resource_name VARCHAR(255) NOT NULL,
    provider_environment VARCHAR(64) NOT NULL,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    sync_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_provider_map_ws_int_proj_env UNIQUE (workspace_id, integration_id, project_id, environment_id)
);

CREATE INDEX IF NOT EXISTS idx_provider_map_ws_int ON provider_resource_mappings(workspace_id, integration_id);
CREATE INDEX IF NOT EXISTS idx_provider_map_env ON provider_resource_mappings(environment_id);
CREATE INDEX IF NOT EXISTS idx_provider_map_proj ON provider_resource_mappings(project_id);
