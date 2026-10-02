# SecretVault — Drift Detection System & Specifications

## 1. Overview

**Drift Detection** in SecretVault continuously compares the authoritative **Desired State** (defined in SecretVault) with the **Actual State** (deployed in external providers like Vercel and Render).

When differences are observed, the system records structured, deduplicated `DriftRecord` entries, computes deterministic severity levels, triggers security intelligence findings when appropriate, and tracks remediation through resolution.

---

## 2. Drift Types & Classification

The Drift Engine identifies nine canonical drift types:

| Drift Type | Description | Default Severity |
| :--- | :--- | :--- |
| `MISSING_FROM_PROVIDER` | A secret defined in SecretVault is missing from the external provider deployment environment. | `HIGH` (Prod) / `MEDIUM` (Staging) / `LOW` (Dev) |
| `EXTRA_IN_PROVIDER` | An unmanaged secret exists in the provider environment that is not tracked by SecretVault. | `MEDIUM` (Prod) / `LOW` (Staging/Dev) |
| `VALUE_MISMATCH` | The secret exists in both SecretVault and the provider, but the values or fingerprints do not match. | `CRITICAL` (Prod) / `HIGH` (Staging) / `LOW` (Dev) |
| `NAME_MISMATCH` | A mapped secret name in SecretVault does not match the provider-side identifier. | `MEDIUM` |
| `ENVIRONMENT_MISMATCH` | A secret is associated with a provider environment that does not match the mapping configuration. | `HIGH` (Prod) / `MEDIUM` (Other) |
| `RESOURCE_MAPPING_MISMATCH` | The target provider project, service, or resource ID is invalid or cannot be resolved. | `HIGH` |
| `PROVIDER_UNAVAILABLE` | The provider API is unreachable, down, or experiencing persistent timeouts. | `MEDIUM` |
| `PERMISSION_DENIED` | Provider credentials/tokens are invalid, expired, or have insufficient scopes to read state. | `HIGH` |
| `UNSUPPORTED` | The provider does not expose sufficient metadata or capabilities for reliable state comparison. | `INFO` |

---

## 3. Deterministic Drift Fingerprinting & Deduplication

To prevent alert fatigue and database bloat from repeated scans, the Drift Engine calculates a deterministic SHA-256 fingerprint for every detected drift instance:

$$\text{Fingerprint} = \text{SHA-256}(\text{workspaceId} + \text{":"} + \text{integrationId} + \text{":"} + \text{mappingId} + \text{":"} + \text{targetIdentifier} + \text{":"} + \text{driftType})$$

### Deduplication Lifecycle
1. **Initial Detection**: A new `DriftRecord` is inserted with status `OPEN`, `occurrenceCount = 1`, and `firstDetectedAt = now`.
2. **Subsequent Detections**: If an open drift with the same fingerprint is detected again, the existing record is updated: `occurrenceCount` is incremented and `lastDetectedAt` is refreshed. No duplicate rows are created.
3. **Automatic Resolution**: When a subsequent scan reveals that the drift condition no longer exists (e.g., following a successful sync or manual fix), the record automatically transitions to `RESOLVED` with `resolvedAt = now` and `resolutionReason = "AUTOMATICALLY_RESOLVED_STATE_MATCH"`.

---

## 4. Drift Severity Matrix

Drift severity is dynamically calculated based on the environment protection tier and drift type:

```
                      ┌──────────────────────────────────────┐
                      │          Drift Detected              │
                      └──────────────────┬───────────────────┘
                                         │
                         Is Environment Production?
                                ┌────────┴────────┐
                                │                 │
                              YES                 NO
                                │                 │
               ┌────────────────┴──────┐   ┌──────┴──────────────┐
               │ VALUE_MISMATCH:       │   │ VALUE_MISMATCH:     │
               │   CRITICAL            │   │   HIGH / LOW        │
               │ MISSING_FROM_PROVIDER:│   │ MISSING_FROM_PROVIDER:
               │   HIGH                │   │   MEDIUM / LOW      │
               │ PERMISSION_DENIED:    │   │ PERMISSION_DENIED:  │
               │   HIGH                │   │   MEDIUM            │
               │ EXTRA_IN_PROVIDER:    │   │ EXTRA_IN_PROVIDER:  │
               │   MEDIUM              │   │   LOW               │
               └───────────────────────┘   └─────────────────────┘
```

---

## 5. Drift Record Lifecycle & State Machine

Drift records progress through a strict, auditable state machine:

```
                   ┌──────────┐
                   │   OPEN   │◄────────┐
                   └────┬─────┘         │
                        │               │ (Re-opened if drift recurs)
          ┌─────────────┼───────────────┤
          ▼             ▼               ▼
   ┌─────────────┐┌────────────┐┌──────────────┐
   │ACKNOWLEDGED ││SYNC_PENDING││   IGNORED    │
   └──────┬──────┘└─────┬──────┘└──────┬───────┘
          │             │              │
          └────────► ┌──┴────────┐ ◄───┘
                     │  RESOLVED │
                     └───────────┘
```

### Valid Status Transitions
- `OPEN` $\to$ `ACKNOWLEDGED`, `SYNC_PENDING`, `IGNORED`, `RESOLVED`
- `ACKNOWLEDGED` $\to$ `SYNC_PENDING`, `IGNORED`, `RESOLVED`
- `SYNC_PENDING` $\to$ `RESOLVED`, `OPEN`, `ERROR`
- `IGNORED` $\to$ `OPEN`, `RESOLVED`
- `RESOLVED` $\to$ `OPEN` (if drift is re-detected later)

---

## 6. Security Intelligence & Audit Integration

- **Security Findings**: When high or critical severity drift is detected (e.g. `VALUE_MISMATCH` in production or `PERMISSION_DENIED` on a provider integration), the Drift Engine creates a `SecurityFinding` in the Phase 6 Security Intelligence Engine.
- **Security Events**: Emits sanitized `DRIFT_DETECTED` and `DRIFT_RESOLVED` security events.
- **Audit Trails**: Every state change, acknowledgment, and resolution is logged to the immutable audit trail.

---

## 7. Drift Detection API Endpoints

### 7.1 Query Drift Records
- `GET /api/v1/workspaces/{workspaceId}/drift`
- **Query Parameters**:
  - `status`: Filter by `DriftStatus` (`OPEN`, `ACKNOWLEDGED`, `RESOLVED`, etc.)
  - `severity`: Filter by `DriftSeverity` (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`)
  - `driftType`: Filter by `DriftType`
  - `projectId`: Filter by project UUID
  - `environmentId`: Filter by environment UUID
  - `integrationId`: Filter by provider integration UUID
  - `page`, `size`, `sort`: Standard pagination and allowlisted sorting

### 7.2 Get Drift Record Details
- `GET /api/v1/workspaces/{workspaceId}/drift/{driftId}`
- Returns full drift details, fingerprint, occurrence history, and resolution metadata.

### 7.3 Update Drift Status
- `PATCH /api/v1/workspaces/{workspaceId}/drift/{driftId}/status`
- **Request Body**:
  ```json
  {
    "status": "ACKNOWLEDGED",
    "resolutionReason": "Investigating manual changes made by platform team"
  }
  ```
