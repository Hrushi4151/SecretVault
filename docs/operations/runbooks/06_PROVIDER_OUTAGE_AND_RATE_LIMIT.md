# SecretVault Operational Runbook 06: Cloud Provider Outage & Rate Limiting

## Incident Summary
Triggered when third-party cloud platform APIs (Vercel API, Render API) fail with 502/503/504 errors or return HTTP 429 Too Many Requests during secret rollover synchronization.

---

### Step-by-Step Response Procedure

1. **Detect**: Alert `SecretVaultProviderRateLimited` or logs showing `PROVIDER_RATE_LIMITED` / `PROVIDER_TIMEOUT`.
2. **Behavior Verification**:
   - `ProviderCredentialRotator` enters bounded exponential backoff retry (up to 3 attempts with 100ms * 2^attempt delay).
   - If rate limited, the rotation job enters `ACTIVATING_STALE` state; the new secret remains valid internally.
3. **Inspect Provider Mapping State**:
   ```bash
   curl https://vault.internal.net/api/v1/workspaces/{workspaceId}/rotation/jobs?status=FAILED \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
   ```
4. **Reconciliation when Provider Recovers**:
   - Once cloud provider status returns to normal, trigger idempotent reconciliation:
     ```bash
     curl -X POST https://vault.internal.net/api/v1/rotation/retry-stale \
       -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
     ```
5. **Idempotent Convergence**:
   - `ProviderCredentialRotator` uses persistent database claims in `event_processing_log` to safely upsert the environment variables without creating duplicates.
6. **Audit**: Confirm all affected provider mappings match expected secret versions.
