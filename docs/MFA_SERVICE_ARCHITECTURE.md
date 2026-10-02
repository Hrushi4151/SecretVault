# SecretVault Phase 5.7.3 — MFA Services & Authentication State Machine Architecture

**Document Version:** 1.0  
**Phase:** 5.7.3 (MFA Services & State Machine)  
**Security Status:** Audited, Tested & Standards-Compliant  
**Package:** `com.secretvault.auth.mfa.service`  

---

## 1. Overview & Service Architecture

Phase 5.7.3 implements the core MFA business service layer, connecting:
1. **Cryptographic Engine (Phase 5.7.1):** RFC 6238 TOTP, Base32, BCrypt-12 recovery code engine.
2. **PostgreSQL Persistence (Phase 5.7.2):** `user_mfa` and `mfa_recovery_codes` tables with AES-256-GCM envelope encryption.
3. **Redis SecurityStateStore:** Atomic, short-lived security state challenges (`mfa_challenge`).
4. **Audit System:** Immutable event stream for MFA lifecycle actions.

```
                  ┌────────────────────────────────────────────────────────┐
                  │                 MfaService Interface                   │
                  │  - isMfaEnabled(userId)                                │
                  │  - getStatus(userId)                                   │
                  │  - beginEnrollment(userId)                             │
                  │  - activateMfa(userId, code)                           │
                  │  - createLoginChallenge(userId)                        │
                  │  - verifyLoginTotp(challengeId, userId, code)          │
                  │  - verifyLoginRecoveryCode(challengeId, userId, code)  │
                  │  - disableMfa(userId)                                  │
                  └───────────────────────────┬────────────────────────────┘
                                              │
                    ┌─────────────────────────┴─────────────────────────┐
                    ▼                                                   ▼
       ┌────────────────────────┐                          ┌────────────────────────┐
       │ PostgreSQL Persistence │                          │ Redis Security State   │
       │ - user_mfa             │                          │ - mfa_challenge        │
       │ - mfa_recovery_codes   │                          │ - single-use Lua DEL   │
       │ - authoritative state  │                          │ - attempt throttling   │
       └────────────────────────┘                          └────────────────────────┘
```

---

## 2. Authentication Flow & State Machine

```
PASSWORD VALID (AuthService)
      │
      ▼
MFA ENABLED? (UserMfa.status == ENABLED)
   /       \
 NO         YES
 │           │
 ▼           ▼
NORMAL     CREATE LOGIN CHALLENGE
AUTH         (Redis: 300s TTL, max 5 attempts)
             │
             ▼
          VERIFY TOTP or RECOVERY CODE
             │
             ▼
          ATOMIC CONSUMPTION
          ├── Redis: Lua GET-and-DEL
          └── Postgres: markUsedIfUnused (for recovery code)
             │
             ▼
          MFA_VERIFIED
             │
             ▼
          TOKEN ISSUANCE (Deferred to Phase 5.7.4)
```

### State Machine Lifecycle (`AuthenticationState`)
| State | Description | Transition Trigger |
|---|---|---|
| `AUTHENTICATION_NOT_STARTED` | User has not submitted credentials | Initial state |
| `PASSWORD_VERIFIED` | Primary password validated; determining MFA requirement | Successful password check |
| `MFA_REQUIRED` | User has MFA enabled; challenge issued in Redis | `createLoginChallenge` |
| `MFA_VERIFIED` | TOTP or Recovery Code successfully validated and consumed | Successful single-use verification |
| `AUTHENTICATED` | Session tokens / JWT issued | Final token issuance |
| `AUTHENTICATION_FAILED` | Invalid credentials or security violation | Verification mismatch |
| `MFA_CHALLENGE_EXPIRED` | Challenge TTL elapsed or challenge already consumed | Challenge not in Redis / replay |
| `MFA_CHALLENGE_LOCKED` | Maximum failed attempts (5) exceeded | Challenge deleted from Redis |

---

## 3. Enrollment & Activation Lifecycle

