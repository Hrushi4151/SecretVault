package metrics

import (
	"sync/atomic"
	"time"
)

// OperatorMetrics records controller-runtime reconciler metrics with strict zero-leakage guarantees.
// Metric labels avoid high cardinality (no secret names or unrestricted workspace IDs as labels).
type OperatorMetrics struct {
	// Reconcile Cycle Counts
	SecretReconcileTotal      uint64
	SecretReconcileSuccess    uint64
	SecretReconcileError      uint64
	SyncReconcileTotal        uint64
	SyncReconcileSuccess      uint64
	SyncReconcileError        uint64
	AuthFailuresTotal         uint64
	RequeuesTotal             uint64
	BackendRequestsTotal      uint64
	BackendRequestErrorsTotal uint64
	LastReconcileDurationMs   int64

	// Phase 13.4 Secret Sync & Lifecycle Metrics
	SecretSyncTotal           uint64
	SecretSyncSuccessTotal    uint64
	SecretSyncFailureTotal    uint64
	SecretVersionChangesTotal uint64
	SecretDriftTotal          uint64
	LeaseCreatedTotal         uint64
	LeaseRenewedTotal         uint64
	LeaseRevokedTotal         uint64
	WorkloadRestartTotal      uint64
	KubernetesSecretWriteTotal uint64
}

// DefaultOperatorMetrics is the global operator metrics instance.
var DefaultOperatorMetrics = &OperatorMetrics{}

// RecordSecretReconcile records a SecretVaultSecret reconciliation cycle result.
func (m *OperatorMetrics) RecordSecretReconcile(success bool, duration time.Duration) {
	atomic.AddUint64(&m.SecretReconcileTotal, 1)
	if success {
		atomic.AddUint64(&m.SecretReconcileSuccess, 1)
	} else {
		atomic.AddUint64(&m.SecretReconcileError, 1)
	}
	atomic.StoreInt64(&m.LastReconcileDurationMs, duration.Milliseconds())
}

// RecordSyncReconcile records a SecretVaultSync reconciliation cycle result.
func (m *OperatorMetrics) RecordSyncReconcile(success bool, duration time.Duration) {
	atomic.AddUint64(&m.SyncReconcileTotal, 1)
	if success {
		atomic.AddUint64(&m.SyncReconcileSuccess, 1)
	} else {
		atomic.AddUint64(&m.SyncReconcileError, 1)
	}
	atomic.StoreInt64(&m.LastReconcileDurationMs, duration.Milliseconds())
}

// RecordAuthFailure increments the controller authentication failure counter.
func (m *OperatorMetrics) RecordAuthFailure() {
	atomic.AddUint64(&m.AuthFailuresTotal, 1)
}

// RecordRequeue increments the scheduled requeue counter.
func (m *OperatorMetrics) RecordRequeue() {
	atomic.AddUint64(&m.RequeuesTotal, 1)
}

// RecordBackendRequest records a call to SecretVault backend API.
func (m *OperatorMetrics) RecordBackendRequest(isError bool) {
	atomic.AddUint64(&m.BackendRequestsTotal, 1)
	if isError {
		atomic.AddUint64(&m.BackendRequestErrorsTotal, 1)
	}
}

// RecordSecretSync records an individual secret synchronization event.
func (m *OperatorMetrics) RecordSecretSync(success bool) {
	atomic.AddUint64(&m.SecretSyncTotal, 1)
	if success {
		atomic.AddUint64(&m.SecretSyncSuccessTotal, 1)
	} else {
		atomic.AddUint64(&m.SecretSyncFailureTotal, 1)
	}
}

// RecordVersionChange records detection of an updated secret version.
func (m *OperatorMetrics) RecordVersionChange() {
	atomic.AddUint64(&m.SecretVersionChangesTotal, 1)
}

// RecordDrift records detection of drift on a managed Kubernetes Secret.
func (m *OperatorMetrics) RecordDrift() {
	atomic.AddUint64(&m.SecretDriftTotal, 1)
}

// RecordLeaseCreated records successful ephemeral lease generation.
func (m *OperatorMetrics) RecordLeaseCreated() {
	atomic.AddUint64(&m.LeaseCreatedTotal, 1)
}

// RecordLeaseRenewed records successful lease extension.
func (m *OperatorMetrics) RecordLeaseRenewed() {
	atomic.AddUint64(&m.LeaseRenewedTotal, 1)
}

// RecordLeaseRevoked records lease teardown.
func (m *OperatorMetrics) RecordLeaseRevoked() {
	atomic.AddUint64(&m.LeaseRevokedTotal, 1)
}

// RecordWorkloadRestart records a rolling restart triggered on a target workload.
func (m *OperatorMetrics) RecordWorkloadRestart() {
	atomic.AddUint64(&m.WorkloadRestartTotal, 1)
}

// RecordKubernetesSecretWrite records create/update/patch on a v1/Secret object.
func (m *OperatorMetrics) RecordKubernetesSecretWrite() {
	atomic.AddUint64(&m.KubernetesSecretWriteTotal, 1)
}

// Snapshot returns a point-in-time dictionary of current metric counters.
func (m *OperatorMetrics) Snapshot() map[string]uint64 {
	return map[string]uint64{
		"secret_reconcile_total":         atomic.LoadUint64(&m.SecretReconcileTotal),
		"secret_reconcile_success":       atomic.LoadUint64(&m.SecretReconcileSuccess),
		"secret_reconcile_error":         atomic.LoadUint64(&m.SecretReconcileError),
		"sync_reconcile_total":           atomic.LoadUint64(&m.SyncReconcileTotal),
		"sync_reconcile_success":         atomic.LoadUint64(&m.SyncReconcileSuccess),
		"sync_reconcile_error":           atomic.LoadUint64(&m.SyncReconcileError),
		"auth_failures_total":            atomic.LoadUint64(&m.AuthFailuresTotal),
		"requeues_total":                 atomic.LoadUint64(&m.RequeuesTotal),
		"backend_requests_total":         atomic.LoadUint64(&m.BackendRequestsTotal),
		"backend_request_errors_total":   atomic.LoadUint64(&m.BackendRequestErrorsTotal),
		"last_reconcile_duration_ms":     uint64(atomic.LoadInt64(&m.LastReconcileDurationMs)),
		"secret_sync_total":              atomic.LoadUint64(&m.SecretSyncTotal),
		"secret_sync_success_total":      atomic.LoadUint64(&m.SecretSyncSuccessTotal),
		"secret_sync_failure_total":      atomic.LoadUint64(&m.SecretSyncFailureTotal),
		"secret_version_changes_total":   atomic.LoadUint64(&m.SecretVersionChangesTotal),
		"secret_drift_total":             atomic.LoadUint64(&m.SecretDriftTotal),
		"lease_created_total":            atomic.LoadUint64(&m.LeaseCreatedTotal),
		"lease_renewed_total":            atomic.LoadUint64(&m.LeaseRenewedTotal),
		"lease_revoked_total":            atomic.LoadUint64(&m.LeaseRevokedTotal),
		"workload_restart_total":         atomic.LoadUint64(&m.WorkloadRestartTotal),
		"kubernetes_secret_write_total":  atomic.LoadUint64(&m.KubernetesSecretWriteTotal),
	}
}
