# SecretVault Step-Up Authentication Security Architecture & Threat Model

**Phase**: 5.8.2 — Generalized Step-Up Authentication  
**Document Version**: 1.0.0  
**Classification**: Engineering & Security Architecture Baseline  
**Audience**: Security Engineers, Platform Architects, DevSecOps Engineers  

---

## 1. Executive Summary

SecretVault's **Generalized Step-Up Authentication Framework** provides an ephemeral, cryptographic, and server-authoritative mechanism proving that an already-authenticated user recently re-authenticated using an approved strong factor (Account Password, TOTP Authenticator, or Backup Recovery Code) for a specific sensitive operation on a specific resource scope.

Step-Up authentication is **not** a general authentication token and **never** bypasses underlying tenant isolation, workspace RBAC, environment protection policies, or JIT authorization rules. It provides proof of authentication strength within a narrowly scoped operation context.

```
Existing Authenticated Session (JWT + Active Session ID)
                    │
                    ▼
     Sensitive Operation Requested (e.g. Production Secret Reveal)
                    │
                    ▼
       Step-Up Policy Evaluation (requiresStepUp?)
                    │
       ┌────────────┴────────────┐
       ▼ NO                      ▼ YES
Proceed with Normal      Create Ephemeral Challenge in Redis
RBAC Authorization       (Bound to User, Session, Action, Context)
                                 │
                                 ▼
                         Strong Factor Verification
                     (Password, TOTP, or Recovery Code)
                                 │
                                 ▼
                     Short-Lived Single-Use Proof Issued
                     (stup_<32-hex>, 300s TTL in Redis)
                                 │
                                 ▼
                   Atomic Lua Consumption & Time-of-Use RBAC
                                 │
                                 ▼
                     Execute Operation & Record Audit Log
```

---

## 2. Security Principles & Threat Model

### 2.1 Invariant Guarantees

1. **User Binding**: Challenges and proof tokens are strictly bound to the authenticated `userId`. Proof issued to User A cannot be consumed by User B under any circumstances.
2. **Session Binding**: Challenges and proofs are strictly bound to the caller's active session identifier (`sessionIdentifier` from JWT `sid` claim). Proof issued on Session A cannot be used on Session B.
3. **Session Revocation Invalidation**: If the issuing session is revoked or expires in PostgreSQL, any active challenge or proof is immediately rejected.
4. **Action Binding**: Proofs are bound to a specific `StepUpAction` (e.g., `SECRET_REVEAL`). A proof issued for `SECRET_REVEAL` will be rejected if presented for `SECRET_DELETE` or `ENVIRONMENT_PROMOTE`.
5. **Resource & Scope Binding**: Proofs are bound to an exact `StepUpContext` (`workspaceId`, `projectId`, `environmentId`, `secretId`). A proof obtained for Secret 1 cannot authorize revealing Secret 2, nor can a proof for Environment 1 authorize Environment 2.
6. **Single-Use Atomic Consumption (Replay Protection)**: Step-up proofs are consumed atomically via Redis Lua script (`consumeAtomic`: atomic GET-and-DEL). Replaying the same token fails immediately with `STEP_UP_INVALID`.
7. **Short-Lived TTL**: Challenges and proofs have an unextendable maximum TTL of 300 seconds (5 minutes).
8. **Server-Authoritative State**: No client-controlled state (`stepUpVerified=true`) is ever trusted. The backend verifies cryptographic tokens against Redis state store.
9. **Zero Credential / Plaintext Exposure**: Passwords, TOTP secrets, recovery codes, and decrypted secret values are never stored in Redis, never stored in persistent browser storage, and never logged.
10. **Fail-Closed Redis Policy**: If Redis is unavailable or returns an error, step-up verification immediately fails closed with HTTP 500 `SECURITY_STATE_ERROR`.
11. **Time-of-Check / Time-of-Use (TOCTOU)**: Authorization is checked before creating a challenge (preventing resource enumeration) and re-verified upon proof consumption (ensuring permission changes made after step-up are respected).

