# SecretVault Phase 12.1 — Production Operations Runbook

## 1. Rotation Failure Remediation

### 1.1 Detection
- **Alert:** `SecretRotationJobFailed` or Security Center finding `Failed Secret Rotation Job: [job-id]`.
- **Log Pattern:** `Rotation execution failed for job {jobId}: {reason}`.

### 1.2 Diagnosis
1. Query job details via API or CLI:
   ```bash
   secretvault rotation jobs get --job-id <job-id>
   ```
2. Inspect `attemptRepository` status to identify failure stage (`GENERATION`, `VALIDATION`, `STAGING`, `ACTIVATION`).
3. Check target provider logs or network firewall rules if `GenericHttpRotator` or `DatabaseRotator` failed validation.

### 1.3 Action
- If transient network or rate-limiting issue (HTTP 429 / 503):
  ```bash
  secretvault rotation jobs retry --job-id <job-id>
  ```
- If credential was activated but consumer deployment failed:
  ```bash
  secretvault rotation rollback --secret-id <secret-id> --target-version <previous-version>
  ```

### 1.4 Verification
- Ensure `secretvault rotation jobs list --secret-id <secret-id>` shows status `COMPLETED` or `ACTIVE`.
- Verify consumers acknowledge new version in `secretvault consumers list`.

---

## 2. Compromised Secret Incident Procedure

### 2.1 Detection
- Security Center alert or automated detection of credential leak in version control.

### 2.2 Immediate Action
1. Mark secret as compromised with immediate lease revocation and rotation:
   ```bash
   secretvault secrets mark-compromised --secret-id <secret-id> --incident-details "Leak in public git commit" --revoke-leases --rotate-now
   ```
2. Verify all active ephemeral leases transition to `REVOKED` in `SecretLeaseRepository`.
3. Check that a new rotation job with `triggerType=EMERGENCY` activates $v_{N+1}$.

---

## 3. Stale Consumer Triage

### 3.1 Detection
- Security Center finding `Stale Secret Consumer: [consumer-name]`.
- Consumer has not updated `currentAcknowledgedVersion` past grace period.

### 3.2 Action
1. Inspect host logs for the consumer workload instance.
2. Check if the consumer requires a restart (`requiresRestart == true`) or if SDK background refresh is encountering local connectivity issues.
3. If instance is decommissioned, disable consumer:
   ```bash
   secretvault consumers disable --consumer-id <consumer-id>
   ```
