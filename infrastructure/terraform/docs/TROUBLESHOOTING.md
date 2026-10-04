# SecretVault Terraform Provider — Troubleshooting Guide

## Common Issues & Diagnosing Errors

### 1. `SecretVault authentication failed (status 401)`
- **Cause**: Invalid, revoked, or expired token in `SECRET_VAULT_TOKEN` or failed OIDC token exchange.
- **Fix**: Verify token status using SecretVault CLI `secretvault auth status` or refresh machine identity secrets.

---

### 2. `SecretVault authorization denied (status 403)`
- **Cause**: Caller lacks necessary RBAC roles (`WORKSPACE_ADMIN` or `WORKSPACE_MEMBER`) on the specified workspace, project, or environment.
- **Fix**: Ensure your user or machine identity has appropriate membership and permissions assigned in SecretVault.

---

### 3. `SecretVault resource conflict (status 409)`
- **Cause**: A project, environment, or machine identity with the same `slug` or `name` already exists in the target container.
- **Fix**: If the resource exists, import it using `terraform import secretvault_<resource>.<name> <id>` or choose a distinct slug.

---

### 4. `x509: certificate signed by unknown authority`
- **Cause**: SecretVault is using an internal or private root CA not trusted by the host OS.
- **Fix**: Specify the CA certificate bundle in provider configuration:
  ```terraform
  provider "secretvault" {
    tls {
      ca_cert_file = "/path/to/ca.pem"
    }
  }
  ```

---

### 5. `SecretVault rate limit exceeded (status 429)`
- **Cause**: High concurrency operations exceeded backend token-bucket rate limits.
- **Fix**: Increase `max_retries` and `retry_wait_max_ms` in the provider configuration. The client respects `Retry-After` headers automatically.
