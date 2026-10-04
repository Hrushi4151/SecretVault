# SecretVault Terraform Provider — Architecture

## 1. System Context & Overview

The SecretVault Terraform Provider allows platform and DevOps engineers to manage SecretVault infrastructure resources using declarative HashiCorp Configuration Language (HCL).

```
   ┌─────────────────────────────────────────────────────────┐
   │                  Terraform CLI / CI/CD                  │
   └────────────────────────────┬────────────────────────────┘
                                │ (gRPC via terraform-plugin-framework)
   ┌────────────────────────────▼────────────────────────────┐
   │             SecretVault Terraform Provider              │
   │  ┌───────────────────────────┐ ┌──────────────────────┐ │
   │  │  Resources & DataSources  │ │  Redaction & Diags   │ │
   │  └─────────────┬─────────────┘ └──────────────────────┘ │
   │                │                                        │
   │  ┌─────────────▼──────────────────────────────────────┐ │
   │  │   Hardened HTTP Client (TLS, Retries, Backoff)     │ │
   │  └─────────────────────────┬──────────────────────────┘ │
   └────────────────────────────┼────────────────────────────┘
                                │ (HTTPS / JSON REST API)
                                │ (Bearer Auth / X-Workspace-ID)
   ┌────────────────────────────▼────────────────────────────┐
   │             SecretVault Backend Control Plane           │
   │                                                         │
   │  - Auth & RBAC Authorization                            │
   │  - Tenant Isolation (Workspace -> Project -> Env)       │
   │  - Envelope Encryption & Key Management (KMS / DEKs)    │
   │  - Immutable Secret Version Engine                      │
   │  - Audit Logging & JIT Access Control                   │
   └─────────────────────────────────────────────────────────┘
```

---

## 2. Architectural Invariants

1. **Backend Authoritative Control**:
   - The Terraform provider is an API client. It **never** bypasses backend authorization, RBAC rules, or tenant boundaries.
   - Database tables, Redis caches, and envelope encryption keys are never directly accessed.

2. **No Second Authorization System**:
   - Terraform configuration defines desired state. The SecretVault backend validates permissions for every operation.

3. **Zero Plaintext in Routine Read**:
   - Routine `terraform refresh`, `terraform plan`, and `terraform apply` operations invoke the metadata APIs (`GET .../secrets/{id}`) which return names, versions, content types, and timestamps without plaintext.
   - The `/reveal` API endpoint is strictly reserved for runtime workload execution and explicit human emergency access.

4. **Multi-Tenant Hierarchy Enforcement**:
   - Every resource explicitly requires `workspace_id`, `project_id`, and `environment_id` where applicable.
   - The HTTP client transmits matching `X-Workspace-ID` request headers to ensure complete cross-tenant boundary validation.

---

## 3. Package Structure

- `main.go`: Provider binary entrypoint and CLI flag parser.
- `internal/provider/`: Provider registration, schema configuration, and client initialization.
- `internal/client/`: Production-grade HTTP client with TLS, exponential backoff, rate limiting (`Retry-After`), and authentication handlers.
- `internal/resources/`: Terraform Resource implementations (`Project`, `Environment`, `Secret`, `MachineIdentity`, `ProviderIntegration`).
- `internal/datasources/`: Terraform Data Source implementations (`Workspace`, `Project`, `Environment`, `Secret`).
- `internal/validators/`: Schema attribute regex validators.
- `internal/diagnostics/`: Error sanitization and token redaction helpers.
