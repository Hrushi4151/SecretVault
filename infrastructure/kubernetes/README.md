# SecretVault — Kubernetes CustomResourceDefinitions (CRDs) & Resource Contracts

## 1. Overview & Architectural Role

This directory contains the official Kubernetes Custom Resource Definitions (CRDs) and Go API specifications (`secretvault.io/v1alpha1`) for the **SecretVault Kubernetes Integration**.

These resources establish the declarative desired-state contract between Kubernetes clusters and the authoritative SecretVault DevSecOps Secret Management Control Plane.

> [!IMPORTANT]
> **CRDs Represent Desired State Only:**
> These CRDs define location references, filtering rules, lease configurations, and synchronization policies. They **NEVER** contain plaintext secret values. Actual reconciliation, OIDC workload identity authentication, and secret decryption are executed by the SecretVault Kubernetes Operator in future Phase 13 milestones.

---

## 2. API Group & Versioning

- **API Group:** `secretvault.io`
- **API Version:** `v1alpha1`
- **Scope:** `Namespaced`
- **Go Package:** `github.com/secretvault/operator/api/v1alpha1`

### Why `v1alpha1`?
Initial declarative contracts are established under `v1alpha1` to allow ecosystem feedback on reconciliation semantics, rotation policies, and workload identity bindings before graduating to `v1beta1` and `v1`.

---

## 3. Custom Resource Definitions

### A. `SecretVaultSecret` (`secretvaultsecrets.secretvault.io`)
- **Short Name:** `svs`
- **Purpose:** Represents a desired reference to a single, specific secret managed in SecretVault.
- **Spec Highlights:**
  - `workspace`: Target SecretVault Workspace slug or UUID (Required).
  - `project`: Target SecretVault Project slug or UUID (Required).
  - `environment`: Target Environment tier (`development`, `staging`, `production`) (Required).
  - `secretName`: Secret key name in SecretVault (e.g., `DB_PASSWORD`, `STRIPE_API_KEY`) (Required).
  - `version`: Optional pinned integer version (e.g. `3`).
  - `versionPolicy`: Version tracking policy (`LATEST` or `PINNED`, default: `LATEST`).
  - `auth`: Optional OIDC workload identity references (`serviceAccountRef`, `machineIdentity`, `providerId`).
  - `target`: Destination Kubernetes Secret name and key (`creationPolicy: Owner`).
  - `refreshInterval`: Version check cadence (e.g., `1h`, `30m`).
  - `lease`: Ephemeral lease configuration (`enableLease: true`, `ttl: "3600s"`, `consumerType: CONTAINER`).
- **Status Highlights:**
  - Standard Kubernetes `conditions` (`Ready`, `Synced`, `Error`).
  - `observedGeneration`, `lastSyncTime`, `currentVersion`.
  - `secretFingerprint`: Non-sensitive SHA-256 fingerprint hash (no plaintext).
  - `leaseId`, `leaseExpiresAt`.

### B. `SecretVaultSync` (`secretvaultsyncs.secretvault.io`)
- **Short Name:** `svsync`
- **Purpose:** Represents an environment-wide bulk synchronization policy into a single aggregated Kubernetes `v1/Secret`.
- **Spec Highlights:**
  - `workspace`, `project`, `environment`: SecretVault tenant context (Required).
  - `target`: Destination Kubernetes `v1/Secret` configuration (`secretName`, `creationPolicy: Owner`, `template`).
  - `filter`: Optional whitelisting (`includeKeys`), blacklisting (`excludeKeys`), and tag filters (`tags`).
  - `refreshInterval`: Drift detection and refresh interval (default: `15m`).
  - `rotationPolicy`: Action to take upon upstream secret rotation (`RestartWorkload`, `NotifyOnly`, `SyncOnly`) with optional `workloadSelector`.
  - `driftPolicy`: Reconciliation behavior upon external modification (`Enforce`, `DetectOnly`, `Ignore`).
- **Status Highlights:**
  - `conditions` (`Ready`, `Synced`, `DriftDetected`, `Error`).
  - `syncedSecretCount`, `driftDetected`, `driftSummary`.

---

## 4. Security & Zero-Trust Invariants

1. **Zero Plaintext in Manifests:**
   Plaintext secrets, DEKs, master keys, and private tokens **NEVER** exist in CRD specs, statuses, annotations, labels, or events.
2. **Authoritative Backend Security:**
   All authentication and access control decisions are strictly evaluated by the SecretVault backend via `EffectiveAccessService` (RBAC + Environment Scopes + JIT grants).
3. **OIDC Workload Identity Integration:**
   Authentication uses Kubernetes ServiceAccount projected tokens exchanged dynamically via `POST /api/v1/oidc/auth/exchange` for short-lived machine tokens (600s TTL). No static permanent credentials are required.
4. **Namespace Boundary Protection:**
   CRDs are strictly `Namespaced` to isolate tenants and prevent cross-namespace secret exfiltration.

---

## 5. Usage Examples

### Example 1: Basic Secret Reference
```yaml
apiVersion: secretvault.io/v1alpha1
kind: SecretVaultSecret
metadata:
  name: database-credentials
  namespace: payment-service
spec:
  workspace: default
  project: payment-gateway
  environment: production
  secretName: DB_PASSWORD
  refreshInterval: 1h
  auth:
    serviceAccountRef:
      name: payment-service-sa
  target:
    name: payment-db-secret
    key: password
    creationPolicy: Owner
```

### Example 2: Bulk Environment Synchronization
```yaml
apiVersion: secretvault.io/v1alpha1
kind: SecretVaultSync
metadata:
  name: payment-environment-sync
  namespace: payment-service
spec:
  workspace: default
  project: payment-gateway
  environment: production
  refreshInterval: 15m
  driftPolicy: Enforce
  auth:
    serviceAccountRef:
      name: payment-service-sa
  target:
    secretName: payment-app-env
    creationPolicy: Owner
```

---

## 6. Directory Structure

```
infrastructure/kubernetes/
├── api/
│   └── v1alpha1/
│       ├── groupversion_info.go
│       ├── secretvaultsecret_types.go
│       └── secretvaultsync_types.go
├── config/
│   ├── crd/
│   │   ├── bases/
│   │   │   ├── secretvault.io_secretvaultsecrets.yaml
│   │   │   └── secretvault.io_secretvaultsyncs.yaml
│   │   └── kustomization.yaml
│   └── samples/
│       ├── secretvault_v1alpha1_secretvaultsecret.yaml
│       ├── secretvault_v1alpha1_secretvaultsecret_pinned.yaml
│       ├── secretvault_v1alpha1_secretvaultsync.yaml
│       └── secretvault_v1alpha1_secretvaultsync_filtered.yaml
├── test/
│   └── crd_validation_test.js
├── go.mod
└── README.md
```

---

## 7. Next Milestones (Phase 13 Roadmap)
- **Phase 13.2:** Kubernetes Workload OIDC Authentication & Projected Token Client.
- **Phase 13.3:** Kubernetes Operator Reconciler Core (leader election, event watchers).
- **Phase 13.4:** Secret Synchronization, Ephemeral Leases & Dynamic Rotation Workload Restarts.
- **Phase 13.5:** Production Helm Charts & Hardening.
