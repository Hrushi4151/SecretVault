# SecretVault — Security Architecture Specification

**Date:** 2026-09-20  
**Version:** 1.0.0 (Phase 5.6 Baseline)  

---

## 1. High-Level Architecture Overview

SecretVault is designed around a **Defense-in-Depth, Zero Standing Privilege (ZSP)** control plane architecture:

```text
[ Client (Browser / CLI / SDK) ]
              │ (TLS / HTTPS)
              ▼
    [ SecurityConfig (Stateless Spring Security) ]
              │
              ├─► CorrelationIdFilter (X-Correlation-ID tracing)
              └─► JwtAuthenticationFilter (RFC-7519 HMAC-SHA256)
                      │
                      ▼
       [ Authoritative Domain Controllers ]
                      │
                      ▼
    [ EffectiveAccessService (9-Step Pipeline) ]
      1. Tenant / Workspace Boundary Verification
      2. Workspace Membership Active Check
      3. Project Boundary Verification
      4. Environment Boundary & Invariant Check (Branch Protection)
      5. Protected Environment Policy (Production Protection)
      6. Standing Workspace Role Baseline
      7. Scoped Project / Environment Access Grants
      8. Granular Resource-Level Grants (access_grants)
      9. Active JIT Temporary Elevation (jit_access_requests)
                      │
                      ▼
[ AesGcmEnvelopeEncryptionService (AES-256-GCM + KMS Wrap) ]
                      │
                      ▼
    [ PostgreSQL Encrypted Storage + Audit Trail ]
```

---

## 2. Cryptographic Envelope Encryption Engine

Every secret version is encrypted with an independent, single-use Data Encryption Key (DEK):
- **Algorithm:** `AES/GCM/NoPadding` (256-bit key)
- **Initialization Vector (IV):** 96-bit cryptographically secure random bytes (`SecureRandom`)
- **Authentication Tag:** 128-bit GCM MAC
- **Authenticated Additional Data (AAD):** `secretId:environmentId:versionNumber` (guarantees cryptographic identity binding and prevents ciphertext swapping across environments).
- **Key Wrapping:** DEK is wrapped by a Key Encryption Key (KEK) managed by `KmsKeyProvider`.
- **Memory Hygiene:** Plaintext DEK byte arrays are zeroized (`Arrays.fill(dekBytes, (byte) 0)`) in `finally` blocks immediately after encryption/decryption.

---

## 3. Just-In-Time (JIT) Temporary Privilege Elevation

- **Time-to-Live:** Configurable between 5 and 240 minutes.
- **Expiry Enforcement:** Real-time clock boundary evaluation (`clock.instant() < expiresAt`) in `EffectiveAccessService` guarantees immediate expiration without relying on scheduled background jobs.
- **Anti-Self-Approval:** Server-side guard prevents requesters from approving their own elevations.
- **Concurrency Control:** Pessimistic write locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) eliminates race conditions on concurrent approval/rejection/cancellation/revocation.

---

## 4. Access Reviews & Compliance Certification

- **Snapshot Immutability:** Initiating a review campaign captures a point-in-time snapshot of effective permissions.
- **Targeted Remediation:** Revoking an item targets the exact underlying grant or JIT elevation without deleting broader standing workspace memberships.
- **Attestation Ledger:** Finalizing a campaign generates an immutable compliance record sealing total items reviewed, decisions recorded, and certifier identity.

---

## 5. Provider Credential Protection & In-Memory Synchronization (Phase 7)

- **Envelope Encryption for Third-Party Tokens:** External provider credentials (e.g. Vercel, Render tokens) are encrypted with `AES/GCM/NoPadding` (256-bit key) using `ProviderCredentialService`.
- **Cryptographic AAD Context Binding:**
  $$\text{AAD} = \text{workspaceId} + ":" + \text{providerType} + ":" + \text{integrationId}$$
  Decryption attempts with mismatched workspace or integration IDs fail GCM tag validation immediately, preventing cross-tenant ciphertext translocation attacks.
- **Write-Only Credential Entry & Redaction:** API responses return masked hints (e.g., `"••••••••••••5ab1"`). Plaintext tokens are strictly write-only upon creation or atomic replacement.
- **In-Memory Secret Push & Memory Hygiene:** When pushing secrets to an external provider:
  1. SecretVault retrieves the encrypted secret version and decrypts the DEK in memory.
  2. The plaintext secret payload is decrypted into memory.
  3. The HTTPS request is dispatched to the provider over TLS.
  4. In `finally` blocks, memory byte references are cleared and zeroized.
  5. No secret plaintexts or provider tokens are written to disk, database columns, audit logs, or HTTP response payloads.
- **Canary Security Verification:** Continuous automated unit tests verify that canary secrets (`SUPER_SECRET_CANARY_123`) never leak into logs, exceptions, audit records, or API responses.

---

## 6. Sync Engine & Drift Detection Security Architecture (Phase 8)

### 6.1 Plaintext-Free Drift Detection & State Comparison
- **Fingerprint-Based Comparison**: Desired and provider states are represented via deterministic SHA-256 fingerprints.
- **Ephemeral In-Memory Handling**: When providers do not supply remote hashes, secrets are decrypted into memory solely for byte comparison and zeroized immediately in `finally` blocks.
- **Zero Plaintext Persistence**: Plaintext secret values, provider authorization headers, and raw tokens are never written to `drift_records`, `sync_jobs`, `sync_operations`, audit logs, or security telemetry payloads.

### 6.2 Conservative Deletion Policy
- To prevent operational outages, unexpected provider-side secrets (`EXTRA_IN_PROVIDER`) are flagged as drift records and security findings but are **never deleted automatically** by the Sync Engine unless an explicit destructive reconciliation policy is specified with authorized permissions.

### 6.3 Concurrency & Lock Safety
- Sync executions enforce mutex locking on `(workspaceId, mappingId)`.
- Prevents concurrent race conditions against the same provider target.
- Database locks are never held across external HTTP provider API calls.

### 6.4 Security Telemetry & Finding Automation
- High-severity drift (e.g., `VALUE_MISMATCH` in production, `PERMISSION_DENIED` on active integrations) automatically registers a `SecurityFinding` in the Phase 6 Security Intelligence Engine.
- Emits sanitized `DRIFT_DETECTED`, `DRIFT_RESOLVED`, `SYNC_STARTED`, `SYNC_COMPLETED`, and `SYNC_FAILED` events.

