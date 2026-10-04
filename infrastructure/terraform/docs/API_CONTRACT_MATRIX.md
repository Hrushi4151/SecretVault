# SecretVault Terraform Provider — API Contract Matrix

This matrix documents the exact mapping between every Terraform resource/data source and the underlying SecretVault Spring Boot backend REST endpoints, DTOs, authorization rules, tenant scoping, and audit logs.

---

## 1. Resources & Data Sources Contract Matrix

| Terraform Entity | Type | HTTP Method | Backend API Endpoint | Request DTO | Response DTO | Auth & Scope | Audit Event |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `secretvault_workspace` | Data Source | `GET` | `/api/v1/workspaces/{id}` | None | `WorkspaceResponse` | Bearer Token / `X-Workspace-ID` | Read telemetry |
| `secretvault_project` | Resource (Create) | `POST` | `/api/v1/workspaces/{workspaceId}/projects` | `CreateProjectRequest` (`name`, `slug`, `description`) | `ProjectResponse` | Bearer Token / Admin / Member | `PROJECT_CREATED` |
| `secretvault_project` | Resource (Read) | `GET` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}` | None | `ProjectResponse` | Scoped by Workspace ID | Read telemetry |
| `secretvault_project` | Resource (Update) | `PATCH` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}` | `UpdateProjectRequest` (`name`, `description`) | `ProjectResponse` | Bearer Token / Workspace Admin | `PROJECT_UPDATED` |
| `secretvault_project` | Resource (Delete) | `DELETE` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}` | None | 204 No Content | Bearer Token / Workspace Admin | `PROJECT_DELETED` |
| `secretvault_project` | Data Source | `GET` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}` | None | `ProjectResponse` | Scoped by Workspace ID | Read telemetry |
| `secretvault_environment` | Resource (Create) | `POST` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments` | `CreateEnvironmentRequest` (`name`, `slug`, `type`, `isProtected`) | `EnvironmentResponse` | Bearer Token / Workspace Admin | `ENVIRONMENT_CREATED` |
| `secretvault_environment` | Resource (Read) | `GET` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}` | None | `EnvironmentResponse` | Scoped by Project & Workspace | Read telemetry |
| `secretvault_environment` | Resource (Update) | `PATCH` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}` | `UpdateEnvironmentRequest` (`name`, `type`, `isProtected`) | `EnvironmentResponse` | Bearer Token / Workspace Admin | `ENVIRONMENT_UPDATED` |
| `secretvault_environment` | Resource (Delete) | `DELETE` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}` | None | 204 No Content | Bearer Token / Workspace Admin | `ENVIRONMENT_DELETED` |
| `secretvault_environment` | Data Source | `GET` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}` | None | `EnvironmentResponse` | Scoped by Project & Workspace | Read telemetry |
| `secretvault_secret` | Resource (Create) | `POST` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/secrets` | `CreateSecretRequest` (`name`, `value`, `contentType`, `comment`) | `SecretMetadataResponse` (**Zero Plaintext**) | Bearer Token / Developer / Admin | `SECRET_CREATED` |
| `secretvault_secret` | Resource (Read) | `GET` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/secrets/{secretId}` | None | `SecretMetadataResponse` (**Strictly Metadata**) | Scoped by Environment & Workspace | Read telemetry (NO Reveal) |
| `secretvault_secret` | Resource (Update) | `PATCH` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/secrets/{secretId}` | `UpdateSecretRequest` (`value`, `contentType`, `comment`) | `SecretMetadataResponse` (**Zero Plaintext**) | Bearer Token / Developer / Admin | `SECRET_VERSION_CREATED` |
| `secretvault_secret` | Resource (Delete) | `DELETE` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/secrets/{secretId}` | None | 204 No Content | Bearer Token / Admin | `SECRET_DELETED` |
| `secretvault_secret` | Data Source | `GET` | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/secrets/{secretId}` | None | `SecretMetadataResponse` (**Metadata Only**) | Scoped by Environment & Workspace | Read telemetry (NO Reveal) |
| `secretvault_machine_identity` | Resource (Create) | `POST` | `/api/v1/workspaces/{workspaceId}/machines` | `CreateMachineIdentityRequest` (`name`, `description`, `role`, `maxTokenTtlSeconds`) | `MachineIdentityResponse` | Bearer Token / Workspace Admin | `MACHINE_IDENTITY_CREATED` |
| `secretvault_machine_identity` | Resource (Read) | `GET` | `/api/v1/workspaces/{workspaceId}/machines/{id}` | None | `MachineIdentityResponse` | Scoped by Workspace ID | Read telemetry |
| `secretvault_machine_identity` | Resource (Update) | `PATCH` | `/api/v1/workspaces/{workspaceId}/machines/{id}` | `UpdateMachineIdentityRequest` (`name`, `description`, `role`, `enabled`) | `MachineIdentityResponse` | Bearer Token / Workspace Admin | `MACHINE_IDENTITY_UPDATED` |
| `secretvault_machine_identity` | Resource (Delete) | `DELETE` | `/api/v1/workspaces/{workspaceId}/machines/{id}` | None | 204 No Content | Bearer Token / Workspace Admin | `MACHINE_IDENTITY_DELETED` |
| `secretvault_provider_integration` | Resource (Create) | `POST` | `/api/v1/workspaces/{workspaceId}/integrations` | `CreateProviderIntegrationRequest` (`name`, `providerType`, `config`, `credentials`) | `ProviderIntegrationResponse` | Bearer Token / Workspace Admin | `INTEGRATION_CREATED` |
| `secretvault_provider_integration` | Resource (Read) | `GET` | `/api/v1/workspaces/{workspaceId}/integrations/{integrationId}` | None | `ProviderIntegrationResponse` | Scoped by Workspace ID | Read telemetry |
| `secretvault_provider_integration` | Resource (Update) | `PATCH` | `/api/v1/workspaces/{workspaceId}/integrations/{integrationId}` | `UpdateProviderIntegrationRequest` (`name`, `config`, `credentials`, `enabled`) | `ProviderIntegrationResponse` | Bearer Token / Workspace Admin | `INTEGRATION_UPDATED` |
| `secretvault_provider_integration` | Resource (Delete) | `DELETE` | `/api/v1/workspaces/{workspaceId}/integrations/{integrationId}` | None | 204 No Content | Bearer Token / Workspace Admin | `INTEGRATION_DELETED` |
| `secretvault_rotation_policy` | Resource (Create) | `POST` | `/api/v1/workspaces/{workspaceId}/rotation-policies` | `CreateRotationPolicyRequest` | `RotationPolicyResponse` | Bearer Token / Admin / Policy Admin | `ROTATION_POLICY_CREATED` |
| `secretvault_rotation_policy` | Resource (Read) | `GET` | `/api/v1/workspaces/{workspaceId}/rotation-policies/{policyId}` | None | `RotationPolicyResponse` | Scoped by Workspace ID | Read telemetry |
| `secretvault_rotation_policy` | Resource (Update) | `PUT` | `/api/v1/workspaces/{workspaceId}/rotation-policies/{policyId}` | `UpdateRotationPolicyRequest` | `RotationPolicyResponse` | Bearer Token / Admin / Policy Admin | `ROTATION_POLICY_UPDATED` |
| `secretvault_rotation_policy` | Resource (Delete) | `DELETE` | `/api/v1/workspaces/{workspaceId}/rotation-policies/{policyId}` | None | 204 No Content | Bearer Token / Admin / Policy Admin | `ROTATION_POLICY_DELETED` |
| Workload Authentication | Provider Auth | `POST` | `/api/v1/auth/oidc/token` or `/api/v1/oidc/auth/exchange` | `OidcTokenExchangeRequest` (`token`, `issuer`, `providerId`) | `OidcTokenResponse` (`accessToken`, `expiresIn`) | Public / Workload JWT | `OIDC_AUTH_SUCCESS` / `OIDC_AUTH_FAILURE` |

