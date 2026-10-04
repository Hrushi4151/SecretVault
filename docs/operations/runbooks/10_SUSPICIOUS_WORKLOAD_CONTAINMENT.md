# SecretVault Operational Runbook 10: Suspicious Workload Containment

## Incident Summary
Triggered when a registered consumer workload or SDK heartbeat daemon exhibits abnormal polling frequency, fails authentication repeatedly, or reports conflicting acknowledged secret versions.

---

### Step-by-Step Response Procedure

1. **Detect**: Alert `ConsumerHeartbeatAnomaly` or `SUSPICIOUS_WORKLOAD_BEHAVIOR` security finding.
2. **Quarantine Consumer**:
   ```bash
   curl -X POST https://vault.internal.net/api/v1/consumers/{consumerId}/quarantine \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
   ```
3. **Revoke Active Secret Leases**:
   ```bash
   curl -X POST https://vault.internal.net/api/v1/leases/consumers/{consumerId}/revoke-all \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
   ```
4. **Inspect Workload Pod / Instance**:
   - Verify pod identity in Kubernetes cluster or instance IP in cloud network logs.
5. **Release Quarantine / Rotate**:
   - If workload is legitimate (e.g. restart storm), lift quarantine; if malicious, terminate host container.
