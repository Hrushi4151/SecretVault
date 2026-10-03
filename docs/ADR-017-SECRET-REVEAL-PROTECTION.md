# ADR-017: Production-Grade Secret Reveal Protection & Cryptographic Policy Engine

## Status
**ACCEPTED & SIGNED OFF** (Phase 5.8.5)

## Context
In SecretVault, secret values represent the core asset entrusted to the platform. Prior to Phase 5.8.5:
1. Secret reveals occurred in a single request without guaranteed intent validation or cryptographic challenge binding.
2. Production secrets relied primarily on role-based checks without granular hierarchical policies enforcing mandatory justification, copy restrictions, or factor-specific step-up constraints.
3. Historical versions were revealed through an uncoupled endpoint.
4. Clients had no authoritative protocol metadata dictating auto-mask countdowns, clipboard auto-clearing timeouts, or copy prohibitions.

## Decision
We have implemented a unified, production-grade **Secret Reveal Protection Architecture**:

1. **Single Authoritative Authorization**:
   - All secret reveal attempts delegate strictly to `EffectiveAccessService` for real-time permission evaluation (`AccessPermission.SECRET_REVEAL`).
   - There are zero parallel or secondary authorization checks.

2. **Hierarchical Reveal Policy Engine (`SecretRevealPolicyService`)**:
   - Supports 4 granular scopes: `WORKSPACE`, `PROJECT`, `ENVIRONMENT`, `SECRET`.
   - Scopes evaluate by strict specificity: `SECRET` overrides `ENVIRONMENT`, which overrides `PROJECT`, which overrides `WORKSPACE`.
   - Supports 4 policy levels: `DEFAULT`, `SENSITIVE`, `HIGHLY_SENSITIVE`, `PRODUCTION_CRITICAL`.

3. **Two-Phase Single-Use Intent Protocol**:
   - `POST .../reveal-intent` evaluates effective access and policy requirements, verifies Step-Up proof / justification, and generates a single-use token (`sec_rev_...`).
   - `POST .../reveal` atomically consumes the token via Redis `GETDEL` (Lua script) to completely eliminate replay attacks, validates contextual resource binding, re-checks real-time permissions (TOCTOU defense), and decrypts payload in-memory.

4. **Cryptographic Protection & In-Memory Zeroization**:
   - AES-256-GCM envelope encryption with strict AAD binding (`secretId:environmentId:versionNumber`).
   - Decrypted byte arrays are explicitly wiped in memory with `Arrays.fill(bytes, (byte) 0)`.
   - Responses enforce HTTP headers: `Cache-Control: no-store, no-cache, must-revalidate, private`, `Pragma: no-cache`, `Expires: 0`.

5. **Historical Version Unification**:
   - Revealing historical secret versions (`/versions/{versionNumber}/reveal-intent` and `/versions/{versionNumber}/reveal`) routes through the authoritative reveal pipeline with identical policy controls and version-bound intent validation.

6. **Adversarial Verification**:
   - 60 comprehensive threat scenarios (`SR-01` through `SR-60`) codified in `SecretRevealThreatMatrixTest.java` and verified with 100% pass rate.

## Consequences
- **Positive**:
  - Full defense against TOCTOU, replay, cross-tenant, cross-secret, cross-user, and cross-session reveal attacks.
  - Granular control over sensitive production credentials (including disabling clipboard copy and enforcing WebAuthn passkeys).
  - Complete, immutable audit log of all reveal intents, executions, policy modifications, and blocked replays.
  - Zero plaintext leakage in logs, URLs, error messages, Redis, and audit records.
- **Trade-offs**:
  - Two-phase reveal requires an extra HTTP round-trip, but is negligible (<15ms) and provides enterprise-grade security. Backward compatibility is maintained for direct API clients.
