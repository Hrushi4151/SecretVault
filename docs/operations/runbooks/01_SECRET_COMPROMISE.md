# SecretVault Operational Runbook 01: Suspected Secret Compromise

## Incident Summary
Triggered when an application secret, API key, database credential, or token managed by SecretVault is suspected or confirmed leaked in public code repositories, third-party logs, or exposed endpoints.

---

### Step-by-Step Incident Response Procedure

#### 1. Detect & Triage
- **Signals:** Security Incident alert (`SECRET_LEAK_DETECTED`), Git repository scan finding, or external vulnerability report.
- Identify: `secretId`, `environmentId`, `workspaceId`, active version number, and downstream provider mappings.

#### 2. Containment
- Immediately quarantine or disable the compromised secret version to block further reads:
  ```bash
  curl -X POST https://vault.internal.net/api/v1/secrets/{secretId}/disable \
    -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
  ```
- Declare a security incident in SecretVault:
  ```bash
  curl -X POST https://vault.internal.net/api/v1/incidents \
    -H "Authorization: Bearer ${SV_ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"title": "Emergency Secret Compromise", "severity": "CRITICAL", "secretId": "'${secretId}'"}'
  ```

#### 3. Immediate Emergency Rotation
- Invoke emergency secret rollover with instant version revocation:
  ```bash
  curl -X POST https://vault.internal.net/api/v1/secrets/{secretId}/emergency-rotate \
    -H "Authorization: Bearer ${SV_ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"reason": "Compromised credential remediation"}'
  ```

#### 4. Cloud Provider & Workload Cutover
- Verify that `ProviderCredentialRotator` pushed the new secret version to connected providers (Vercel, Render).
- Send notification/webhook to subscribed consumer workloads to acknowledge the new version.

#### 5. Revocation of Compromised Version
- Permanently revoke the compromised version:
  ```bash
  curl -X POST https://vault.internal.net/api/v1/secrets/{secretId}/versions/{oldVersion}/revoke \
    -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
  ```

#### 6. Verification
- Confirm that the old credential is no longer valid against the downstream service.
- Verify that downstream workloads report healthy status using the new version via `/actuator/health` or SDK heartbeat.

#### 7. Audit & Post-Mortem
- Inspect `audit_logs` for any unauthorized access during the compromise window:
  ```bash
  curl https://vault.internal.net/api/v1/audit/secrets/{secretId} \
    -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
  ```
- Publish security incident resolution report.
