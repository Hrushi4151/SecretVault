# SecretVault — Comprehensive Security Architecture & Threat Model

## 1. STRIDE Threat Model & Mitigations

| Threat Category | Potential Attack Vector | SecretVault Defense / Mitigation |
|---|---|---|
| **Spoofing** | Forged user or machine identity token | Cryptographically signed JWT tokens with short TTLs; OIDC workload federation with issuer validation; MFA/WebAuthn for administrative actions. |
| **Tampering** | Modifying ciphertext or database records directly | AES-256-GCM authenticated encryption with 128-bit auth tags (AEAD); HMAC integrity checks on audit records. |
| **Repudiation** | Actor denies creating, deleting, or revealing a secret | Non-repudiable, append-only `audit_logs` storing actor identity, timestamp, IP, request ID, and action outcome. |
| **Information Disclosure** | Secret leaks via database dump, logs, APM, or errors | Envelope encryption (AES-256-GCM); zero plaintext stored in PostgreSQL; `GlobalExceptionHandler` sanitizes errors; log scrubbers strip sensitive headers. |
| **Denial of Service** | Flooding reveal or sync endpoints | Redis sliding-window rate limiting; async worker queue isolation for heavy sync jobs; connection pooling. |
| **Elevation of Privilege** | Cross-tenant IDOR or role tampering | Multi-tenant tenant verification at repository and service layer; granular RBAC checks; JIT time-bound access. |

---

## 2. Multi-Tenant Authorization & RBAC

```text
Identity (User / Service Account / OIDC Workload)
  └── Organization Membership Verification
        └── Workspace Access Check
              └── Project Access Check
                    └── Environment Scope (Dev, Staging, Prod)
                          └── Role & Granular Permission Check
```

### Initial Organization Roles & Phase 2 Capabilities:
- **OWNER:** Full account, billing, security policy, workspace/project/environment lifecycle control (`canManageWorkspace()`, `canCreateProjects()`, `canManageProjects()`, `canManageEnvironments()`, `canWriteSecrets()`).
- **ADMIN:** Workspace administration, project/environment management, team invitations, and policy configuration (`canManageWorkspace()`, `canCreateProjects()`, `canManageProjects()`, `canManageEnvironments()`, `canWriteSecrets()`).
- **DEVELOPER:** Can create projects and manage secrets in allowed environments (`canCreateProjects()`, `canWriteSecrets()`). Read-only access to workspaces and projects; cannot delete projects or create/delete custom environments.
- **VIEWER:** Strict read-only access to workspaces, projects, environments, and secret metadata (`canReadSecrets()`). Cannot mutate projects, environments, or secrets.

### Granular Permissions:
- `project.read` — View projects and their environment summaries.
- `project.create` — Provision new projects with default environments (`development`, `staging`, `production`).
- `project.update` / `project.delete` — Modify or delete projects (OWNER / ADMIN only).
- `environment.read` — View environment lists and configuration details.
- `environment.create` / `environment.update` / `environment.delete` — Manage custom environment tiers and protection status (OWNER / ADMIN only).
- `secret.read` — View secret names, descriptions, tags, and sync status.
- `secret.reveal` — Decrypt and view plaintext secret material.
- `secret.create` / `secret.update` / `secret.delete` — Modify secrets.
- `secret.rotate` — Execute manual or scheduled rotation.
- `integration.manage` — Connect/disconnect infrastructure providers.
- `sync.execute` — Trigger synchronization jobs.
- `audit.read` — Inspect immutable audit logs.
- `security.manage` — Configure policies, JIT settings, and IP allowlists.

### Hierarchical Authorization & Anti-Mass-Assignment Invariants:
1. **Server-Side Authoritative Authorization:** Frontend claims of role, tenant, or membership are never trusted. All access decisions evaluate the database-backed `WorkspaceMembership`.
2. **Anti-Mass-Assignment:** Request DTOs strictly limit modifiable fields to `name`, `slug`, `description`, `envType`, `isProtected`, and `status`. Critical fields (`id`, `workspaceId`, `projectId`, `createdBy`, `createdAt`) are exclusively generated and assigned server-side.
3. **Cross-Tenant IDOR Guard:** All database lookups for projects and environments enforce `findByIdAndWorkspaceId` and `findByIdAndProjectId`. Querying a resource under a mismatched parent ID returns `404 RESOURCE_NOT_FOUND`.

---

## 3. Envelope Encryption Architecture (AES-256-GCM)

```text
[Plaintext Secret Payload] + [Unique 256-bit DEK] ──(AES-256-GCM)──> [Ciphertext] + [128-bit Tag] + [96-bit IV]
                                     │
   [Master KEK (KMS / HSM)] ─────────┴─(Encrypt DEK)──> [Encrypted DEK]
```

- **Key Encryption Key (KEK / Master Key):** Managed in KMS (AWS KMS, GCP Cloud KMS, Vault HSM) or provided via secure environment key.
- **Data Encryption Key (DEK):** Cryptographically random 256-bit key uniquely generated for every secret version.
- **Initialization Vector (IV):** Secure random 96-bit nonce generated per encryption operation.
- **Integrity Tag:** 128-bit GCM authentication tag. Decryption strictly aborts if ciphertext is altered.

---

## 4. Secret Reveal Safeguards

- **Default State:** Plaintext is masked (`••••••••`).
- **Explicit User Action:** Decryption occurs only upon deliberate user request (`POST /api/v1/secrets/{id}/reveal`).
- **Step-Up Authentication:** Optional policy requiring MFA re-verification before revealing production secrets.
- **No Secret Transmission in:**
  - HTTP URLs or query parameters
  - Browser console or analytics payloads
  - Server application logs or APM spans
  - AI prompts or external LLM APIs

---

## 5. Just-In-Time (JIT) Temporary Access Workflow

```text
Developer Requests JIT Access (Reason + Duration: e.g. 1 hour)
  └── Admin / Dual Approval Workflow
        └── Ephemeral Role Grant Issued in Redis
              └── Developer Performs Authorized Production Task
                    └── Time Window Expires -> Automatic Revocation & Audit Flag
```

---

## 6. AI Intelligence Security Boundaries

1. **Zero Plaintext Secrets:** Plaintext secret values are NEVER sent to external LLMs or AI services.
2. **Sanitized Context Only:** Only sanitized metadata (key names, rotation age, sync state, failure logs) is provided.
3. **Advisory Role:** AI suggestions are non-destructive and strictly advisory.
4. **Resilience:** The core secret engine functions without interruption if the AI service is disabled.
