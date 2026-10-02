# SecretVault — Provider Integration Framework & Deployment Platform Guide

> **Phase:** Phase 7 — Provider Integration Framework  
> **Role:** Member 2 — Backend / Provider Integration Owner  
> **Status:** Production-Ready Baseline  
> **Target Providers:** Vercel, Render (Extensible SPI for AWS, Azure, GCP, Kubernetes, GitHub, GitLab, Cloudflare, Railway, Fly.io)

---

## 1. Executive Summary & Mission

The **Provider Integration Framework** allows SecretVault to securely and seamlessly integrate with external cloud, platform, and deployment providers. External deployment providers (such as Vercel and Render) require environment variables and configuration secrets to execute builds, serverless functions, and containerized workloads.

Instead of manually copy-pasting plaintext secrets across developer consoles, SecretVault serves as the single source of truth and securely syncs encrypted secrets to external provider APIs on-demand with zero plaintext persistence.

```
+-----------------------------------------------------------------------+
|                             SecretVault                               |
|        (Envelope Encryption, RBAC, Scoped Workspaces/Environments)    |
+-----------------------------------------------------------------------+
                                    │
                                    ▼
+-----------------------------------------------------------------------+
|                    Provider Integration Framework                     |
|  - Encrypted Credentials (AES-256-GCM + AAD Binding)                  |
|  - Normalized Capabilities & Error Abstraction                        |
|  - Tenant-Isolated Resource & Environment Mappings                    |
|  - In-Memory Secret Zeroization & Canary Leak Protection              |
+-----------------------------------------------------------------------+
            │                                           │
            ▼                                           ▼
+-----------------------+                   +-----------------------+
|  Vercel Adapter (SPI) |                   |  Render Adapter (SPI) |
+-----------------------+                   +-----------------------+
            │ (TLS / REST)                              │ (TLS / REST)
            ▼                                           ▼
+-----------------------+                   +-----------------------+
|    Vercel REST API    |                   |    Render REST API    |
| (Projects, Envs, Vars)|                   | (Services, Envs, Vars)|
+-----------------------+                   +-----------------------+
```

---

## 2. Core Domain Model

### 2.1 `ProviderIntegration` Entity
Represents an authenticated connection between a SecretVault workspace and a third-party platform provider.

| Field | Type | Description |
|---|---|---|
| `id` | `UUID` | Primary key identifier. |
| `workspaceId` | `UUID` | Workspace tenant ownership boundary. |
| `providerType` | `ProviderType` | Enum: `VERCEL`, `RENDER` (Extensible). |
| `displayName` | `String` | Human-readable label (e.g., `"Vercel Production Team"`). |
| `status` | `ProviderIntegrationStatus` | `ACTIVE`, `DISABLED`, `ERROR`, `VALIDATING`, `REVOKED`. |
| `configuration` | `String` (JSON) | Non-sensitive provider config (e.g. `teamId`, `slug`, `region`). |
| `encryptedCredentialReference`| `String` (JSON) | AES-256-GCM encrypted payload (`ciphertextBase64`, `ivBase64`, `authTagBase64`, `keyVersion`). |
| `redactedCredentialHint` | `String` | Safe display hint (e.g., `"••••••••••••5ab1"`). |
| `createdBy` | `UUID` | User ID of the administrator who configured the integration. |
| `createdAt` / `updatedAt` | `Instant` | Timestamps for auditability and caching. |
| `lastValidatedAt` | `Instant` | Timestamp of the last successful provider handshake. |
| `lastErrorAt` | `Instant` | Timestamp of the last connection failure. |
| `lastErrorCode` | `String` | Normalized error code (`PROVIDER_AUTHENTICATION_FAILED`, etc.). |
| `metadata` | `String` (JSON) | Extensible metadata bag. |

### 2.2 `ProviderResourceMapping` Entity
Maps a SecretVault project and environment to a specific external provider project/service and target environment tier.

| Field | Type | Description |
|---|---|---|
| `id` | `UUID` | Primary key identifier. |
| `workspaceId` | `UUID` | Tenant isolation boundary. |
| `integrationId` | `UUID` | Foreign key referencing parent `provider_integrations`. |
| `projectId` | `UUID` | SecretVault internal project ID. |
| `environmentId` | `UUID` | SecretVault internal environment ID. |
| `providerResourceType` | `String` | Provider resource kind (e.g., `PROJECT` for Vercel, `SERVICE` for Render). |
| `providerResourceId` | `String` | External provider ID (e.g., `prj_12345` or `srv-abc1234`). |
| `providerResourceName` | `String` | Human-readable external resource name. |
| `providerEnvironment` | `String` | External environment target (e.g., `production`, `preview`, `development`). |
| `autoSyncOnSecretChange` | `boolean` | Flag for future sync triggers (Phase 8). |
| `metadata` | `String` (JSON) | Adapter-specific mapping metadata. |

---

## 3. Credential Security Architecture

Provider credentials (API tokens, personal access tokens, service account keys) represent high-value administrative assets. SecretVault enforces a strict security envelope:

### 3.1 Credential Lifecycle
```
User Enters Token in UI / API (Write-Only)
                    │
                    ▼
Validate Token with Provider API (Network Handshake)
                    │
                    ▼
Encrypt with AES-256-GCM + AAD Binding (workspaceId:providerType:id)
                    │
                    ▼
Persist Encrypted JSON + Generate Redacted Hint (••••••••••••5ab1)
                    │
                    ▼
[ AT REST: Zero Plaintext in Database Columns ]
                    │
                    ▼
On Sync / Discovery Request: Decrypt Ephemeral Key in RAM
                    │
                    ▼
Execute TLS REST Call with Provider
                    │
                    ▼
Discard & Zeroize RAM Byte Arrays in `finally` Blocks
```

