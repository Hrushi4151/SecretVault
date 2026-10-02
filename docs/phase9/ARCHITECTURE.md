# Phase 9: System Architecture

## Architectural Overview

The Machine Identity and OIDC Workload Authentication subsystem provides a secure, cryptographically verified, and non-bypassable mechanism for workloads to authenticate and access secrets without long-lived static secrets.

```
+----------------------------------------------------------------------------------------------------+
|                                    Workload (CI/CD, K8s, VM, Lambda)                                |
+----------------------------------------------------------------------------------------------------+
                                      |
                                      | 1. Obtain OIDC ID Token (JWT) from Provider
                                      v
+----------------------------------------------------------------------------------------------------+
|                                Identity Provider (GitHub / GitLab / Generic OIDC)                  |
+----------------------------------------------------------------------------------------------------+
                                      |
                                      | 2. POST /api/v1/auth/oidc/token
                                      |    { oidcProviderId, machineIdentityId, idToken }
                                      v
+----------------------------------------------------------------------------------------------------+
|                                  SecretVault Backend                                                |
|                                                                                                    |
|   +-----------------------+     +-----------------------+     +--------------------------------+   |
|   | SsrfSafeHttpClient    | --> | JwksKeyProvider       | --> | JwtValidationEngine            |   |
|   | (Blocks Private IPs)  |     | (JWKS cache + TTL)    |     | (RSA/EC Signature + Expiry/Iss)|   |
|   +-----------------------+     +-----------------------+     +--------------------------------+   |
|                                                                               |                    |
|                                                                               v                    |
|   +--------------------------------------------------------------------------------------------+   |
|   | ClaimRuleEngine (EQUALS, NOT_EQUALS, IN, NOT_IN, PREFIX, SUFFIX, CONTAINS, REGEX)          |   |
|   | Matches extracted claims against OidcTrustPolicy                                           |   |
|   +--------------------------------------------------------------------------------------------+   |
|                                                                               |                    |
|                                                                               v                    |
|   +--------------------------------------------------------------------------------------------+   |
|   | MachineSessionService                                                                      |   |
|   | Generates sv_machine_... bearer token (stores SHA-256 hash in DB, sets TTL)                 |   |
|   +--------------------------------------------------------------------------------------------+   |
+----------------------------------------------------------------------------------------------------+
                                      |
                                      | 3. Returns { accessToken: "sv_machine_...", expiresIn: 3600 }
                                      v
+----------------------------------------------------------------------------------------------------+
|                          Authenticated Secret Operations via Bearer Token                          |
|                                                                                                    |
|   JwtAuthenticationFilter                                                                          |
|     -> Extracts sv_machine_ token                                                                  |
|     -> Resolves MachineSession via SHA-256 hash                                                    |
|     -> Validates session active & machine active                                                   |
|     -> Constructs UserPrincipal (ROLE_MACHINE)                                                     |
|                                                                                                    |
|   EffectiveAccessService                                                                           |
|     -> Single unified authorization path                                                           |
|     -> Evaluates machine_access_grants (WORKSPACE, PROJECT, ENVIRONMENT, SECRET)                   |
|     -> Applies deterministic DENY > ALLOW resolution                                               |
+----------------------------------------------------------------------------------------------------+
```

---

## Component Deep Dive

### 1. Identity & Session Model
- **`MachineIdentity`**: Represents a non-human workload bound to a workspace. Can be in state `ACTIVE`, `DISABLED`, or `REVOKED`.
- **`MachineAccessGrant`**: Defines explicit permissions (`READ_SECRET`, `WRITE_SECRET`, `DELETE_SECRET`, `REVEAL_SECRET`, `LIST_SECRETS`, `ADMIN`) granted to a machine identity at a specific scope (`WORKSPACE`, `PROJECT`, `ENVIRONMENT`, `SECRET`).
- **`MachineSession`**: Tracks active token sessions with SHA-256 hashed tokens, client IP, user agent, expiration time, and last used timestamp.

### 2. OIDC Validation Subsystem
- **`SsrfSafeHttpClient`**: Prevents Server-Side Request Forgery attacks when fetching OpenID configuration or JWKS keys. Explicitly rejects non-HTTPS URLs (unless local testing flag is set), loopback addresses (`127.0.0.0/8`), private RFC 1918 ranges (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), cloud metadata endpoints (`169.254.169.254`), and IPv6 equivalents.
- **`JwksKeyProvider`**: Discovers or loads JWKS keys from OIDC providers, maintaining an in-memory cache with configurable TTL and proactive key rotation.
- **`JwtValidationEngine`**: Verifies JWT header, claims, algorithm (RS256, RS384, RS512, ES256, ES384, ES512), issuer match, audience match, expiration, and cryptographic signature against the provider's JWKS.

### 3. Claim Rule Engine & Trust Policies
- **`ClaimAdapterRegistry`**: Maps provider types to claim adapters:
  - `GitHubActionsClaimAdapter`: Extracts `repository`, `ref`, `actor`, `workflow`, `job_workflow_ref`, `environment`.
  - `GitLabCiClaimAdapter`: Extracts `project_path`, `ref`, `user_login`, `pipeline_source`, `environment`.
  - `GenericOidcClaimAdapter`: Normalizes standard RFC 7519 and nested JSON claims.
- **`ClaimRuleEngine`**: Evaluates individual `OidcClaimRule` conditions against token claims using match operators (`EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`, `PREFIX`, `SUFFIX`, `CONTAINS`, `REGEX`). Trust policies are matched only when all rules in the policy are satisfied (`AND` semantic within a policy).

### 4. Zero Parallel Authorization Engines
Machine requests follow the identical authorization pipeline as human users:
1. `JwtAuthenticationFilter` sets the `SecurityContext` with `UserPrincipal.createMachine(...)` having role `ROLE_MACHINE`.
2. `SecretAuthorizationHelper` delegates secret access evaluation to `EffectiveAccessService.evaluateAccess(...)`.
3. `EffectiveAccessService` checks workspace membership or machine identity validity, loads direct scoped grants, and computes the effective `AccessDecision`.
4. Machines have zero implicit `ADMIN` or `OWNER` rights. All permissions must be explicitly granted via `MachineAccessGrant`.

---

## Data Model & Relationships

```
+------------------------------------+
|            Workspaces              |
+------------------------------------+
       | 1                   | 1
       |                     |
       v *                   v *
+--------------------+ +--------------------+
| Machine Identities | |   Oidc Providers   |
+--------------------+ +--------------------+
       | 1                   | 1
       |                     |
       +----------+----------+
                  |
                  v *
       +--------------------+
       | Oidc Trust Policies|
       +--------------------+
                  | 1
                  v *
       +--------------------+
       |  Oidc Claim Rules  |
       +--------------------+
```

All foreign keys are cascaded or indexed properly, ensuring referential integrity and performant lookup queries.
