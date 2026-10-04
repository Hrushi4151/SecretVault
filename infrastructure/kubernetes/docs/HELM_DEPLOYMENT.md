# SecretVault Kubernetes Operator — Helm Deployment Runbook

## 1. Overview & Architecture

The `secretvault-operator` Helm chart packages the SecretVault Kubernetes Operator for production deployments. The operator synchronizes declarative `SecretVaultSecret` and `SecretVaultSync` custom resources with the authoritative SecretVault DevSecOps Secret Management Control Plane.

---

## 2. Prerequisites

1. **Kubernetes Cluster:** Version 1.26+ with standard API capabilities.
2. **KMS / etcd Encryption-at-Rest:** Required for production deployments using native `v1/Secret` synchronization.
3. **Projected ServiceAccount Tokens:** Cluster must support `TokenRequest` projection for OIDC workload identity exchange.
4. **Network Connectivity:** Egress to SecretVault Control Plane backend on port 8080 (or custom configured port/domain via TLS).
5. **Helm:** v3.8.0+.

---

## 3. Installation

### A. Cluster-Wide Deployment (Default)
In cluster-wide mode, a single operator deployment watches and reconciles SecretVault custom resources across all cluster namespaces.

```bash
helm install secretvault-operator ./infrastructure/kubernetes/helm/secretvault-operator \
  --namespace secretvault-system \
  --create-namespace \
  --set secretvault.serverUrl="https://secretvault-backend.secretvault.svc.cluster.local:8080" \
  --set replicaCount=2
```

### B. Single-Namespace Isolated Deployment
For multi-tenant clusters or restricted environments where cluster-wide permissions are forbidden, the operator can be scoped to a single namespace.

```bash
helm install secretvault-operator ./infrastructure/kubernetes/helm/secretvault-operator \
  --namespace payment-service \
  --set operator.scope=namespace \
  --set operator.watchNamespace=payment-service \
  --set secretvault.serverUrl="https://secretvault-backend.secretvault.svc.cluster.local:8080"
```

---

## 4. Security Configuration & Best Practices

### A. Non-Root & Pod Security Standards (PSS)
The operator pod runs with strict non-root and least-privilege security settings:
- `runAsNonRoot: true` (UID: `65532`, GID: `65532`)
- `allowPrivilegeEscalation: false`
- `readOnlyRootFilesystem: true` (ephemeral `/tmp` on memory-backed `emptyDir`)
- `capabilities.drop: ["ALL"]`
- `seccompProfile.type: RuntimeDefault`

### B. Network Policy Isolation
The chart includes a Zero-Trust `NetworkPolicy` (`networkPolicy.enabled: true`):
- **Ingress:** Allows metrics scraping on port 8080.
- **Egress:** Restricts traffic exclusively to CoreDNS (port 53), Kubernetes API Server (ports 443/6443/8443), and SecretVault Backend API (port 8080).

### C. TLS & CA Bundles
Production deployments must use verified HTTPS:
```yaml
secretvault:
  serverUrl: "https://api.secretvault.io"
  tls:
    enabled: true
    insecureSkipVerify: false
    caSecretRef: "secretvault-custom-ca"
```

---

## 5. Upgrade & Rollback Strategy

### A. Upgrades
Helm does not automatically upgrade CustomResourceDefinitions in the `crds/` folder during `helm upgrade`. To upgrade:

1. Update CRDs manually if schema changes were introduced:
   ```bash
   kubectl apply -f ./infrastructure/kubernetes/helm/secretvault-operator/crds/
   ```
2. Upgrade the Helm release:
   ```bash
   helm upgrade secretvault-operator ./infrastructure/kubernetes/helm/secretvault-operator \
     --namespace secretvault-system \
     --reuse-values
   ```

### B. Rollbacks
To roll back to a previous revision:
```bash
helm rollback secretvault-operator <revision-number> --namespace secretvault-system
```
Rolling back the operator deployment preserves existing `SecretVaultSecret`, `SecretVaultSync`, and destination `v1/Secret` resources without disruption.

---

## 6. CRD Lifecycle & Disaster Recovery

- **CRD Protection:** CRD definitions are located in `crds/`. Uninstalling the Helm release via `helm uninstall` **DOES NOT** delete custom resources or CRDs.
- **Explicit Deletion:** Deleting custom resources or CRDs requires explicit `kubectl delete crd` actions by a cluster administrator.
- **Orphan/Merge Policy:** Generated secrets with `creationPolicy: Merge` or `creationPolicy: None` remain in the cluster even if the CRD instance is removed.

---

## 7. Monitoring & Observability

### Key Metrics to Monitor
- `secret_sync_total` / `secret_sync_failure_total`: Secret delivery rate and failure alerts.
- `secret_version_changes_total`: Upstream rotation activity.
- `secret_drift_total`: External drift detections on managed keys.
- `lease_created_total` / `lease_renewed_total` / `lease_revoked_total`: Ephemeral lease lifecycle.
- `workload_restart_total`: Rolling workload restart count on secret rotation.

### Enabling ServiceMonitor for Prometheus Operator
```yaml
metrics:
  enabled: true
  serviceMonitor:
    enabled: true
    interval: 30s
    scrapeTimeout: 10s
```

---

## 8. Troubleshooting Guide

| Symptom | Diagnostic Step | Remediation |
| :--- | :--- | :--- |
| **`AuthenticationFailed`** | `kubectl describe svs <name>` | Verify ServiceAccount projected token exists and OIDC trust policy is registered in SecretVault. |
| **`AuthorizationDenied`** | Check operator logs for `403` | Confirm MachineIdentity has access grants for the requested Workspace/Project/Environment. |
| **`SecretNotFound`** | `kubectl get svs <name> -o yaml` | Verify `spec.secretName` and `spec.version` exist in the upstream SecretVault environment. |
| **`DriftDetected`** | Inspect `status.driftSummary` | Check if external processes modified the target Kubernetes Secret. Under `DriftPolicy: Enforce`, the operator restores SecretVault state automatically. |
| **`BackendUnavailable`** | Check network policy & DNS | Verify SecretVault API URL is reachable from the operator pod namespace. |
