package metrics

import (
	"sync/atomic"
	"time"
)

// OperatorMetrics records controller-runtime reconciler metrics with strict zero-leakage guarantees.
// Metric labels avoid high cardinality (no secret names or unrestricted workspace IDs as labels).
type OperatorMetrics struct {
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
	}
}
