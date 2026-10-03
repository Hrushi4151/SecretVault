# SecretVault Phase 12 Architecture Audit

## 1. Executive Summary

Phase 12 transitions SecretVault from a passive secret storage and retrieval system into an active, zero-downtime secret lifecycle management platform. This document audits the existing architecture across Phases 1–11, identifies reusable components, maps concrete integration points, documents technical gaps and risks, and establishes the database and component migration plan.

---

## 2. Existing Architecture Audit (Phases 1–11)

### 2.1 Multi-Tenant Hierarchy & Core Data Model (Phases 1–4)
- **Entities:**
  - `Workspace` (`workspaces`): Root tenant container with role-based access (`OWNER`, `ADMIN`, `MEMBER`).
  - `Project` (`projects`): Scoped application or service domain within a workspace.
  - `Environment` (`environments`): Target execution tier (`DEVELOPMENT`, `STAGING`, `PRODUCTION`) with protection rules.
  - `Secret` (`secrets`): Named secret container within an environment, tracking `current_version_number` and status (`ACTIVE`, `DISABLED`, `DELETED`).
  - `SecretVersion` (`secret_versions`): Immutable envelope-encrypted version record storing `ciphertext`, wrapped DEK, IV, auth tag, `key_reference`, `version_number`, and `version_type` (`INITIAL`, `VALUE_UPDATE`, `ROLLBACK`, `PROMOTION`, `ROTATION`).

### 2.2 Cryptographic Envelope Encryption & KMS (Phase 3)
- **Service:** `EncryptionService` / `AesGcmEnvelopeEncryptionService`.
- **Properties:**
  - AES-256-GCM authenticated encryption with 96-bit random IVs and 128-bit authentication tags.
  - Unique data encryption keys (DEKs) per version wrapped with the active master key reference (`key_reference`).
  - Strict Authenticated Additional Data (`AAD`) binding format: `secretId:environmentId:versionNumber`.
  - In-memory decryption only; zero persistent plaintext.

### 2.3 Centralized Authorization Engine (Phase 5)
- **Service:** `EffectiveAccessService`.
- **Invariants:**
  - Single authoritative policy enforcement engine combining workspace membership, project access, environment access, fine-grained `AccessGrant` records (`ALLOW` / `DENY`), and active `JitAccessRequest` temporary elevations.
  - Granular permissions model in `AccessPermission` (`secret.read`, `secret.reveal`, `secret.create`, `secret.update`, `secret.rollback`, `access.manage`, `jit.request`, `jit.approve`).
  - Defense-in-depth: Tenant checks, project checks, environment boundaries, and object-level scoping.

### 2.4 Machine Identity & Workload OIDC Authentication (Phase 9)
- **Entities:** `MachineIdentity`, `MachineSession`, `OidcProvider`, `OidcTrustPolicy`, `OidcClaimRule`, `MachineAccessGrant`.
- **Mechanics:**
  - Short-lived (600s TTL) session tokens issued via cryptographic OIDC JWT exchange at `/api/v1/auth/oidc/token`.
  - Granular `MachineAccessGrant` permissions bound to workspace, project, or environment scopes.
  - Complete integration with `EffectiveAccessService`.

### 2.5 SecretVault CLI & Process Execution (Phase 10)
- **Module:** `cli/` (`com.secretvault.cli`).
- **Features:** Human & machine authentication, context management, secure process runtime injection (`secretvault run`), `.env` sync.

### 2.6 SecretVault Java 21 SDK & Spring Boot Starter (Phase 11)
- **Modules:** `sdk/secretvault-sdk-core`, `sdk/secretvault-spring-boot-starter`.
- **Features:**
  - Safe `SecretValue` with string redaction.
  - Bounded multi-tenant composite in-memory LRU cache.
  - Single-flight `RequestCoalescer` and `CircuitBreaker`.
  - Spring `${secretvault:KEY}` property post-processor and `@SecretVaultValue` bean injection.
  - Redacted Actuator health check and OpenTelemetry metrics.

---

## 3. Reusable Components & Direct Integration Points