### 2.2 Threat Matrix

| Threat ID | Threat Description | Mitigation Strategy | Verification |
| :--- | :--- | :--- | :--- |
| **TH-01** | **Replay Attack**: Attacker intercepts proof token from network or logs and attempts reuse. | Tokens are single-use; atomically deleted via Lua script on first consumption. | Tested in `StepUpSecurityTest.testSecurity_replayProtection` and `StepUpConcurrencyTest`. |
| **TH-02** | **Cross-User Privilege Escalation**: User A obtains proof and attacker User B passes it with their session. | Proof payload contains `userId`; `verifyAndConsumeProof` asserts `proof.userId == caller.userId`. | Tested in `StepUpSecurityTest.testSecurity_crossUserReplay_rejected`. |
| **TH-03** | **Cross-Session Hijacking**: Attacker on stolen session attempts to reuse proof from legitimate session. | Proof payload contains `sessionIdentifier`; validated against caller's active session. | Tested in `StepUpSecurityTest.testSecurity_crossSessionReplay_rejected`. |
| **TH-04** | **Action Substitution**: Proof obtained for low-risk action used for high-risk action (e.g. reveal vs. delete). | Proof contains exact `StepUpAction` enum; mismatch triggers 403 `STEP_UP_MISMATCH`. | Tested in `StepUpSecurityTest.testSecurity_actionSubstitution_rejected`. |
| **TH-05** | **Resource Scope Elevation (IDOR)**: Proof obtained for Secret A in Dev used for Secret B in Prod. | Context (`workspaceId`, `projectId`, `environmentId`, `secretId`) checked strictly. | Tested in `StepUpSecurityTest.testSecurity_secretResourceSubstitution_rejected`. |
| **TH-06** | **Session Revocation Race**: User logs out or admin revokes session, but proof remains within 5m TTL. | `verifyAndConsumeProof` validates caller's session `isActive()` in PostgreSQL before allowing action. | Tested in `StepUpSecurityTest.testSecurity_revokedSessionInvalidatesProof`. |
| **TH-07** | **Brute-Force Factor Guessing**: Attacker guesses passwords or 6-digit TOTP codes on challenge. | Rate-limited at controller + 5 maximum failed attempts per challenge triggers automatic deletion and lockout. | Tested in `StepUpSecurityTest.testSecurity_bruteForceLockout`. |
| **TH-08** | **Stale / Revoked Permissions**: User permissions removed between challenge creation and consumption. | Normal RBAC check runs before and after step-up verification (Time-of-Use). | Tested in `SecretServiceTest`. |

---

## 3. Supported Authentication Factors

### 3.1 Password Step-Up
- Compares supplied password against the authenticated user's BCrypt hash (`User.passwordHash`) with strength cost 12.
- Authenticated user ID is derived strictly from `SecurityContext` / `UserPrincipal`. Request body user identifiers are ignored.
- Failed attempts increment attempt counters and emit `STEP_UP_VERIFICATION_FAILED` audit events without leaking password validity specifics.

### 3.2 TOTP Authenticator App Step-Up
- Integrates the existing Phase 5.7 TOTP cryptographic engine (`DefaultMfaService`, `TotpService`).
- Decrypts user's AES-256-GCM envelope-encrypted MFA secret in memory with AAD `user-mfa:<userId>`.
- Evaluates RFC 6238 time-step windows (current, -1, +1).
- Available whenever the caller's account has MFA status `ENABLED`.

### 3.3 Backup Recovery Code Step-Up
- Directly evaluates and single-use consumes one of the 10 PBKDF2/SHA-256 hashed recovery codes generated during MFA enrollment.
- Atomically marks the code as used in PostgreSQL with timestamp upon verification.

---

## 4. Centralized Step-Up Policy Engine

The `StepUpPolicyService` centralizes decisions regarding which actions require step-up re-authentication:

```java
public interface StepUpPolicyService {
    boolean requiresStepUp(UUID userId, StepUpAction action, StepUpContext context);
}
```

### Policy Rules Matrix:

