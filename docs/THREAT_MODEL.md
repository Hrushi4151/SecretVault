# SecretVault — System Threat Model (STRIDE Framework)

**Date:** 2026-09-20  
**Version:** 1.0.0 (Phase 5.6 Baseline)  

---

## 1. System Assets
1. **Plaintext Secret Payloads:** API keys, database credentials, certificates, tokens.
2. **Data Encryption Keys (DEKs):** Ephemeral 256-bit AES keys encrypting secret versions.
3. **Key Encryption Keys (KEKs):** Master wrapping keys managed by KMS providers.
4. **Authentication Credentials:** User password hashes (BCrypt) and refresh tokens (SHA-256).
5. **Authorization Decisions & Lineage:** Granular grants, JIT records, role bindings.
6. **Audit Logs & Attestation Ledgers:** Immutable compliance records.

---

## 2. Threat Actors & Trust Boundaries
- **Untrusted External Network:** Public internet communicating via TLS/HTTPS.
- **Malicious External Attacker:** Probing public endpoints (`/api/v1/auth/*`, `/actuator/health`).
- **Compromised User Identity:** Legitimate user whose credentials/JWT was intercepted.
- **Malicious Insider / Rogue Tenant:** Authenticated member attempting privilege escalation or cross-tenant access.
- **Compromised Storage / Database Admin:** Direct database inspection (mitigated by AES-GCM envelope encryption).

---

## 3. STRIDE Threat Analysis & Mitigations

### 1. Spoofing Identity
- **Threat:** Forging JWT tokens, tampering with user ID/role claims, or replay attacks.
- **Mitigation:**
  - HMAC-SHA256 signature verification using server-side secret key (`Keys.hmacShaKeyFor`).
  - Authorization derived exclusively from database state (`EffectiveAccessService`), never trusting client claims.
  - Refresh tokens stored as SHA-256 hashes with automatic rotation.

### 2. Tampering with Data
- **Threat:** Modifying encrypted secret ciphertext in PostgreSQL or swapping ciphertexts across environments.
- **Mitigation:**
  - AES-256-GCM 128-bit authentication tags verify integrity.
  - Authenticated Additional Data (AAD) binds `secretId:environmentId:versionNumber` to the ciphertext.
  - Modifying ciphertext or AAD causes immediate GCM decryption failure.

### 3. Repudiation
- **Threat:** User claims they did not reveal a secret, approve a JIT elevation, or create a grant.
- **Mitigation:**
  - Append-only `AuditService` logs every privileged action with actor ID, timestamp, workspace ID, resource target, and outcome.
  - Zero plaintext secrets logged in audit trails.

### 4. Information Disclosure
- **Threat:** Secret plaintext leaking in error stack traces, server logs, URLs, or browser local storage.
- **Mitigation:**
  - `GlobalExceptionHandler` sanitizes all HTTP error responses.
  - Zero `logger.info/debug` calls contain secret plaintext.
  - Secret reveal endpoints operate strictly via POST with `Cache-Control: no-store`.

### 5. Denial of Service (DoS)
- **Threat:** Flooding JIT elevation requests or creating massive review campaigns.
- **Mitigation:**
  - Max duration cap (240 minutes) on JIT requests.
  - Duplicate pending request conflict detection (`existsPendingRequest` -> `409 Conflict`).
  - Database pagination with capped page sizes (max 100).

### 6. Elevation of Privilege
- **Threat:** Developer approving their own JIT request, or Project admin granting workspace-wide admin access.
- **Mitigation:**
  - Anti-Self-Approval guard (`request.getUserId().equals(approverUserId)` -> `403 Forbidden`).
  - Anti-Self-Review guard in Access Reviews.
  - Strict hierarchical scope containment (`EffectiveAccessService.checkPermission`).

---

## 4. Residual Risks & Future Roadmap
- **Session Revocation Latency:** Short-lived JWTs expire in 24 hours. Advanced roadmap includes real-time Redis token blacklist.
- **KMS Availability:** LocalDev provider must be substituted with cloud KMS in multi-region production clusters.