| Component / Subsystem | Location | Phase 12 Integration Role |
|---|---|---|
| `EncryptionService` | `com.secretvault.encryption.service` | Encrypts new secret versions generated during rotation with fresh DEK and AAD binding. |
| `SecretService` / `SecretRepository` | `com.secretvault.secret.service` | Manages container status, increments version numbers, and retrieves secret metadata. |
| `SecretVersionRepository` | `com.secretvault.secret.repository` | Persists new immutable `ROTATION` and `ROLLBACK` versions. |
| `EffectiveAccessService` | `com.secretvault.access.service` | Evaluates rotation authorization, lease issuance, consumer registration, and emergency actions. |
| `AuditService` | `com.secretvault.audit.service` | Records all rotation, lease, and consumer lifecycle actions with zero plaintext leakage. |
| `SecurityPostureService` / `SecurityEventService` | `com.secretvault.security.*` | Emits findings for unrotated secrets, overdue rotations, expired leases, and stale consumers. |
| `ProviderIntegrationService` | `com.secretvault.provider.service` | Integrates third-party platforms (AWS, GCP, Vault, GitHub, Render) for external credential rotation. |
| `RedisRateLimiter` | `com.secretvault.common.ratelimit` | Ephemeral distributed locking for rotation jobs and rate limiting for refresh/lease endpoints. |
| `SecretVaultClient` (SDK) | `io.secretvault.sdk.api` | Consumes rotated versions, registers consumer heartbeats, and manages leases. |

---

## 4. Architectural Gaps to Bridge in Phase 12

1. **Rotation Policy & State Machine:** Need persistent domain model for rotation policies, scheduling, execution jobs, and a 14-state lifecycle state machine with optimistic concurrency control.
2. **Pluggable Secret Generation Engine:** Need cryptographically secure generator supporting strings, passwords, API keys, tokens, certificates, and database credentials.
3. **Targeted Rotation Providers:** Need dedicated rotators (e.g., `DatabaseRotator`, `ApiKeyRotator`, `GenericHttpRotator`, `ProviderCredentialRotator`).
4. **Secret Leases & Consumer Workload Registry:** Need runtime lease management (`SecretLease`) with TTL renewal and workload dependency graph (`SecretConsumer`, `SecretDependency`).
5. **Zero-Downtime Rollout & Grace Period:** Need staged activation, dual-credential grace periods, and automated old-version revocation.
6. **Compromised Secret Workflow & Emergency Rotation:** Need one-click incident response triggering immediate rotation and lease invalidation.
7. **Rotation Center UI:** Need a dedicated frontend interface for policy configuration, active job monitoring, impact graphs, and leases.

---

## 5. Risks & Mitigations

| Risk | Impact | Mitigation Strategy |
|---|---|---|
| **Concurrent Rotation Race Conditions** | Duplicate versions or conflicting state transitions | Distributed locking via Redis/PostgreSQL + JPA `@Version` optimistic locking on rotation jobs. |
| **Consumer Outage During Rotation** | Application downtime if old secret is revoked prematurely | Enforce Staged Rollout + Dual-Credential Grace Period + Health Check Validation before old version revocation. |
| **Refresh Storm (Thundering Herd)** | Backend overload when 500+ consumers detect rotation | Jittered SDK polling + local single-flight coalescing + bounded rate limiting. |
| **Plaintext Leakage in Logs / Audit** | Security breach of newly generated credentials | Strict redaction in `AuditService`, `RedactionUtil`, and masking across all toString and exception models. |
| **Lease Replay Attacks** | Unauthorized prolonged secret access | Server-side lease ID validation + bound to authenticated machine identity + TTL expiry checks. |

---

## 6. Migration Plan

1. **Flyway Migration `V15__secret_rotation_leases_consumers.sql`:**
   - Create `rotation_policies`, `rotation_jobs`, `rotation_attempts`, `rotation_validations`, `rotation_deployments`.
   - Create `secret_leases`, `secret_consumers`, `secret_dependencies`.
   - Add foreign keys, indexes, check constraints, and unique constraints.
2. **Backend Domain & Services:**
   - Implement rotation models, generation engine, state machine, providers, validation, and lease/consumer services.
   - Wire authorization into `EffectiveAccessService` with new `AccessPermission` values.
   - Register audit actions and Security Center detection rules.
3. **SDK & CLI Extensions:**
   - Add lease and consumer APIs to Java SDK and Spring Boot Starter.
   - Add CLI subcommands (`secretvault rotation`, `secretvault lease`, `secretvault consumer`).
4. **Frontend Rotation Center:**
   - Implement Rotation Dashboard, Policies, Jobs, Wizard, Impact Graph, and Lease/Consumer management.
