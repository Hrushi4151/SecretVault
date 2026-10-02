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
7. **Security Telemetry & Findings:** Structured security events, risk scores, and fingerprinted vulnerabilities.
8. **Provider Integration Credentials:** External platform API tokens (Vercel, Render) encrypted with AES-256-GCM.
9. **Provider Resource Mappings:** Tenant-isolated mappings linking internal environments to external cloud resources.
10. **Drift Records & Fingerprints:** Deduplicated ledger of state discrepancies between SecretVault and external providers.
11. **Sync Execution Jobs & Operations:** Audit and execution tracking for synchronization plans and operations.

---

## 2. Threat Actors & Trust Boundaries
- **Untrusted External Network:** Public internet communicating via TLS/HTTPS.
- **Malicious External Attacker:** Probing public endpoints (`/api/v1/auth/*`, `/actuator/health`).
- **Compromised User Identity:** Legitimate user whose credentials/JWT was intercepted.
- **Malicious Insider / Rogue Tenant:** Authenticated member attempting privilege escalation, cross-tenant access, or finding suppression.
- **Compromised Storage / Database Admin:** Direct database inspection (mitigated by AES-GCM envelope encryption).

---

## 3. STRIDE Threat Analysis & Mitigations

### 1. Spoofing Identity
- **Threat:** Forging JWT tokens, tampering with user ID/role claims, or spoofing security event actors.
- **Mitigation:**
  - HMAC-SHA256 signature verification using server-side secret key (`Keys.hmacShaKeyFor`).
  - Authorization derived exclusively from database state (`EffectiveAccessService`), never trusting client claims.
  - Refresh tokens stored as SHA-256 hashes with automatic rotation.
  - Security event logging derives `actorUserId` directly from authenticated `UserPrincipal`.

### 2. Tampering with Data
- **Threat:** Modifying encrypted secret ciphertext in PostgreSQL, tampering with findings, or manipulating risk scores.
- **Mitigation:**
  - AES-256-GCM 128-bit authentication tags verify integrity.
  - Authenticated Additional Data (AAD) binds `secretId:environmentId:versionNumber` to the ciphertext.
  - Deterministic SHA-256 fingerprinting prevents finding ID and drift record tampering and duplicate injection.
  - Finding state transitions require explicit authorization (`SECURITY_MANAGE`) and write audit trail records.
  - Sync execution strictly enforces deterministic state fingerprints before issuing provider updates.

### 3. Repudiation
- **Threat:** User claims they did not reveal a secret, approve a JIT elevation, or dismiss a critical security finding.
- **Mitigation:**
  - Append-only `AuditService` logs every privileged action with actor ID, timestamp, workspace ID, resource target, and outcome.
  - Finding status updates persist mandatory resolution reasons, timestamps, and caller identities.
  - Zero plaintext secrets logged in audit trails.
  - Sync executions and dry runs record full execution history (`sync_jobs`, `sync_operations`, audit actions).

### 4. Information Disclosure
- **Threat:** Secret plaintext leaking in error stack traces, server logs, security event metadata, finding descriptions, or provider synchronization traffic.
- **Mitigation:**
  - `SafeEventMetadataSanitizer` automatically redacts sensitive substrings (`secret`, `token`, `password`, `key`, `credential`) from telemetry payloads.
  - `GlobalExceptionHandler` sanitizes all HTTP error responses.
  - Zero `logger.info/debug` calls contain secret plaintext.
  - Secret reveal endpoints operate strictly via POST with `Cache-Control: no-store`.
  - In-memory provider secret push immediately wipes plaintext byte arrays and never logs Authorization headers or secret bodies.
  - Drift records and sync operations store deterministic SHA-256 fingerprints, never plaintext secret values.
  - Write-only credential input and masked hint responses (`••••••••••••5ab1`).

### 5. Denial of Service (DoS)
- **Threat:** Flooding JIT elevation requests, triggering intensive security scans, or flooding external provider APIs to exhaust rate limits.
- **Mitigation:**
  - Per-workspace concurrency locking prevents concurrent overlapping security analysis scans.
  - Concurrency mutex on `(workspaceId, mappingId)` prevents overlapping sync execution runs against external providers.
  - Max duration cap (240 minutes) on JIT requests.
  - Strict sort field whitelisting on finding, event, and drift query endpoints.
  - Database pagination with capped page sizes (max 100).
  - Provider adapters enforce bounded timeouts (5s connect, 15s read) and handle 429 rate limit responses gracefully without infinite retry loops.

### 6. Elevation of Privilege / Cross-Tenant IDOR
- **Threat:** Developer approving their own JIT request, dismissing security findings, or manipulating provider integrations / mappings in another tenant workspace.
- **Mitigation:**
  - Anti-Self-Approval guard (`request.getUserId().equals(approverUserId)` -> `403 Forbidden`).
  - Anti-Self-Review guard in Access Reviews.
  - Strict hierarchical scope containment (`EffectiveAccessService.checkPermission`).
  - `SECURITY_MANAGE` permission required to dismiss findings, assign owners, or ingest telemetry.
  - `INTEGRATION_MANAGE` and `INTEGRATION_SYNC` permissions required for provider operations.
  - `SYNC_VIEW`, `SYNC_DRY_RUN`, `SYNC_EXECUTE`, `DRIFT_VIEW`, and `DRIFT_MANAGE` permissions required for sync and drift workflows.
  - Conservative deletion prevents unauthorized destruction of unmanaged remote secrets.
  - Explicit workspace ID validation and AAD context binding on all provider integration and mapping queries prevents IDOR attacks.

---

## 4. Residual Risks & Future Roadmap
- **Session Revocation Latency:** Short-lived JWTs expire in 24 hours. Advanced roadmap includes real-time Redis token blacklist.
- **KMS Availability:** LocalDev provider must be substituted with cloud KMS in multi-region production clusters.
- **Automated Remediation Engine:** Phase 6 provides detection and workflow state tracking; Phase 8 provides controlled reconciliation and drift resolution; automated shadow rotation planned for Phase 14.


