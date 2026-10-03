# SecretVault — WebAuthn / Passkeys Security Invariants & Threat Matrix

**Version:** 1.0.0 (Phase 5.8.3)  
**Security Classification:** Highly Confidential — Threat Modeling & Invariants  
**Authoritative Implementation:** SecretVault Security Engineering

---

## 1. Security Invariants

The following 20 security invariants are strictly enforced by the codebase and verified by automated security test suites:

1. **Factor, Not Authorization:** WebAuthn is an authentication and re-authentication factor; it never grants authorization, bypasses RBAC, or elevates role permissions.
2. **Tenant Isolation:** WebAuthn operations are strictly bound to the authenticated caller's identity; cross-tenant or cross-user credential operations are impossible.
3. **No Private Key Ingestion:** Authenticators retain private keys in hardware; the backend never receives or stores private keys in memory, logs, or PostgreSQL.
4. **Single-Use Ephemeral Challenges:** All WebAuthn challenges are cryptographically random, single-use, 300-second TTL, and atomically consumed via Redis Lua scripts (`GET` + `DEL`).
5. **Strict Origin Enforcement:** Client origin (`clientDataJSON.origin`) must match the server's configured allowlist (`secretvault.webauthn.allowed-origins`). Wildcards (`*`) and dynamic Host header trusting are prohibited.
6. **RP ID Validation:** Relying Party ID is strictly validated against server configuration during both registration and authentication.
7. **Session Binding:** Step-up challenges and registration ceremonies are bound to the caller's active `UserSession`. Revoked or expired sessions immediately invalidate outstanding ceremonies.
8. **Action & Resource Context Binding:** Step-up proof tokens are cryptographically bound to the target user, session, specific action (`SECRET_REVEAL`, `SECRET_DELETE`, etc.), and resource context (`workspaceId`, `projectId`, `environmentId`, `secretId`).
9. **Single-Use Step-Up Proofs:** Step-up proofs are consumed atomically upon sensitive operation invocation; replay attempts are rejected with HTTP 403 `STEP_UP_INVALID`.
10. **Fail-Closed Redis State:** Redis communication failures during challenge or proof evaluation cause operations to fail closed with HTTP 500 `SECURITY_STATE_ERROR` rather than defaulting to open access.
11. **User Status Verification:** Disabled, suspended, or locked user accounts cannot register credentials, issue authentication challenges, or verify assertions.
12. **Sign-Count Rollback Detection:** Authenticator signature counters are tracked per credential. Decreasing counter anomalies trigger `WEBAUTHN_CLONE_DETECTED` audit alerts and step-up challenges.
13. **Lockout Prevention:** Users cannot delete their last remaining authentication factor without possessing alternative recovery or login mechanisms.
14. **Safe Response Metadata:** Credential listing and management endpoints return only safe metadata (`id`, `friendlyName`, `createdAt`, `lastUsedAt`, `transports`, `discoverable`); public key COSE bytes are never exposed over public APIs.
15. **Zero Persistent Storage of Secrets:** Frontend client never persists challenges, raw assertions, or WebAuthn artifacts in `localStorage`, `sessionStorage`, or `IndexedDB`.
16. **Zero-Log Sanitization:** Sensitive cryptographic payloads and challenge values are excluded from application loggers and audit streams.
17. **Distributed Rate Limiting:** All ceremony endpoints are guarded by Redis distributed rate limiters (User ID for authenticated, IP for public).
18. **Cache-Control Enforcement:** All WebAuthn API responses include `Cache-Control: no-store, no-cache, must-revalidate` and `Pragma: no-cache`.
19. **Adversarial IDOR Protection:** Credential lookup and mutation queries strictly enforce `WHERE id = :id AND user_id = :userId`.
20. **Concurrency Safety:** Simultaneous concurrent attempts (16+ threads) targeting the same challenge or proof token result in exactly one successful consumption and all other attempts failing.

---

## 2. Adversarial Threat Matrix

| Threat ID | Adversarial Threat Vector | Mitigation Mechanism | Verification Test |
| :--- | :--- | :--- | :--- |
| **THREAT-01** | Challenge Replay Attack | Ephemeral Redis key consumed via atomic Lua GET+DEL. | `WebAuthnSecurityTest.testChallengeReplay_rejected` |
| **THREAT-02** | Assertion Replay Attack | Single-use challenge invalidation prevents re-submitting signed assertion. | `WebAuthnAuthenticationTest.testAuthenticationReplay_fails` |
| **THREAT-03** | Cross-User Challenge Theft | Registration challenge payload stores `userId`; mismatched verifier rejected. | `WebAuthnRegistrationTest.testRegistrationWrongUser_fails` |
| **THREAT-04** | Phishing / Origin Spoofing | Yubico library validates `clientDataJSON.origin` against configured allowed origins. | `WebAuthnSecurityTest.testInvalidOrigin_rejected` |
| **THREAT-05** | RP ID Confusion | Server verifies `authenticatorData.rpIdHash` matches configured RP ID SHA-256 hash. | `WebAuthnSecurityTest.testInvalidRpId_rejected` |
| **THREAT-06** | Credential IDOR (Rename/Revoke) | Repository enforces `user_id` scoping on all mutations; foreign IDs return 404/403. | `WebAuthnSecurityTest.testIdorProtection_otherUserCredentialAccess` |
| **THREAT-07** | Step-Up Proof Session Moving | Step-Up proof validates caller `sessionIdentifier` against proof `sessionIdentifier`. | `StepUpSecurityTest.testCrossSessionProof_fails` |
| **THREAT-08** | Step-Up Proof Action Escalation | Proof issued for `SECRET_REVEAL` cannot be used to execute `SECRET_DELETE`. | `StepUpSecurityTest.testActionMismatch_fails` |
| **THREAT-09** | Inactive / Suspended User Login | User status checked before issuing challenge and during assertion completion. | `WebAuthnAuthenticationTest.testDisabledUser_fails` |
| **THREAT-10** | Concurrent Challenge Race | 16 concurrent threads attempting same challenge; Lua atomicity allows exactly 1. | `WebAuthnConcurrencyTest.testConcurrentRegistrationChallengeConsumption` |
| **THREAT-11** | Authenticator Clone / Counter Rollback | `sign_count` comparison flags decreasing counters; triggers `WEBAUTHN_CLONE_DETECTED`. | `WebAuthnSecurityTest.testSignCountAnomaly_detected` |
| **THREAT-12** | Accidental Account Lockout | `DefaultWebAuthnService.revokeCredential` checks remaining password/MFA methods. | `WebAuthnSecurityTest.testCredentialRevocationLockoutPrevention` |

---

## 3. Operational & Environment Configuration

### Development Configuration (`application-dev.yml`)
```yaml
secretvault:
  webauthn:
    rp-id: localhost
    rp-name: SecretVault Local Dev
    allowed-origins:
      - http://localhost:5173
      - http://localhost:3000
    challenge-ttl-seconds: 300
    timeout-seconds: 60
    user-verification: PREFERRED
    allow-origin-port: true
    clone-detection-action: ALERT_AND_CHALLENGE
```

### Production Configuration (`application-prod.yml`)
```yaml
secretvault:
  webauthn:
    rp-id: ${WEBAUTHN_RP_ID:secretvault.dev}
    rp-name: SecretVault Security Control Plane
    allowed-origins:
      - https://${WEBAUTHN_APP_DOMAIN:app.secretvault.dev}
    challenge-ttl-seconds: 300
    timeout-seconds: 60
    user-verification: REQUIRED
    allow-origin-port: false
    clone-detection-action: ALERT_AND_CHALLENGE
```
