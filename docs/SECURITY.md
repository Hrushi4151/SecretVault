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

## 2. Multi-Tier Authorization & Access Scoping (Role $\neq$ Scope) [IMPLEMENTED]

```text
Identity (User / Service Account / OIDC Workload)
  └── Organization Membership Verification
        └── Workspace Membership Check (OWNER | ADMIN | DEVELOPER | VIEWER)
              └── Project Access Scope (ProjectAccess Grant)
                    └── Environment Access Scope (EnvironmentAccess Grant: READ | WRITE | MANAGE)
                          └── Effective Permission Evaluation
```

### Effective Permission Invariant:
$$\text{Effective Permission} = \text{Workspace Role} \cap \text{Project Scope} \cap \text{Environment Scope} \cap \text{Security Policies}$$

- **Child Scopes Restrict Privilege:** A child grant (ProjectAccess or EnvironmentAccess) may narrow down capabilities (e.g. restrict a workspace DEVELOPER to READ in Production).
- **Elevation Prevented:** A child grant can never elevate permissions beyond the parent workspace role (e.g. a VIEWER cannot write in Production even if an environment record says WRITE).

### Workspace Roles & Capabilities:
- **OWNER:** Full account, billing, security policy, workspace/project/environment lifecycle control (`canManageWorkspace()`, `canCreateProjects()`, `canManageProjects()`, `canManageEnvironments()`, `canWriteSecrets()`).
- **ADMIN:** Workspace administration, project/environment management, team invitations, and policy configuration (`canManageWorkspace()`, `canCreateProjects()`, `canManageProjects()`, `canManageEnvironments()`, `canWriteSecrets()`).
- **DEVELOPER:** Can create projects and manage secrets in allowed environments (`canCreateProjects()`, `canWriteSecrets()`). Read-only access to workspaces and projects; cannot delete projects or create/delete custom environments.
- **VIEWER:** Strict read-only access to workspaces, projects, environments, and secret metadata (`canReadSecrets()`). Cannot mutate projects, environments, or secrets.

### Last Owner Safeguard:
- A workspace must never be left ownerless.
- Demoting (`updateMemberRole`) or removing (`removeMember`) the final remaining `OWNER` is rejected with `400 BAD_REQUEST`.

---

## 3. Workspace Invitations & Token Security [IMPLEMENTED]

1. **Cryptographically Secure Random Tokens:** Invitations generate high-entropy single-use tokens (`inv_...`).
2. **SHA-256 One-Way Hash Storage:** Raw tokens are never stored in the database. Only the cryptographic SHA-256 hash is persisted in `workspace_invitations.token_hash`.
3. **Single-Use Acceptance:** Upon acceptance, the invitation status transitions from `PENDING` to `ACCEPTED` and stores `accepted_at`. Subsequent attempts to accept the token are rejected with `400 BAD_REQUEST`.
4. **Expiration & Revocation:** Expired tokens (`expires_at < now()`) or revoked invitations (`status = REVOKED`) cannot be accepted.
5. **No Token Leaks:** Raw invitation tokens are returned exactly once upon creation and are NEVER recorded in logs, stack traces, or audit metadata.

---

## 4. Envelope Encryption Architecture (AES-256-GCM) [PHASE 3 PLANNED]

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

## 5. Secret Reveal Safeguards [PHASE 3 PLANNED]

- **Default State:** Plaintext is masked (`••••••••`).
- **Explicit User Action:** Decryption occurs only upon deliberate user request (`POST /api/v1/secrets/{id}/reveal`).
- **Step-Up Authentication:** Optional policy requiring MFA re-verification before revealing production secrets.
- **No Secret Transmission in:**
  - HTTP URLs or query parameters
  - Browser console or analytics payloads
  - Server application logs or APM spans
  - AI prompts or external LLM APIs

---

## 6. AI Intelligence Security Boundaries

1. **Zero Plaintext Secrets:** Plaintext secret values are NEVER sent to external LLMs or AI services.
2. **Sanitized Context Only:** Only sanitized metadata (key names, rotation age, sync state, failure logs) is provided.
3. **Advisory Role:** AI suggestions are non-destructive and strictly advisory.
4. **Resilience:** The core secret engine functions without interruption if the AI service is disabled.
