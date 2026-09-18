-- ==============================================================================
-- SecretVault — V6 Versioning, Branching, Tags & Promotion Schema (Phase 4)
-- ==============================================================================

-- 1. Extend secret_versions with typed versioning and lineage columns
ALTER TABLE secret_versions ADD COLUMN IF NOT EXISTS version_type VARCHAR(32) NOT NULL DEFAULT 'VALUE_UPDATE';
ALTER TABLE secret_versions ADD COLUMN IF NOT EXISTS source_version_id UUID REFERENCES secret_versions(id) ON DELETE SET NULL;
ALTER TABLE secret_versions ADD COLUMN IF NOT EXISTS source_secret_id UUID REFERENCES secrets(id) ON DELETE SET NULL;
ALTER TABLE secret_versions ADD COLUMN IF NOT EXISTS source_environment_id UUID REFERENCES environments(id) ON DELETE SET NULL;
ALTER TABLE secret_versions ADD COLUMN IF NOT EXISTS branch_id UUID;

CREATE INDEX IF NOT EXISTS idx_secret_versions_type ON secret_versions(version_type);
CREATE INDEX IF NOT EXISTS idx_secret_versions_source_ver ON secret_versions(source_version_id);
CREATE INDEX IF NOT EXISTS idx_secret_versions_branch ON secret_versions(branch_id);

-- 2. Secret Branches Table (Isolated Feature Branches within a Secret)
CREATE TABLE IF NOT EXISTS secret_branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    secret_id UUID NOT NULL REFERENCES secrets(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    base_version_id UUID REFERENCES secret_versions(id) ON DELETE SET NULL,
    head_version_id UUID REFERENCES secret_versions(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    merged_at TIMESTAMP WITH TIME ZONE,
    merged_by UUID REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_secret_branch_name UNIQUE (secret_id, name)
);

CREATE INDEX IF NOT EXISTS idx_secret_branches_secret ON secret_branches(secret_id);
CREATE INDEX IF NOT EXISTS idx_secret_branches_status ON secret_branches(status);

-- 3. Secret Version Tags Table (Immutable Metadata Tags per Version)
CREATE TABLE IF NOT EXISTS secret_version_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    secret_version_id UUID NOT NULL REFERENCES secret_versions(id) ON DELETE CASCADE,
    name VARCHAR(64) NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_version_tag UNIQUE (secret_version_id, name)
);

CREATE INDEX IF NOT EXISTS idx_version_tags_version ON secret_version_tags(secret_version_id);
CREATE INDEX IF NOT EXISTS idx_version_tags_name ON secret_version_tags(name);
