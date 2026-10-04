# SecretVault Terraform Provider — Authentication Guide

## 1. Supported Authentication Methods

The SecretVault provider supports two primary authentication modes:

### Method A: Static Personal Access / Service Account Token (Recommended for Local Dev & Testing)

Set the token via environment variable or provider block:

```bash
export SECRET_VAULT_ADDR="https://vault.internal:8443"
export SECRET_VAULT_TOKEN="sv_pat_abc123..."
```

```terraform
provider "secretvault" {
  address = var.secretvault_address
  token   = var.secretvault_token
}
```

---

### Method B: Machine Identity / OIDC Workload Token Exchange (Recommended for Production CI/CD & Kubernetes)

For GitHub Actions, GitLab CI, or Kubernetes pods running Terraform:

```bash
export SECRET_VAULT_ADDR="https://vault.internal:8443"
export SECRET_VAULT_CLIENT_ID="k8s-terraform-runner"
export SECRET_VAULT_CLIENT_SECRET="$ACTIONS_ID_TOKEN_REQUEST_TOKEN"
export SECRET_VAULT_WORKSPACE_ID="00000000-0000-0000-0000-000000000001"
```

```terraform
provider "secretvault" {
  address       = var.secretvault_address
  client_id     = var.client_id
  client_secret = var.client_secret
  workspace_id  = var.workspace_id
}
```

The provider will automatically call `POST /api/v1/auth/oidc/token` to exchange the OIDC assertion for a short-lived, rotated session token.

---

## 2. Environment Variables Summary

| Environment Variable | Description |
| :--- | :--- |
| `SECRET_VAULT_ADDR` | Base URL of SecretVault backend API |
| `SECRET_VAULT_TOKEN` | Static Bearer API token |
| `SECRET_VAULT_CLIENT_ID` | Machine Identity client ID / audience |
| `SECRET_VAULT_CLIENT_SECRET` | Machine Identity subject token / secret |
| `SECRET_VAULT_WORKSPACE_ID` | Default Workspace UUID |
| `SECRET_VAULT_CA_CERT_FILE` | Path to custom PEM CA certificate |
| `SECRET_VAULT_INSECURE_SKIP_VERIFY` | Set to `true` only in local test environments |
