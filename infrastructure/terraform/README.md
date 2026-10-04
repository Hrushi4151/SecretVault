# SecretVault Terraform Provider

The official **SecretVault Terraform Provider** allows DevSecOps and Platform teams to manage SecretVault workspaces, projects, environments, machine identities, provider integrations, and secrets as Infrastructure as Code (IaC) via HashiCorp Terraform and OpenTofu.

---

## Provider Source & Installation

```terraform
terraform {
  required_version = ">= 1.5.0"
  required_providers {
    secretvault = {
      source  = "registry.terraform.io/secretvault/secretvault"
      version = "~> 1.0.0"
    }
  }
}

provider "secretvault" {
  address      = "https://vault.internal:8443"
  token        = var.secretvault_token
  workspace_id = var.workspace_id
}
```

---

## Key Features

- **HashiCorp Terraform Plugin Framework**: Built using modern Go Plugin Framework with native plan modifiers, validators, and structured diagnostics.
- **Zero-Plaintext Read Protection**: `resource "secretvault_secret"` and `data "secretvault_secret"` strictly query metadata endpoints during `Read()`, preventing routines plans and refreshes from pulling plaintext into memory or state unnecessarily.
- **Envelope Encryption Parity**: Interacts cleanly with SecretVault envelope encryption backend without exposing KMS or data keys to Terraform.
- **Multi-Tenant Scoping**: Enforces explicit workspace, project, and environment boundaries with `X-Workspace-ID` request correlation.
- **Workload OIDC & Machine Identities**: Automates machine credentials, token lifetimes, and external cloud provider integrations (Render, Vercel, GitHub, AWS).
- **Production Hardened HTTP Client**: Context cancellation, bounded exponential retries with jitter, `Retry-After` rate limit handling, and automated token/password redaction in diagnostics.

---

## Implemented Resources & Data Sources

| Type | Name | Purpose | Import Support |
| :--- | :--- | :--- | :--- |
| **Resource** | `secretvault_project` | Application project grouping | `workspace_id/project_id` |
| **Resource** | `secretvault_environment` | Deployment tiers (dev, staging, prod) | `workspace_id/project_id/environment_id` |
| **Resource** | `secretvault_secret` | Versioned secrets with write-only protection | `workspace_id/project_id/environment_id/secret_id` |
| **Resource** | `secretvault_machine_identity` | Workload/K8s machine identity | `workspace_id/machine_id` |
| **Resource** | `secretvault_provider_integration` | External cloud sync integration | `workspace_id/integration_id` |
| **Data Source** | `secretvault_workspace` | Read workspace container metadata | N/A |
| **Data Source** | `secretvault_project` | Read project details | N/A |
| **Data Source** | `secretvault_environment` | Read environment tier details | N/A |
| **Data Source** | `secretvault_secret` | Read secret metadata (zero-plaintext) | N/A |

---

## State Safety Matrix

| Resource Attribute | In State? | Sensitive Flag? | Fetched in `Read()`? | Storage Boundary Note |
| :--- | :--- | :--- | :--- | :--- |
| `secretvault_secret.name` | Yes | No | Yes | Key identifier |
| `secretvault_secret.value` | Yes | **YES** | **NEVER** | Write-only lifecycle; preserve state only |
| `secretvault_secret.fingerprint` | Yes | No | Yes | SHA256 drift digest |
| `secretvault_secret.version` | Yes | No | Yes | Immutable version counter |
| `secretvault_provider_integration.credentials` | Yes | **YES** | **NEVER** | Redacted upon return |

> [!WARNING]
> **Terraform State Security Boundary:**
> While SecretVault encrypts secrets in its database, the Terraform state file (`terraform.tfstate`) is outside SecretVault's encryption boundary. Remote state storage (S3 + SSE-KMS, GCS, Terraform Cloud) must be encrypted at rest and restricted via least-privilege IAM policies.

---

## Documentation

- [Architecture Guide](docs/ARCHITECTURE.md)
- [API Contract Matrix](docs/API_CONTRACT_MATRIX.md)
- [Security & Threat Model](docs/SECURITY.md)
- [Secret State Safety Deep-Dive](docs/SECRET_STATE_SAFETY.md)
- [Authentication & OIDC Workload Guide](docs/AUTHENTICATION.md)
- [Troubleshooting & Diagnostics](docs/TROUBLESHOOTING.md)
