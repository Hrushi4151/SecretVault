# Phase 9: Threat Model & Security Invariants

## Threat Surface Analysis

The introduction of Machine Identities and OIDC Workload Authentication introduces new potential threat vectors that SecretVault systematically neutralizes.

---

## 1. Threat Vectors & Countermeasures

### T1: SSRF Attacks via Provider Endpoints
- **Threat**: An attacker supplies a malicious `issuerUrl` or `jwksUrl` pointing to an internal service (e.g., AWS Metadata `169.254.169.254` or internal microservices) to exfiltrate host credentials.
- **Countermeasure**: `SsrfSafeHttpClient` validates all destination IP addresses prior to connection and pins them at the socket level. Rejects private RFC 1918, loopback, link-local, and cloud metadata subnets.

### T2: Identity Provider Token Forgery & Replay
- **Threat**: An attacker submits a fabricated JWT, an expired token, or a token signed with a weak/symmetric key.
- **Countermeasure**: `JwtValidationEngine` verifies asymmetric cryptographic signatures against authentic JWKS public keys. Enforces strict `iss`, `aud`, `exp`, and algorithm validation. Disallows `none` and `HS256`.

### T3: Overly Broad Trust Policies & Pipeline Hijacking
- **Threat**: A trust policy only checks `repository_owner`, allowing any arbitrary repository in an organization or public fork to claim secrets.
- **Countermeasure**: Visual Policy Builder enforces repository pinning. `OverlyBroadTrustPolicyRule` scans and flags unpinned or wildcard policies in the Security Center.

### T4: Database Compromise & Token Theft
- **Threat**: An attacker with database read access dumps active machine session tokens.
- **Countermeasure**: Only SHA-256 hashes (`tokenHash`) of bearer tokens are persisted. Original bearer tokens cannot be derived from database records.

### T5: Machine Identity Privilege Escalation
- **Threat**: A machine identity gains administrative access or accesses secrets across workspace boundaries.
- **Countermeasure**: Single unified authorization pipeline (`EffectiveAccessService`). Machine identities have zero implicit permissions, cannot administer workspaces unless explicitly granted, and are strictly bounded by workspace foreign keys and scoped access grants.

---

## 2. Security Invariants

1. **Deterministic Resolution**: `DENY > ALLOW` always holds across all permission evaluations.
2. **Short-Lived Sessions**: Machine session tokens have fixed TTLs (default 1 hour) and are instantly revocable.
3. **Immutable Audit Trails**: All token exchanges, policy evaluations, and secret reveals are recorded with structured audit action codes.
