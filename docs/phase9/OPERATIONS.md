# Phase 9: Operations & Runbook

## Operational Procedures

This runbook outlines operational lifecycle procedures, monitoring alerts, and incident response steps for SecretVault Machine Identity and OIDC Workload Federation.

---

## 1. Routine Maintenance

### Machine Session Purge
- Expired machine sessions can be cleaned up automatically or on a scheduled basis.
- SecretVault checks session expiration on every request (`expires_at < NOW()`).

### Identity Provider Key Rotation
- Identity providers (e.g. GitHub Actions) rotate their JWKS public signing keys periodically.
- SecretVault's `JwksKeyProvider` handles key rotations automatically:
  - Caches JWKS with a TTL (default: 60 minutes).
  - Triggers an immediate key fetch on cache miss when an incoming JWT references a new Key ID (`kid`).

---

## 2. Emergency Incident Response

### Compromised Workload / Machine Identity
If a machine identity's credentials or pipeline environment are compromised:

1. **Disable Machine Identity Immediately**:
   ```bash
   secretvault machine disable <machine-identity-id>
   ```
   Or via API: `POST /api/v1/workspaces/{workspaceId}/machines/{machineId}/disable`.
   *Result*: Immediately terminates and blocks all active sessions and rejects subsequent token exchange attempts.

2. **Revoke Active Sessions**:
   - Inspect active sessions: `GET /api/v1/workspaces/{workspaceId}/machines/{machineId}/sessions`.
   - Revoke specific sessions: `DELETE /api/v1/workspaces/{workspaceId}/machines/{machineId}/sessions/{sessionId}`.

3. **Audit Token Access**:
   - Query `audit_events` for `SECRET_REVEAL` or `SECRET_READ` where `actor = <machine-identity-id>`.
   - Identify all secrets accessed during the compromise window and initiate secret rotation.

---

## 3. Monitoring & Alerting Metrics

| Metric / Event | Severity | Threshold | Action |
| :--- | :--- | :--- | :--- |
| `OIDC_TOKEN_EXCHANGE_FAILURE` | WARNING | > 10 failures in 5 min | Check for expired token, branch policy mismatch, or provider outage. |
| `SSRF_BLOCK_TRIGGERED` | HIGH | > 0 events | Investigate attempted internal IP discovery endpoint configuration. |
| `OVERLY_BROAD_POLICY_DETECTED`| MEDIUM | > 0 in Security Center | Review trust policy claim rules and enforce repository/branch pinning. |
| `MACHINE_EXPIRATION_APPROACHING`| LOW | Within 7 days | Review and renew machine identity validity with workspace admin. |
