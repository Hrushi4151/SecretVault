# SecretVault Operational Runbook 09: Machine Identity & OIDC Token Compromise

## Incident Summary
Triggered when a Machine Identity (CI/CD runner, Kubernetes workload, Terraform service account) client secret or OIDC trust relationship is compromised.

---

### Step-by-Step Response Procedure

1. **Detect**: Alert `MACHINE_SECRET_ACCESS_DENIED` spike or abnormal workload token consumption from unapproved IP ranges.
2. **Immediate Token Revocation**:
   ```bash
   curl -X POST https://vault.internal.net/api/v1/machine-identities/{identityId}/tokens/revoke-all \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
   ```
3. **Disable Machine Identity**:
   ```bash
   curl -X POST https://vault.internal.net/api/v1/machine-identities/{identityId}/disable \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
   ```
4. **Audit Accessed Secrets**:
   - Inspect secrets accessed by this machine identity in the last 24 hours:
     ```bash
     curl https://vault.internal.net/api/v1/audit/actors/{identityId} \
       -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
     ```
5. **Rotate All Exposed Secrets**:
   - Trigger mass rotation across all secrets accessed by the compromised machine identity.
6. **Re-key Machine Identity & Re-enable**:
   - Issue fresh credentials and update workload configuration.
