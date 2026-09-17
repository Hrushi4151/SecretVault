-- ==============================================================================
-- SecretVault — V4 Workspace Invitations & Access Scoping (Phase 2 Extension)
-- ==============================================================================

-- 1. Workspace Invitations
CREATE TABLE IF NOT EXISTS workspace_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    invited_by UUID REFERENCES users(id) ON DELETE SET NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'DEVELOPER',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_invitations_workspace_id ON workspace_invitations(workspace_id);
CREATE INDEX IF NOT EXISTS idx_invitations_email ON workspace_invitations(email);
CREATE INDEX IF NOT EXISTS idx_invitations_token_hash ON workspace_invitations(token_hash);
CREATE INDEX IF NOT EXISTS idx_invitations_status ON workspace_invitations(status);

-- 2. Scoped Project Access
CREATE TABLE IF NOT EXISTS project_access (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL DEFAULT 'DEVELOPER',
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_user_access UNIQUE (project_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_project_access_project_id ON project_access(project_id);
CREATE INDEX IF NOT EXISTS idx_project_access_user_id ON project_access(user_id);

-- 3. Scoped Environment Access
CREATE TABLE IF NOT EXISTS environment_access (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    environment_id UUID NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_level VARCHAR(32) NOT NULL DEFAULT 'WRITE',
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_env_user_access UNIQUE (environment_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_env_access_env_id ON environment_access(environment_id);
CREATE INDEX IF NOT EXISTS idx_env_access_user_id ON environment_access(user_id);
