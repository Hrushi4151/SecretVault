# Phase 9: Security & Threat Mitigation

## Security Architecture Highlights

Phase 9 strictly adheres to SecretVault's defense-in-depth and zero-trust security invariants:

---

## 1. Zero Plaintext Secret & Token Storage
- **Opaque Machine Bearer Tokens**: Generated via high-entropy `SecureRandom` (256-bit entropy) with prefix `sv_machine_`.
- **Hashed Session Lookup**: Only the SHA-256 hash of the bearer token (`tokenHash`) is persisted in the `machine_sessions` table. A database compromise never exposes usable session tokens.
- **Never Logged**: Bearer tokens and OIDC ID tokens are masked in server logs and audit records.

---

## 2. SSRF Protection & Network Isolation
The `SsrfSafeHttpClient` prevents outbound request tampering when connecting to OIDC discovery and JWKS endpoints:
- Blocks RFC 1918 Private ranges (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
- Blocks Loopback (`127.0.0.0/8`, `::1`).
- Blocks Cloud Metadata (`169.254.169.254`, `metadata.google.internal`).
- Enforces DNS resolution check and SSL Socket pinning to defeat DNS rebinding.

---

## 3. Strict JWT Validation Standards
- Unsecured JWTs (`alg: none`) and symmetric signing algorithms (`HS256`) are rejected outright.
- Enforces asymmetric algorithms: RSA (`RS256`, `RS384`, `RS512`) and ECDSA (`ES256`, `ES384`, `ES512`).
- Validates `iss` (exact match), `aud` (audience verification), `exp` (with 60-second clock skew tolerance), and `nbf`.
- Verifies public keys against cached JWKS with cryptographic signature checking.

---

## 4. Single Unified Authorization Engine (Zero Bypass)
- Machine identities do not use a separate authorization subsystem.
- All access requests route through `EffectiveAccessService.evaluateAccess(...)`.
- Machine identities have **zero implicit admin permissions**.
- Deterministic hierarchy: Scoped Grants -> `DENY > ALLOW` evaluation.
- JIT access, Access Reviews, and WhyAccess lineage fully encompass machine identities.

---

## 5. Security Intelligence & Automated Rule Scans
The Security Center executes automated risk evaluation:
- `MachineIdentityRiskRule`: Identifies machines nearing expiration, active machines with zero trust policies, or revoked identities with stale sessions.
- `OverlyBroadTrustPolicyRule`: Scans for missing repository pins, wildcard branch expressions, or missing claim constraints.
