# SecretVault Kubernetes Operator — Security Threat Model

## 1. Scope & Trust Boundaries

This threat model analyzes the security boundaries, attack vectors, and defense mechanisms for the SecretVault Kubernetes Operator (`secretvault.io/v1alpha1`) and Helm deployment.

```
+-----------------------------------------------------------------------------------+
| [Untrusted / Semi-Trusted] Kubernetes Workload Pods                               |
+-----------------------------------------------------------------------------------+
                                         ▲
                                         │ ServiceAccount Projected Token
                                         ▼
+-----------------------------------------------------------------------------------+
| [Trusted] SecretVault Kubernetes Operator (Non-root, read-only root, least priv) |
+-----------------------------------------------------------------------------------+
                                         ▲
                                         │ Mutual TLS / Machine Session Exchange
                                         ▼
+-----------------------------------------------------------------------------------+
| [Authoritative] SecretVault DevSecOps Control Plane Backend                       |
+-----------------------------------------------------------------------------------+
```

---

## 2. Threat Analysis & Mitigations

### T1: Plaintext Secret Exposure in Kubernetes Storage (etcd)
- **Threat:** Native Kubernetes `v1/Secret` objects contain base64-encoded secret payloads stored in cluster `etcd`. Compromise of etcd or cluster backup exposes secrets.
- **Mitigation:**
  - Cluster administrators **must** enable Kubernetes KMS / etcd encryption-at-rest.
  - Operator stores zero plaintext in CRD status, CRD annotations, labels, logs, metrics, or events.
  - Transient memory buffers in the operator are zeroized immediately after API transmission.

### T2: Malicious Helm Values & Template Injection
- **Threat:** An attacker supplies malformed or hostile Helm values attempting privilege escalation, host mounting, or disabling security contexts.
- **Mitigation:**
  - Strict `values.schema.json` enforces schema constraints, forbidding privileged containers, hostPID, hostNetwork, hostPath, or running as root.
  - Helm templates use strict quoting, escaping, and bounded integer parameters.

### T3: RBAC Privilege Expansion & Cluster Takeover
- **Threat:** An attacker compromises the operator pod and attempts to perform cluster-admin actions (e.g. creating clusterroles, reading kube-system secrets, exec into pods).
- **Mitigation:**
  - Scoped RBAC explicitly grants only `secretvault.io` CRDs, `core/v1` `secrets`, `apps/v1` (`deployments`, `statefulsets`, `daemonsets`), `coordination.k8s.io` `leases`, and `events`.
  - Zero permissions on `pods/exec`, `pods/attach`, `clusterroles`, `clusterrolebindings`, or `serviceaccounts/token`.
  - Support for `operator.scope: namespace` restricts RBAC entirely to single-namespace `Role` and `RoleBinding`.

### T4: Cross-Namespace Exfiltration & Workload Restart Abuse
- **Threat:** A tenant in `namespace-a` creates a `SecretVaultSecret` referencing a destination in `namespace-b`, attempting cross-namespace secret injection or restarting unauthorized workloads.
- **Mitigation:**
  - Reconcilers strictly enforce `target.namespace == resource.namespace`.
  - Workload selector rolling restarts (`RestartWorkload`) strictly match workloads within the CR's local namespace.

### T5: OIDC Workload Token Theft & Replay
- **Threat:** An adversary intercepts a ServiceAccount token and attempts to impersonate a machine identity against SecretVault backend.
- **Mitigation:**
  - Short-lived projected tokens with audience binding (`https://api.secretvault.io`) and bounded expiration.
  - SecretVault backend verifies JWKS signatures, validates OIDC trust policy claims, and issues short-lived machine sessions (600s TTL).
  - Machine tokens are held exclusively in volatile memory and never persisted to disk.

### T6: Network Interception & TLS Downgrade
- **Threat:** Man-in-the-middle (MITM) attacks intercepting communication between the Kubernetes operator and SecretVault backend.
- **Mitigation:**
  - TLS 1.2+ minimum enforced with verified CA certificate validation.
  - `tls.insecureSkipVerify: false` by default; custom CA bundle mounting supported via `tls.caSecretRef`.

### T7: Accidental Secret Deletion & CRD Destruction
- **Threat:** Running `helm uninstall` accidentally destroys production secrets or causes cluster outages.
- **Mitigation:**
  - CRDs are located in `crds/` which Helm does not delete on uninstall.
  - `creationPolicy: Merge` and `creationPolicy: None` guarantee that generated `v1/Secret` objects remain intact even if custom resources are removed.

---

## 3. Responsibility Matrix

| Security Layer | SecretVault Control Plane | Kubernetes Operator | Cluster Administrator |
| :--- | :--- | :--- | :--- |
| **Secret Encryption at Rest (Control Plane)** | ✅ Authoritative (AES-256-GCM / KMS) | N/A | N/A |
| **Secret Encryption at Rest (Kubernetes)** | N/A | N/A | ✅ Mandatory KMS / etcd encryption |
| **Workload Authentication (OIDC)** | ✅ JWKS & Trust Policy Match | ✅ Projected Token Exchange | Configure SA Projected Volumes |
| **Least-Privilege Kubernetes RBAC** | N/A | ✅ Scoped Roles / ClusterRoles | Restrict tenant RBAC |
| **Network Isolation (Zero-Trust)** | ✅ Backend TLS & IP filtering | ✅ Helm NetworkPolicy | CNI NetworkPolicy Support |