---

## 2. Supported External Provider Types

The `secretvault_provider_integration` resource supports the canonical backend `ProviderType` enumeration:

1. `VERCEL` — Vercel Project Environment Variables Sync
2. `RENDER` — Render Service & Environment Sync
3. `AWS` / `AWS_SECRETS_MANAGER` — AWS Secrets Manager & Parameter Store
4. `AZURE` — Azure Key Vault Sync
5. `GCP` — Google Cloud Secret Manager Sync
6. `KUBERNETES` — Kubernetes Secret Sync Operator
7. `GITHUB` — GitHub Actions Secret & Repository Sync
8. `GITLAB` — GitLab CI/CD Variable Sync
9. `CLOUDFLARE` — Cloudflare Pages / Workers Environment Sync
10. `RAILWAY` — Railway.app Deployment Sync
11. `FLY_IO` — Fly.io Apps Secret Sync
12. `CUSTOM` — Custom webhook & API destination

---

## 3. Error Code Handling & Diagnostics

| HTTP Status | Client Mapping | Terraform Diagnostic Behavior | Retry Behavior |
| :--- | :--- | :--- | :--- |
| **400 Bad Request** | `APIError` | Displays sanitized error message | Non-retryable |
| **401 Unauthorized** | `IsUnauthorized()` | Guides user to verify `SECRET_VAULT_TOKEN` / credentials | Non-retryable |
| **403 Forbidden** | `IsForbidden()` | Alerts user to missing RBAC permission or tenant boundary violation | Non-retryable |
| **404 Not Found** | `IsNotFound()` | In `Read()`: Removes resource from state (`RemoveResource`); In `Delete()`: No-op | Non-retryable |
| **409 Conflict** | `IsConflict()` | Alerts user that resource slug/name already exists | Non-retryable |
| **429 Too Many Requests** | `IsRateLimited()` | Automatically waits `Retry-After` header with exponential backoff | Retried up to `max_retries` |
| **502 / 503 / 504** | Transient Gateway | Retried with jittered exponential backoff for idempotent methods (`GET`, `PUT`, `DELETE`) | Retried up to `max_retries` |