### 3.2 Security Guarantees
1. **No Plaintext in Database:** The `encrypted_credential_ref` column contains only AES-256-GCM ciphertext, IV, and tag.
2. **Authenticated Additional Data (AAD) Context Binding:**
   $$\text{AAD} = \text{workspaceId} + ":" + \text{providerType} + ":" + \text{integrationId}$$
   If an attacker copies encrypted credentials between workspaces or integrations in the database, decryption fails immediately due to GCM tag verification.
3. **No Plaintext in API Responses:** All REST responses return `redactedCredentialHint` (e.g., `"••••••••••••5ab1"`). The plaintext credential field is strictly write-only on creation or update.
4. **No Plaintext in Logs & Telemetry:** Provider tokens and Authorization headers are stripped and masked across `AuditService`, `SecurityEvent` loggers, and HTTP client interceptors.
5. **Atomic Credential Replacement:** Updating an existing integration validates the new credential first before overwriting the active record, ensuring working configurations are never destroyed by typos.

---

## 4. Provider Implementation Details

### 4.1 Vercel Adapter (`VERCEL`)
- **API Base URL:** `https://api.vercel.com`
- **Authentication:** `Authorization: Bearer <token>`
- **Capabilities Supported:**
  - `VALIDATE_CONNECTION` — Calls `GET /v2/user`
  - `LIST_PROJECTS` — Calls `GET /v9/projects`
  - `GET_PROJECT` — Calls `GET /v9/projects/{id}`
  - `LIST_ENVIRONMENTS` — Returns standard targets: `development`, `preview`, `production`
  - `READ_SECRETS` / `LIST_SECRETS` — Calls `GET /v9/projects/{id}/env` (returns metadata only)
  - `WRITE_SECRETS` — Calls `POST /v10/projects/{id}/env` (creates) or `PATCH /v10/projects/{id}/env/{envId}` (updates existing variable)
  - `DELETE_SECRETS` — Calls `DELETE /v9/projects/{id}/env/{envId}`
  - `HEALTH_CHECK` — Calls `GET /v2/user`

### 4.2 Render Adapter (`RENDER`)
- **API Base URL:** `https://api.render.com/v1`
- **Authentication:** `Authorization: Bearer <token>`
- **Capabilities Supported:**
  - `VALIDATE_CONNECTION` — Calls `GET /owners`
  - `LIST_PROJECTS` (Services) — Calls `GET /services`
  - `GET_PROJECT` — Calls `GET /services/{id}`
  - `LIST_ENVIRONMENTS` — Returns service-level targets: `production`, `staging`, `preview`
  - `READ_SECRETS` / `LIST_SECRETS` — Calls `GET /services/{id}/env-vars` (metadata only)
  - `WRITE_SECRETS` — Calls `PUT /services/{id}/env-vars` (upserts environment variable array)
  - `DELETE_SECRETS` — Calls `DELETE /services/{id}/env-vars/{envVarKey}`
  - `HEALTH_CHECK` — Calls `GET /owners`

---

## 5. End-to-End User & Frontend Workflow

```
1. Admin selects Provider (e.g. Vercel)
         │
         ▼
2. Enters Display Name + Provider API Token (Write-Only)
         │
         ▼
3. Backend validates credentials directly with Vercel API
         │
         ▼
4. Backend encrypts token (AES-256-GCM + AAD) and persists integration
         │
         ▼
5. Admin browses available remote projects (Resource Discovery API)
         │
         ▼
6. Admin maps SecretVault Project & Environment -> Provider Project & Target
         │
         ▼
7. Admin pushes secret to provider:
   - SecretVault decrypts secret in RAM
   - Pushes to Vercel via TLS
   - Immediately wipes RAM
   - Emits safe audit record (PROVIDER_SECRET_PUSHED)
```

---

## 6. Access Control & RBAC Matrix

All provider operations enforce strict multi-tier permissions via `EffectiveAccessService`:

| Operation | Required Permission | Allowed Roles |
|---|---|---|
| View Integrations & Status | `INTEGRATION_VIEW` | `OWNER`, `ADMIN`, `DEVELOPER`, `VIEWER` |
| Create / Update / Delete Integration | `INTEGRATION_MANAGE` | `OWNER`, `ADMIN` |
| Validate Connection | `INTEGRATION_MANAGE` | `OWNER`, `ADMIN` |
| Discover Provider Resources | `INTEGRATION_VIEW` | `OWNER`, `ADMIN`, `DEVELOPER` |
| Create / Update / Delete Mappings | `INTEGRATION_MANAGE` | `OWNER`, `ADMIN` |
| Push / Sync / Delete Provider Secrets | `INTEGRATION_SYNC` | `OWNER`, `ADMIN`, `DEVELOPER` |

---

## 7. Audit Logging & Security Telemetry

Every provider management and secret push operation emits structured audit records:

- `PROVIDER_INTEGRATION_CREATED`
- `PROVIDER_INTEGRATION_UPDATED`
- `PROVIDER_INTEGRATION_DELETED`
- `PROVIDER_INTEGRATION_VALIDATED`
- `PROVIDER_MAPPING_CREATED`
- `PROVIDER_MAPPING_UPDATED`
- `PROVIDER_MAPPING_REMOVED`
- `PROVIDER_SECRET_PUSHED`
- `PROVIDER_SECRET_DELETED`
- `PROVIDER_OPERATION_FAILED`

All audit metadata is passed through `SafeEventMetadataSanitizer` to guarantee that no tokens, credentials, or secret plaintexts are ever persisted in audit logs or security telemetry streams.