| Action | Context Evaluation | Step-Up Required? | Rationale |
| :--- | :--- | :--- | :--- |
| `SECRET_REVEAL` | `EnvType.PRODUCTION` or `isProtected=true` | **YES** | Protects production secrets from transient session misuse. |
| `SECRET_REVEAL` | `EnvType.DEVELOPMENT` / `STAGING` (unprotected) | **NO** | Minimizes developer friction during standard development. |
| `SECRET_DELETE` | `EnvType.PRODUCTION` or `isProtected=true` | **YES** | Prevents accidental or unauthorized destructive production operations. |
| `SECRET_ROLLBACK` | `EnvType.PRODUCTION` or `isProtected=true` | **YES** | Guards production configuration alterations. |
| `ENVIRONMENT_PROMOTE` | Destination `EnvType.PRODUCTION` or protected | **YES** | Enforces strong authorization before promoting changes to production. |
| `SESSION_REVOKE_ALL` | Any context | **YES** | Mass session termination is a critical security event. |
| `MFA_DISABLE` | Any context | **YES** | Modifying account security posture requires re-authentication. |
| `ACCESS_GRANT` / `REVOKE` | `EnvType.PRODUCTION` or protected scope | **YES** | Prevents unauthorized standing access elevation. |
| `JIT_APPROVE` | Production target | **YES** | Ensures approver identity for temporary elevation. |

---

## 5. Redis State Architecture

Redis security state storage reuses `RedisSecurityStateStore` and `RedisKeyBuilder` with the following namespaces:

- **Challenge Category**: `secretvault:{env}:security:step_up_challenge:{challengeId}`
  - TTL: 300 seconds
  - Payload: `StepUpChallengePayload` (safe identifiers only)
  - Attempt Counter: `secretvault:{env}:security:step_up_challenge:{challengeId}:attempts`
- **Proof Category**: `secretvault:{env}:security:step_up_proof:{proofToken}`
  - TTL: 300 seconds
  - Payload: `StepUpProofPayload` (bound user, session, action, context)

All Redis interactions adhere strictly to the naming invariant: payload classes never contain "secret", "password", "totp", or "privatekey" in class name, preventing raw entity persistence.

---

## 6. Audit System Integration

Every step-up state transition emits append-only, immutable audit events:

- `STEP_UP_CHALLENGE_CREATED`: Logged when challenge is generated for a user.
- `STEP_UP_VERIFICATION_SUCCESS`: Logged upon successful factor verification.
- `STEP_UP_VERIFICATION_FAILED`: Logged upon failed credential attempt.
- `STEP_UP_EXPIRED`: Logged when expired challenge or proof is presented.
- `STEP_UP_REPLAY_REJECTED`: Logged when duplicate token or mismatched context is submitted.
- `STEP_UP_PROOF_CONSUMED`: Logged when proof is successfully consumed for operation.

Audit entries contain safe metadata (`actor`, `userId`, `workspaceId`, `challengeId`, `authMethod`, `outcome`). They **never** contain passwords, OTP codes, recovery codes, or secret plaintexts.

---

## 7. Frontend Integration & UX

1. When a user requests a protected action (e.g. revealing a production secret), the backend responds with HTTP 403 `STEP_UP_REQUIRED`.
2. The frontend catches this code and opens `StepUpAuthenticationModal`.
3. The modal requests an ephemeral challenge, displays available factors (Password, Authenticator App, Recovery Code), and accepts input.
4. On successful verification, the modal receives the proof token and transparently retries the original request with the `X-Step-Up-Proof` HTTP header.
5. Sensitive form inputs are zeroized on modal close, factor switch, or submission.

---

## 8. Future Roadmap: WebAuthn / Passkeys Integration

In future phases, WebAuthn/FIDO2 will be added as an additional `StepUpFactor.WEBAUTHN`. The `StepUpAuthenticationService` and `StepUpAuthenticationModal` are already designed with factor abstraction to incorporate WebAuthn challenges and public-key assertion verification without restructuring the generalized framework.
