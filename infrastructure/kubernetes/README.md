# SecretVault — Kubernetes CustomResourceDefinitions (CRDs) & Operator Architecture

## 1. Overview & Architectural Role

This directory contains the official Kubernetes Custom Resource Definitions (CRDs), Go API specifications (`secretvault.io/v1alpha1`), and Controller-Runtime Operator for the **SecretVault Kubernetes Integration**.

These components establish a declarative desired-state synchronization and lifecycle management engine between Kubernetes clusters and the authoritative SecretVault DevSecOps Secret Management Control Plane.

```
+-----------------------------------------------------------------------------------+
|                            SecretVault Control Plane                              |
|   (Authoritative RBAC, Tenant Scopes, Secret Storage, Leases, Rotation, Audit)    |
+-----------------------------------------------------------------------------------+
                                         ▲
                                         │ TLS 1.2+ (HTTPS)
                                         │ Machine Session Auth (sv_machine_...)
                                         ▼
+-----------------------------------------------------------------------------------+
|                        SecretVault Kubernetes Operator                            |
|                                                                                   |
|  +-------------------------------------+  +------------------------------------+  |
|  |    SecretVaultSecretReconciler      |  |     SecretVaultSyncReconciler      |  |
|  |                                     |  |                                    |  |
|  | - Metadata-First Version Check      |  | - Bulk Scope Secret Listing        |  |
|  | - Target v1/Secret Sync             |  | - Include/Exclude/Tag Filters      |  |
|  | - CreationPolicy: Owner/Merge/Orphan|  | - Drift Detection & Remediation    |  |
|  | - Ephemeral Lease Lifecycle         |  | - Rolling Workload Restart         |  |
|  | - Immediate Plaintext Scrubbing     |  | - Restart Loop Prevention          |  |
|  +-------------------------------------+  +------------------------------------+  |
+-----------------------------------------------------------------------------------+
                                         │
                                         ▼ Kubernetes API Server
+-----------------------------------------------------------------------------------+
|                              Kubernetes Namespace                                 |
|                                                                                   |
|  [v1/Secret]                    [apps/v1 Deployment / StatefulSet / DaemonSet]   |
|  - Encrypted in etcd via KMS    - Pod Template Annotation: secretvault.io/revision|
|  - Preserved unrelated keys     - Zero plaintext in workload annotations          |
+-----------------------------------------------------------------------------------+
```

---

## 2. Critical Security Model: Native Kubernetes Secrets

> [!WARNING]
> ### Honest Security Notice: Native Kubernetes Secret Storage
> Native Kubernetes `v1/Secret` synchronization persists base64-encoded secret payloads in Kubernetes `etcd` storage.
>
> **Native Kubernetes Secret synchronization is NOT equivalent to zero-plaintext-at-rest storage.**
>
> To achieve defense-in-depth in production when using native Kubernetes Secrets:
> 1. **Kubernetes KMS / Encryption-at-Rest:** The Kubernetes cluster control plane **MUST** have etcd encryption-at-rest configured with a hardware security module (HSM) or Cloud KMS provider (e.g. AWS KMS, GCP KMS, Azure Key Vault).
> 2. **Least-Privilege RBAC:** Access to `v1/Secret` objects in target namespaces must be strictly restricted to authorized pods via dedicated `ServiceAccount` tokens.
> 3. **Memory Safety & Zero Leakage:** The SecretVault operator only holds plaintext in transient memory buffers during the exact instant of transmission and releases references immediately. Plaintext values are **NEVER** written to disk, CRD status, CRD annotations, labels, logs, events, or metrics.

---

## 3. Custom Resource Definitions

