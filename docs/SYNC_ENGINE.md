# SecretVault — Sync Engine Architecture & Specifications

## 1. Overview & Core Philosophy

The **SecretVault Sync Engine** (Phase 8) provides an automated, deterministic, and cryptographically secure synchronization framework that bridges SecretVault with external cloud and deployment providers (such as Vercel, Render, AWS, and Kubernetes).

### Source of Truth Model
- **SecretVault = Desired State (Source of Truth)**: SecretVault stores the authoritative desired state of all project, environment, and secret definitions. Secrets are versioned, branchable, encrypted with per-secret DEKs (AES-256-GCM), and governed by fine-grained RBAC.
- **Provider = Actual State (Target)**: External providers maintain the actual runtime environment variables or secret stores for running applications.
- **Sync Engine**: Computes the delta (drift) between Desired State and Actual State, formulates execution plans, and reconciles state according to strict safety policies.

```
                 ┌────────────────────────────────┐
                 │          SecretVault           │
                 │    Desired State (Trunk/Env)   │
                 └───────────────┬────────────────┘
                                 │
                                 ▼
                 ┌────────────────────────────────┐
                 │          Sync Engine           │
                 │  - DesiredStateResolver        │
                 │  - ActualStateResolver         │
                 │  - DriftDetector               │
                 │  - SyncPlanningEngine          │
                 │  - SyncExecutionEngine         │
                 └───────────────┬────────────────┘
                                 │ (AES-256 decrypted in-memory only)
                                 ▼
                 ┌────────────────────────────────┐
                 │     Vercel / Render / Cloud    │
                 │          Actual State          │
                 └────────────────────────────────┘
```

---

## 2. Sync Engine Architecture Components

The engine is built around modular, decoupled components conforming to the Open-Closed Principle:

### 2.1 DesiredStateResolver
- Resolves the active secrets within a specified scope (`WORKSPACE`, `PROJECT`, `ENVIRONMENT`, `MAPPING`, or `SECRET`).
- Queries the latest active version on the main trunk branch of each secret.
- Computes deterministic SHA-256 fingerprints of secret names and plaintext values in memory without persisting or logging plaintexts.
- Outputs a collection of immutable `DesiredSecretState` objects.

### 2.2 ActualStateResolver
- Queries external provider adapters via `ProviderAdapterRegistry` using decrypted in-memory credentials.
- Handles connectivity errors, rate limiting (HTTP 429), and authorization revocations gracefully.
- Distinguishes between provider unavailability/permission errors and missing secrets.
- Outputs `ActualStateResult` containing `ProviderSecretState` snapshots and connection health status.

### 2.3 DriftDetectionEngine
- Compares `DesiredSecretState` against `ProviderSecretState` for each resource mapping.
- Identifies drift categories: `MISSING_FROM_PROVIDER`, `EXTRA_IN_PROVIDER`, `VALUE_MISMATCH`, `NAME_MISMATCH`, `ENVIRONMENT_MISMATCH`, `RESOURCE_MAPPING_MISMATCH`, `PROVIDER_UNAVAILABLE`, `PERMISSION_DENIED`, `UNSUPPORTED`.
- Computes deterministic SHA-256 drift fingerprints:
  $$\text{Fingerprint} = \text{SHA-256}(\text{workspaceId} \parallel \text{integrationId} \parallel \text{mappingId} \parallel \text{targetIdentifier} \parallel \text{driftType})$$
- Performs concurrency-safe deduplication: updates existing records, increments occurrence counts, and transitions resolved drift records to `RESOLVED`.

### 2.4 SyncPlanningEngine
- Evaluates detected drift and desired state against the specified `ReconciliationPolicy`.
- Generates a `SyncPlan` composed of ordered `SyncOperationPlan` items.
- Operation types:
  - `CREATE`: Secret exists in SecretVault but is missing in provider.
  - `UPDATE`: Secret value or metadata differs between SecretVault and provider.
  - `DELETE`: Unmanaged secret exists in provider (guarded by conservative deletion policy).
  - `NO_OP`: Desired and actual states are in perfect alignment.
  - `BLOCKED`: Operation cannot proceed due to provider unavailability or policy constraint.
  - `ERROR`: Invalid mapping or resolution failure.

### 2.5 SyncExecutionEngine
- Executes `SyncPlan` operations against provider adapters using ephemeral in-memory decryption.
- Enforces concurrency locking on `(workspaceId, mappingId)` to prevent race conditions.
- Implements bounded retries with exponential backoff on HTTP 429 / transient network timeouts.
- Tracks per-operation execution states (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `SKIPPED`).
- Supports partial failures without blind rollbacks.
- Records structured `SyncJob` and `SyncOperation` history.
- Emits security telemetry events and audit logs.

