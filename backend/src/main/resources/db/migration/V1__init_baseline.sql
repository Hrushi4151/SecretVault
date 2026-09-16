-- ==============================================================================
-- SecretVault — V1 Baseline Schema Initialization
-- ==============================================================================

-- Enable UUID extension if available
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Baseline system metadata table
CREATE TABLE IF NOT EXISTS system_metadata (
    id VARCHAR(64) PRIMARY KEY,
    schema_version VARCHAR(32) NOT NULL,
    initialized_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
);

-- Insert baseline metadata record
INSERT INTO system_metadata (id, schema_version, status)
VALUES ('secretvault-core', '1.0.0', 'INITIALIZED')
ON CONFLICT (id) DO NOTHING;
