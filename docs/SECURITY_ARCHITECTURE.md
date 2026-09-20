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
