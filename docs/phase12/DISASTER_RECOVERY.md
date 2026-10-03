# Phase 12: Disaster Recovery & Data Integrity

## 1. Finding & Scan State Resilience

All scan definitions, repository registrations, findings, and allowlists are stored in PostgreSQL managed with transactional integrity (`@Transactional`).

### High Availability & Replication
- Repository security tables are replicated alongside the primary SecretVault schema.
- In the event of primary node failure, standby replicas maintain finding history without loss.

### Ephemeral State Handling
- Subprocess sandboxes exist exclusively on local ephemeral storage (`scratch`).
- If an application node crashes during a scan, uncompleted scan jobs transition to `FAILED` during the startup heartbeat check, and temporary directories are cleaned by the container lifecycle manager.

### Recovery Procedures
- To re-scan a repository after unexpected cluster degradation:
  ```bash
  secretvault repository scan <repo-id>
  ```
- Because findings are deduplicated by `(workspace_id, repository_id, fingerprint, file_path)`, re-scanning is fully idempotent and will not generate duplicate alerts.
