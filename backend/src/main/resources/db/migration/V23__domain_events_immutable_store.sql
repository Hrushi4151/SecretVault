-- ==============================================================================
-- SecretVault — V23 Authoritative Immutable Domain Event Store (Phase 13)
-- ==============================================================================
-- Establishes the authoritative, immutable domain event history stream
-- separate from the mutable transactional outbox delivery queue.

CREATE TABLE IF NOT EXISTS domain_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    environment_id UUID REFERENCES environments(id) ON DELETE SET NULL,
    secret_id UUID REFERENCES secrets(id) ON DELETE SET NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version INTEGER,
    actor_type VARCHAR(64) NOT NULL DEFAULT 'USER',
    actor_id VARCHAR(128),
    correlation_id VARCHAR(128),
    causation_id VARCHAR(128),
    request_id VARCHAR(128),
    source VARCHAR(128) NOT NULL DEFAULT 'secretvault-core',
    severity VARCHAR(32) NOT NULL DEFAULT 'INFO',
    payload_json TEXT NOT NULL,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Query performance indexes for multi-tenant streaming, aggregate reconstruction, and audit trails
CREATE INDEX IF NOT EXISTS idx_domain_events_ws_time ON domain_events(workspace_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_domain_events_ws_type ON domain_events(workspace_id, event_type, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_domain_events_ws_agg ON domain_events(workspace_id, aggregate_type, aggregate_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_domain_events_correlation ON domain_events(correlation_id);
CREATE INDEX IF NOT EXISTS idx_domain_events_secret ON domain_events(workspace_id, secret_id, occurred_at DESC);

-- Database-level immutability enforcement: Prevent UPDATE and DELETE operations
CREATE OR REPLACE FUNCTION prevent_domain_event_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'domain_events is strictly immutable: % operations are prohibited', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_domain_event_mutation ON domain_events;
CREATE TRIGGER trg_prevent_domain_event_mutation
    BEFORE UPDATE OR DELETE ON domain_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_domain_event_mutation();

-- Outbox claim fencing: Assign unique claim tokens to prevent stale workers from acknowledging or failing work
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS claim_token VARCHAR(128);
CREATE INDEX IF NOT EXISTS idx_outbox_claim_token ON event_outbox(claim_token);
