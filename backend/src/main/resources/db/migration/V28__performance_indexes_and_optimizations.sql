-- ==============================================================================
-- SecretVault — V28 Database Performance Indexes & Query Optimizations
-- ==============================================================================

-- 1. Composite index for chronological workspace audit log retrieval
-- Eliminates table scans and in-memory sorting for audit log feeds
CREATE INDEX IF NOT EXISTS idx_audit_logs_ws_created_desc 
ON audit_logs(workspace_id, created_at DESC);

-- 2. Composite indexes for Security Findings status & severity aggregation
-- Accelerates getPosture and getOverview queries in SecurityPostureService
CREATE INDEX IF NOT EXISTS idx_sec_findings_ws_status_sev 
ON security_findings(workspace_id, status, severity);

CREATE INDEX IF NOT EXISTS idx_sec_findings_ws_observed_desc 
ON security_findings(workspace_id, last_observed_at DESC);

-- 3. Composite indexes for Workspace and Project hierarchies
CREATE INDEX IF NOT EXISTS idx_projects_ws_status 
ON projects(workspace_id, status);

CREATE INDEX IF NOT EXISTS idx_environments_project_status 
ON environments(project_id, status);
