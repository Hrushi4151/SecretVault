# SecretVault Operational Runbook 02: Provider Credential Compromise

## Incident Summary
Triggered when a cloud provider integration credential (Vercel Bearer Token, Render API Key) stored in SecretVault for synchronizing secrets is compromised or exposed.

---

### Step-by-Step Response Procedure

1. **Detect**: Alert from Security Center or provider security team indicating API token unauthorized usage.
2. **Contain**: Disable provider integration in SecretVault immediately:
   ```bash
   curl -X POST https://vault.internal.net/api/v1/integrations/{integrationId}/disable \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
   ```
3. **Revoke at Cloud Provider**: Log into the affected cloud provider (Vercel / Render console) and immediately delete/revoke the API key.
4. **Generate New Provider Credential**: Create a new least-privilege token on the provider console.
5. **Update SecretVault Integration**: Update the integration with the newly minted token:
   ```bash
   curl -X PUT https://vault.internal.net/api/v1/integrations/{integrationId} \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{"apiKey": "NEW_GENERATED_API_KEY"}'
   ```
6. **Re-Enable Integration & Force Sync**:
   ```bash
   curl -X POST https://vault.internal.net/api/v1/integrations/{integrationId}/enable \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
   curl -X POST https://vault.internal.net/api/v1/sync/reconcile-all \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
   ```
7. **Audit**: Review audit logs for all provider sync operations during the incident.