### A. `SecretVaultSecret` (`secretvaultsecrets.secretvault.io`)
- **Short Name:** `svs`
- **Purpose:** Synchronizes a single secret reference into a destination Kubernetes `v1/Secret` and optionally manages runtime ephemeral leases.
- **Specification:**
  - `workspace`: Target SecretVault Workspace slug or UUID (Required).
  - `project`: Target SecretVault Project slug or UUID (Required).
  - `environment`: Target Environment tier (`development`, `staging`, `production`) (Required).
  - `secretName`: Secret key name in SecretVault (Required).
  - `version`: Optional pinned integer version (e.g. `3`).
  - `versionPolicy`: Version tracking policy (`LATEST` or `PINNED`, default: `LATEST`).
  - `auth`: Optional OIDC workload identity configuration (`serviceAccountRef`, `machineIdentity`, `providerId`).
  - `target`: Destination Kubernetes Secret configuration (`name`, `key`, `creationPolicy`).
  - `refreshInterval`: Version evaluation interval (e.g., `1h`, `30m`).
  - `lease`: Ephemeral lease configuration (`enableLease: true`, `ttl: "3600s"`, `consumerType: CONTAINER`, `autoRenew: true`).
- **Status Subresource (Zero-Plaintext):**
  - Conditions: `Ready`, `Synced`, `Error`.
  - `observedGeneration`, `lastSyncTime`, `currentVersion`.
  - `secretFingerprint`: Non-sensitive SHA-256 fingerprint hash (no plaintext).
  - `leaseId`: Active SecretVault lease UUID.
  - `leaseExpiresAt`: Expiration timestamp of the active lease.
  - `targetSecretRef`: Reference to generated Kubernetes Secret (`name`, `namespace`, `uid`, `resourceVersion`).

### B. `SecretVaultSync` (`secretvaultsyncs.secretvault.io`)
- **Purpose:** Performs bulk environment secret synchronization into an aggregated Kubernetes `v1/Secret` with automated drift detection and rolling workload restarts.
- **Specification:**
  - `workspace`, `project`, `environment`: SecretVault tenant context (Required).
  - `target`: Destination Kubernetes `v1/Secret` details (`secretName`, `creationPolicy`, `template`).
  - `filter`: Whitelisting (`includeKeys`), blacklisting (`excludeKeys`), and tag filters (`tags`).
  - `refreshInterval`: Synchronization cadence (default: `15m`).
  - `rotationPolicy`: Remediating actions on rotation (`RestartWorkload`, `NotifyOnly`, `SyncOnly`) with `workloadSelector`.
  - `driftPolicy`: Reconciliation behavior on external modifications (`Enforce`, `DetectOnly`, `Ignore`).
- **Status Subresource:**
  - Conditions: `Ready`, `Synced`, `DriftDetected`, `Error`.
  - `syncedSecretCount`: Number of synchronized secrets.
  - `driftDetected`, `driftSummary`: Safe diagnostic summaries.

---

## 4. Creation Policies & Key Preservation

| Policy | Generated Kubernetes Secret Ownership | Behavior on CR Deletion | Unrelated Keys Handling |
| :--- | :--- | :--- | :--- |
| **`Owner`** (Default) | Controller ownerReference attached. | Kubernetes Garbage Collection automatically deletes the Secret. | SecretVault-managed keys are synced; unmanaged keys may be overwritten on full update. |
| **`Merge`** | No ownerReference attached. | Deleting the CR preserves the Secret. | **Strictly Preserved:** Only SecretVault-managed keys are modified; all other external keys remain intact. |
| **`None` / `Orphan`** | No ownerReference attached. | Deleting the CR preserves the Secret. | Manages specified keys without claiming lifecycle ownership of the Secret object. |

---

## 5. Ephemeral Lease Lifecycle Integration

When `lease.enableLease: true` is configured:
1. **Creation:** Upon synchronization, the operator invokes `POST /api/v1/workspaces/{workspaceId}/leases` registering a `SecretConsumer` of type `CONTAINER` (or `SERVICE`, `WORKER`, `JOB`).
2. **Renewal:** The operator monitors `leaseExpiresAt`. When the remaining TTL falls below the safety margin (50% of TTL or 15 minutes), the operator automatically renews the lease via `POST /api/v1/workspaces/{workspaceId}/leases/{leaseId}/renew`.
3. **Revocation:** When the `SecretVaultSecret` resource is deleted from Kubernetes, the operator's finalizer (`secretvault.io/finalizer`) revokes the active lease via `DELETE /api/v1/workspaces/{workspaceId}/leases/{leaseId}`.
4. **Zero Exposure:** Lease tokens and secret values are never stored in lease metadata.