---

## 3. Reconciliation Policies & Safety Guardrails

| Policy | Behavior | Remote Delete Allowed? |
| :--- | :--- | :--- |
| `DETECT_ONLY` | Detects and records drift without making any provider modifications. | No |
| `SAFE_RECONCILIATION` *(Default)* | Pushes creates and updates to the provider. Ignores or flags extra provider secrets without deleting them. | No |
| `PUSH_SECRETVAULT_TO_PROVIDER` | Authoritative unidirectional sync. Replaces provider state to match SecretVault. Deletions require explicit flag. | With explicit authorization |
| `BIDIRECTIONAL_NOT_ALLOWED` | Enforces that SecretVault is the strict source of truth; prevents reverse sync from provider into SecretVault. | No |

### Delete Safety
- Provider secret deletion is a high-risk operation that can cause application downtime.
- The Sync Engine defaults to **conservative deletion**: unexpected provider secrets (`EXTRA_IN_PROVIDER`) are flagged as drift/security findings but are **NEVER** deleted automatically unless explicit reconciliation policies and user permissions authorize the action.

---

## 4. Concurrency & Idempotency

### Concurrency Protection
- Sync operations acquire an execution lock keyed by `workspaceId:mappingId`.
- Prevents concurrent HTTP write storms against the same provider environment.
- Non-blocking lock checks reject overlapping jobs with `409 Conflict` or queue them safely.
- Database locks are never held across external HTTP provider calls.

### Sync Idempotency
- Repeated sync runs with identical desired and actual states yield `NO_OP` operations (`SyncOperationStatus.SKIPPED`).
- Operations verify state fingerprints before issuing API calls to prevent redundant writes.
- First execution against mismatched provider creates/updates provider variable; second execution without state change yields `NO_OP` with 0 provider mutations and 0 duplicate drift records.
- Each `SyncJob` receives a unique UUID idempotency key.

### 4.1 Provider Failure & Retry Classification
- **HTTP 429 (`PROVIDER_RATE_LIMITED`)**: Bounded retry up to 3 attempts with exponential backoff (`backoffMs * attempt`). Halts safely without infinite retry loops.
- **HTTP 401/403 (`PROVIDER_AUTHENTICATION_FAILED` / `PROVIDER_AUTHORIZATION_FAILED`)**: Never retried blindly; immediately marked as `FAILED` with normalized error code.
- **HTTP 400/422 (`PROVIDER_INVALID_REQUEST`)**: Never retried; classified as validation error.
- **HTTP 5xx / Network Outage (`PROVIDER_UNAVAILABLE`)**: Categorized strictly as `PROVIDER_UNAVAILABLE`, never falsely reported as `MISSING_FROM_PROVIDER`.

### 4.2 Conservative Deletion Safety
- Remote secrets found on the provider that are unmanaged in SecretVault are planned as `SyncOperationType.BLOCKED` with machine-readable error code `UNMANAGED_PROVIDER_SECRET`.
- Zero automated deletion occurs without explicit administrative policy override.

---

## 5. Background Scheduler & Targeted Drift Scans

- **Background Scheduler (`SyncDriftScheduler`)**:
  - Configurable via `secretvault.sync.scheduler.enabled` and `secretvault.sync.scheduler.cron`.
  - Performs bounded workspace batching to prevent CPU/network exhaustion.
  - Scans active integrations and resource mappings on a recurring schedule.
  - Emits security findings when persistent or high-severity drift is discovered in production environments.
- **Targeted & On-Demand Detection**:
  - Scoped drift analysis available for `WORKSPACE`, `PROJECT`, `ENVIRONMENT`, and `MAPPING` targets via `POST /api/v1/workspaces/{wId}/drift/detect`.
  - Post-sync verification automatically triggers targeted drift detection on affected mappings immediately after push execution.

---

## 6. Security Guarantees & Canary Verification

1. **Zero Plaintext Persistence**: Plaintext secret values are never saved to `drift_records`, `sync_jobs`, `sync_operations`, audit logs, or security telemetry.
2. **Zero Credential Exposure**: Provider tokens and API keys are decrypted in-memory using AES-256-GCM only for the duration of the provider request.
3. **Canary Verification**: Test suites assert that canary strings (e.g. `SUPER_SECRET_CANARY_123`) never appear in logs, exceptions, databases, or API responses.
4. **Tenant Isolation**: All sync operations and queries strictly filter by `workspaceId` with verification via `EffectiveAccessService`.

