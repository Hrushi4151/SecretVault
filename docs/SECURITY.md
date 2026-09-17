# SecretVault — Comprehensive Security Architecture & Threat Model

## 1. STRIDE Threat Model & Mitigations

| Threat Category | Potential Attack Vector | SecretVault Defense / Mitigation |
|---|---|---|
| **Spoofing** | Forged user or machine identity token | Cryptographically signed JWT tokens with short TTLs; OIDC workload federation with issuer validation; MFA/WebAuthn for administrative actions. |
| **Tampering** | Modifying ciphertext or database records directly | AES-256-GCM authenticated encryption with 128-bit auth tags (AEAD); HMAC integrity checks on audit records. |
| **Repudiation** | Actor denies creating, deleting, or revealing a secret | Append-only `audit_logs` storing actor identity, timestamp, IP, request ID, and action outcome. |
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

## 4. Envelope Encryption Architecture (AES-256-GCM) [IMPLEMENTED]

```text
[Plaintext Secret Payload] + [Unique 256-bit DEK] ──(AES-256-GCM + AAD Binding)──> [Ciphertext] + [128-bit Tag] + [96-bit IV]
                                     │
   [Master KEK (KMS / HSM)] ─────────┴─(Key Wrapping)─────────────────────────> [Encrypted DEK]
```

- **Key Encryption Key (KEK / Master Key):** Managed through the pluggable `KmsKeyProvider` SPI (Local AES-KW/GCM wrapping in development; AWS KMS / HashiCorp Vault in production).
- **Data Encryption Key (DEK):** Cryptographically secure, high-entropy 256-bit AES key generated uniquely per secret version via `SecureRandom`. DEKs are never reused or persisted in plaintext.
- **Initialization Vector (IV):** Secure random 96-bit nonce generated per encryption operation.
- **Integrity Tag:** 128-bit GCM authentication tag. Decryption strictly fails closed if ciphertext or authentication tag is tampered with.
- **Cryptographic AAD Context Binding:** Authenticated Additional Data binds `secretId:environmentId:versionNumber` into the AEAD calculation. Ciphertexts cannot be transplanted across secrets, environments, or version slots.

---

## 5. Secret Reveal Safeguards [IMPLEMENTED]

- **Default Masked State:** Standard secret retrieval endpoints (`GET /secrets`, `GET /secrets/{id}`) and listing tables return only sanitized metadata (`SecretMetadataResponse`). Plaintext values are completely absent.
- **Explicit In-Memory Decryption:** Decryption occurs strictly upon an explicit `POST /api/v1/.../secrets/{id}/reveal` request. The plaintext is decrypted transiently in JVM memory and immediately handed to the client response.
- **Anti-Caching HTTP Headers:** Reveal responses include `Cache-Control: no-store, no-cache, must-revalidate, private` and `Pragma: no-cache` headers, preventing intermediate proxies, CDNs, or browser disk caches from recording plaintext.
- **Append-Only Audit Trail:** Every reveal event generates an immutable `SECRET_REVEALED` row in `audit_logs` capturing actor identity, workspace, IP address, request ID, and timestamp.
- **Zero Leakage Invariant:** Plaintext secrets, raw DEKs, and unencrypted master keys are NEVER transmitted in:
  - HTTP URLs or query parameters
  - Browser console logs, localStorage, or sessionStorage
  - Application error responses (`RFC-7807` standard error model)
  - Server application logs, APM spans, or metrics
  - AI prompts or external LLM service boundaries


---

## 6. AI Intelligence Security Boundaries

1. **Zero Plaintext Secrets:** Plaintext secret values are NEVER sent to external LLMs or AI services.
2. **Sanitized Context Only:** Only sanitized metadata (key names, rotation age, sync state, failure logs) is provided.
3. **Advisory Role:** AI suggestions are non-destructive and strictly advisory.
4. **Resilience:** The core secret engine functions without interruption if the AI service is disabled.
