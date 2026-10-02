# SecretVault Session Management & Session Security Architecture

**Document Version:** 1.0.0  
**Phase:** 5.8.1 — Session Management & Session Security  
**Last Updated:** 2026-10-02  
**Author:** SecretVault Security Architecture Team  

---

## 1. Overview & Objective

SecretVault Phase 5.8.1 introduces an enterprise-grade, server-side authenticated session governance system for human users. The system binds refresh tokens directly to cryptographic user session records, enables real-time device and access telemetry, prevents replay attacks via atomic single-use refresh token consumption, enforces strict user-level authorization boundaries (zero IDOR), and integrates seamlessly with Multi-Factor Authentication (MFA) and account lifecycle states.

```
+-----------------------------------------------------------------------------------+
|                                 USER ACCOUNT                                      |
+-----------------------------------------------------------------------------------+
                                         │
        ┌────────────────────────────────┼────────────────────────────────┐
        ▼                                ▼                                ▼
+──────────────────+             +──────────────────+             +──────────────────+
|  UserSession 1   |             |  UserSession 2   |             |  UserSession 3   |
| (Current Device) |             | (Work Laptop)    |             | (Mobile Device)  |
+──────────────────+             +──────────────────+             +──────────────────+
        │                                │                                │
        ▼                                ▼                                ▼
+──────────────────+             +──────────────────+             +──────────────────+
|  RefreshToken    |             |  RefreshToken    |             |  RefreshToken    |
| (Active Chain)   |             | (Active Chain)   |             | (Active Chain)   |
+──────────────────+             +──────────────────+             +──────────────────+
        │                                │                                │
        ▼                                ▼                                ▼
  Access Token                     Access Token                     Access Token
  (JWT with `sid`)                 (JWT with `sid`)                 (JWT with `sid`)
```

---

## 2. Session Model & Database Schema

### 2.1 Entity Hierarchy
- **`User`**: Account owner across one or more workspaces.
- **`UserSession` (`user_sessions` table)**: Represents one authenticated login context on a specific browser/device.
- **`RefreshToken` (`refresh_tokens` table)**: Holds one-way hashed token material (`token_hash`) linked directly to `session_id`.
- **Access Token (JWT)**: Ephemeral stateless bearer token (24-hour TTL) signed with HMAC-SHA256, carrying an opaque server-generated `sid` (session identifier) claim.

### 2.2 Database Migration (`V12__user_sessions_schema.sql`)
```sql
CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    session_identifier VARCHAR(64) NOT NULL UNIQUE,
    auth_method VARCHAR(32) NOT NULL DEFAULT 'PASSWORD',
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    device_name VARCHAR(128),
    browser VARCHAR(64),
    operating_system VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_user_active ON user_sessions(user_id, revoked_at, expires_at);
CREATE INDEX idx_user_sessions_identifier ON user_sessions(session_identifier);

ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS session_id UUID REFERENCES user_sessions(id) ON DELETE SET NULL;
CREATE INDEX idx_refresh_tokens_session_id ON refresh_tokens(session_id);
```

---

## 3. Session Lifecycle & Token Binding

### 3.1 Primary Authentication & MFA Integration
1. **Password Validation**: Validates user credentials and ensures `UserStatus.ACTIVE`.
2. **MFA Gate**:
   - If MFA is enabled on the account, a transient `MfaChallenge` is issued in Redis. **No `UserSession` or `RefreshToken` is created.**
   - Only after successful TOTP or Recovery Code verification is a `UserSession` initialized with `authMethod` set to `PASSWORD_MFA_TOTP` or `PASSWORD_MFA_RECOVERY`.
3. **Session Creation**:
   - Cryptographically random 128-bit hex session identifier generated (`sess_<32-hex>`).
   - Parsed user-agent metadata (Browser, OS, sanitized device string) and validated IP address recorded.
   - Initial `RefreshToken` created and hashed via SHA-256 with `session_id` FK.
   - JWT Access Token issued with `"sid": "sess_..."` claim.

### 3.2 Token Refresh & Atomic Replay Resistance
When a client requests `/api/v1/auth/refresh`:
1. The incoming refresh token is SHA-256 hashed.
2. The user account status is verified active.
3. The refresh token is atomically marked revoked in a single database statement:
   ```sql
   UPDATE refresh_tokens SET revoked = true 
   WHERE token_hash = :tokenHash AND revoked = false;
   ```