### 3.1 Enrollment Initiation (`beginEnrollment`)
1. Verifies user does not already have an `ENABLED` MFA configuration.
2. Generates 160-bit cryptographically secure Base32 secret using `TotpService`.
3. Encrypts secret via `AesGcmEnvelopeEncryptionService` with AAD bound to `"user-mfa:<userId>"`.
4. Stores `PENDING_VERIFICATION` state in `user_mfa` (replacing any existing pending/disabled record).
5. Generates RFC 6238 Key URI (`otpauth://totp/SecretVault:user@domain?secret=...`).
6. Emits `MFA_ENROLLMENT_STARTED` audit event.
7. **Security Invariant:** Plaintext secret and Key URI are returned once transiently in memory; never persisted in DB, Redis, or audit logs.

### 3.2 MFA Activation (`activateMfa`)
1. Requires a valid pending `UserMfa` record.
2. Decrypts TOTP secret in memory and verifies candidate TOTP code.
3. On verification success:
   - Sets `UserMfa.status = ENABLED`, `verifiedAt = now`, `failedAttempts = 0`.
   - Synchronizes `users.is_mfa_enabled = true`.
   - Purges any existing recovery codes.
   - Generates 10 high-entropy unambiguous recovery codes (60-bit entropy each).
   - Hashes codes with BCrypt-12 and stores in `mfa_recovery_codes`.
   - Emits `MFA_ACTIVATED` audit event.
   - Returns plaintext recovery codes **once** for user backup.

---

## 4. Redis Challenge Management & Atomic Consumption

### 4.1 Challenge Data Model (`MfaChallengePayload`)
Stored in Redis under `secretvault:{env}:security:mfa_challenge:{challengeId}`:
- `challengeId`: UUID string.
- `userId`: UUID of authenticated user.
- `purpose`: `"LOGIN_MFA"`.
- `createdAt`: Timestamp.
- `expiresAt`: Timestamp (`createdAt + 300s`).
- `maxAttempts`: `5`.

**Zero Sensitive Material:** Challenge payload contains **no** passwords, hashes, TOTP secrets, recovery codes, or tokens.

### 4.2 Single-Use Replay Protection
- Challenge consumption is executed via Redis Lua script:
  ```lua
  local val = redis.call('GET', KEYS[1])
  if val then
      redis.call('DEL', KEYS[1])
      return val
  end
  return nil
  ```
- **Concurrency Guarantee:** 20+ concurrent racing requests against the same challenge will result in **exactly 1 success**; all other requests receive `MFA_CHALLENGE_EXPIRED`.

---

## 5. Recovery Code Verification

1. Loads unused recovery codes for the user's active MFA profile.
2. Performs in-memory BCrypt matching against candidate code.
3. Upon match, executes atomic SQL consumption in PostgreSQL:
   ```sql
   UPDATE mfa_recovery_codes
   SET used = true, used_at = :now, updated_at = :now
   WHERE id = :id AND used = false;
   ```
4. Atomically consumes Redis MFA challenge via Lua script.
5. Emits `MFA_RECOVERY_CODE_USED` and `MFA_VERIFICATION_SUCCESS` audit events.
6. Returns `MFA_VERIFIED` state.

---

## 6. Fail-Closed Redis Resilience

If Redis is unreachable or returns an error during challenge creation, lookup, or consumption:
- The service **fails closed**: authentication is denied immediately.
- Throws sanitized `ApiException.internal("MFA_SERVICE_UNAVAILABLE", "MFA service temporarily unavailable")`.
- Under no circumstance is MFA bypassed or tokens issued when Redis is down.

---

## 7. Deferred to Phase 5.7.4

The following are strictly deferred to Phase 5.7.4:
- `MfaController` REST endpoints (`/api/v1/auth/mfa/*`).
- `AuthController.login` integration with MFA challenge response.
- JWT and refresh token issuance upon MFA challenge verification.
- Rate-limiting annotations on controller routes.