---

## 6. Secret Rotation & Workload Rolling Restart

When upstream secrets rotate in SecretVault:
1. **Detection:** The operator detects the new secret version via metadata-first evaluation.
2. **Version Update:** The operator retrieves the updated secret value and updates the target Kubernetes `v1/Secret`.
3. **Rolling Restart (`RestartWorkload`):**
   - The operator locates target `apps/v1` `Deployment`, `StatefulSet`, or `DaemonSet` resources matching `workloadSelector` **strictly in the same namespace**.
   - The operator updates the pod template annotation:
     ```yaml
     spec:
       template:
         metadata:
           annotations:
             secretvault.io/revision: "<timestamp-nanos>"
     ```
   - Kubernetes triggers an automated rolling update of pods with zero downtime.
4. **Restart-Loop Prevention:** Workloads are only restarted if the secret revision actually changed. Redundant reconciliations will never cause continuous restart loops.

---

## 7. Drift Detection Policies

The operator periodically compares target Kubernetes Secret keys against expected SecretVault state:

- **`Enforce` (Default):** Restores the authoritative SecretVault secret data, overwriting unauthorized external modifications.
- **`DetectOnly`:** Sets the `DriftDetected: True` status condition and emits a warning event without modifying the Kubernetes Secret.
- **`Ignore`:** Ignores differences between the Kubernetes Secret and SecretVault.

---

## 8. Least-Privilege RBAC Configuration

The operator requires minimal scoped permissions:
- **`secretvault.io` (CRDs):** `get`, `list`, `watch`, `create`, `update`, `patch`, `delete` on `secretvaultsecrets`, `secretvaultsyncs`, and their status subresources.
- **`core/v1` (`secrets`):** `get`, `list`, `watch`, `create`, `update`, `patch`, `delete` in managed namespaces.
- **`apps/v1` (`deployments`, `statefulsets`, `daemonsets`):** `get`, `list`, `watch`, `patch`, `update` for rolling workload restarts.
- **`coordination.k8s.io` (`leases`):** `get`, `list`, `watch`, `create`, `update`, `patch`, `delete` for leader election.
- **`core/v1` (`events`):** `create`, `patch` for event reporting.

---

## 9. Performance & Optimization Model

1. **Metadata-First Version Checking:** The operator queries lightweight secret metadata (`GetSecretMetadata`) before deciding whether to decrypt. If the version is unchanged and the target Secret is intact, no reveal is executed, preventing audit spam and backend load.
2. **Bulk Synchronization Optimization:** During bulk sync (`SecretVaultSync`), secrets are filtered using whitelists (`includeKeys`), blacklists (`excludeKeys`), and tags before retrieval. Only modified secrets are decrypted.
3. **Bounded Memory Footprint:** Secret buffers are explicitly zeroized in memory immediately after transmitting to the Kubernetes API.
4. **Optimistic Concurrency:** Kubernetes `resourceVersion` conflicts are handled cleanly with exponential backoff and retry.

---

## 10. Phase Roadmap Status
- [x] **Phase 13.1:** Kubernetes CRDs & Resource Contracts (`SecretVaultSecret`, `SecretVaultSync`).
- [x] **Phase 13.2:** Kubernetes Workload OIDC Authentication & Projected Token Client.
- [x] **Phase 13.3:** Kubernetes Operator Reconciler Core (manager, leader election, events).
- [x] **Phase 13.4:** Kubernetes Secret Synchronization, Ephemeral Leases & Dynamic Rotation.
- [ ] **Phase 13.5:** Production Helm Charts & Hardening (Deferred).
- [ ] **Phase 13.6:** Production Terraform Provider (Deferred).
- [ ] **Phase 13.7:** End-to-End Testing & Certification (Deferred).