4. If the update affected 0 rows, a replay attack or invalid token is detected. The request is immediately rejected (`401 Unauthorized`) and an audit event (`SESSION_REFRESH_REJECTED`) is recorded.
5. The associated `UserSession` is verified not revoked and not expired.
6. The session `last_used_at` timestamp is refreshed.
7. A new rotated `RefreshToken` is issued, bound to the same `session_id`, and returned with a fresh Access Token.

---

## 4. Session Revocation Semantics

### 4.1 Single Session Revocation (`DELETE /api/v1/auth/sessions/{sessionId}`)
- Validates that `authenticatedUser.id == session.userId`. Any cross-user attempt yields `404 Not Found` (strict IDOR protection).
- Marks `UserSession` as revoked (`revoked_at = NOW()`, `revocation_reason = "USER_REVOKED"`).
- Atomically revokes all `refresh_tokens` referencing this session ID.
- If the current session is revoked, the client's next refresh will immediately fail, prompting the frontend to transition to a signed-out state.

### 4.2 Revoke All Other Sessions (`POST /api/v1/auth/sessions/revoke-others`)
- Identifies the caller's active session via the authenticated JWT `sid` claim.
- Revokes all other active sessions for that user ID.
- Invalidates all refresh tokens belonging to other sessions while keeping the current session's refresh chain intact.

### 4.3 Revoke All Sessions (`POST /api/v1/auth/sessions/revoke-all`)
- Atomically revokes all sessions and all refresh tokens belonging to the user.
- Enforces full credential teardown across all devices.

---

## 5. Security Invariants & Threat Model

| Threat | Defense / Mitigation | Verified In Test |
| :--- | :--- | :--- |
| **Insecure Direct Object Reference (IDOR)** | Sessions are strictly user-scoped (`user_id == session.userId`). Workspace roles (even OWNER/ADMIN) cannot view or revoke another member's login sessions. | `SessionSecurityTest.testIdorSessionRevocation` |
| **Refresh Token Replay** | Tokens are rotated upon every refresh. Concurrent or reused tokens fail atomic SQL consumption and are rejected. | `SessionConcurrencyTest.testSimultaneousRefreshWithSameTokenEnforcesReplayProtection` |
| **Pending MFA Challenge Leak** | MFA challenges are short-lived Redis keys (300s TTL). No session or refresh token exists until MFA verification succeeds. | `SessionSecurityTest.testPendingMfaChallengeCreatesZeroSessions` |
| **Suspended / Disabled User Refresh** | Refresh pipeline re-checks user account status from database; deactivated accounts cannot refresh. | `SessionSecurityTest.testDeactivatedUserCannotRefreshSession` |
| **Plaintext Credential Persistence** | Refresh tokens are hashed via SHA-256 before database insertion. Raw tokens are never logged or stored. | Architecture Inspection & Audit Logs |
| **Session Fixation / Client-Controlled IDs** | Session identifiers are server-generated using `SecureRandom` with 128-bit entropy (`sess_<32-hex>`). | `SessionSecurityTest.testSessionIdentifierEntropyAndFormat` |
| **Rate-Limiting Exhaustion** | Session endpoints are protected by Redis token-bucket rate limiters (`@RateLimited(tier = SENSITIVE_OPERATION)`). | Security Review |

---

## 6. Audit Logging

All session lifecycle actions are immutably logged to the `audit_logs` table:

- `SESSION_CREATED`: Recorded upon successful authentication and session initialization.
- `SESSION_REFRESHED`: Recorded upon successful refresh token rotation.
- `SESSION_REVOKED`: Recorded when an individual session is terminated by the user.
- `SESSION_REVOKED_ALL_OTHERS`: Recorded when all other sessions are terminated.
- `SESSION_REVOKED_ALL`: Recorded when all user sessions are terminated.
- `SESSION_REFRESH_REJECTED`: Emitted on replay attacks, expired sessions, or revoked token usage.

---

## 7. Frontend Integration (`Sessions & Devices`)

Located in **Settings → Account & Security → Sessions & Devices**:
- **Current Device Card**: Highlights current browser, operating system, IP address, and login method (e.g. `Password + MFA TOTP`), with an active pulse indicator.
- **Other Active Sessions**: Displays list of other devices with last-active timestamps and individual revocation buttons.
- **Revoke All Other Sessions**: One-click bulk revocation with confirmation modal.
- **Sign Out All**: Global termination with confirmation dialog.
- **Zero Token Leakage**: The UI never receives or retains refresh tokens, token hashes, or internal database IDs.
