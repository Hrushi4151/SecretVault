package metrics

import (
	"sync/atomic"
	"time"
)

// AuthMetrics holds safe operational counters for Kubernetes authentication and client activity.
// All values are strictly aggregated counters and latency measurements with zero token or secret exposure.
type AuthMetrics struct {
	AuthAttemptsTotal     uint64
	AuthSuccessTotal      uint64
	AuthFailuresTotal     uint64
	SessionRefreshesTotal uint64
	HttpRetriesTotal      uint64
	LastExchangeDuration  int64 // Nanoseconds
}

// DefaultAuthMetrics is a singleton metrics instance for operational tracking.
var DefaultAuthMetrics = &AuthMetrics{}

// RecordAuthAttempt increments the total authentication attempts counter.
func (m *AuthMetrics) RecordAuthAttempt() {
	atomic.AddUint64(&m.AuthAttemptsTotal, 1)
}

// RecordAuthSuccess increments the successful authentication counter and records exchange duration.
func (m *AuthMetrics) RecordAuthSuccess(duration time.Duration) {
	atomic.AddUint64(&m.AuthSuccessTotal, 1)
	atomic.StoreInt64(&m.LastExchangeDuration, duration.Nanoseconds())
}

// RecordAuthFailure increments the failed authentication counter.
func (m *AuthMetrics) RecordAuthFailure() {
	atomic.AddUint64(&m.AuthFailuresTotal, 1)
}

// RecordSessionRefresh increments the session refresh counter.
func (m *AuthMetrics) RecordSessionRefresh() {
	atomic.AddUint64(&m.SessionRefreshesTotal, 1)
}

// RecordHttpRetry increments the HTTP retry counter.
func (m *AuthMetrics) RecordHttpRetry() {
	atomic.AddUint64(&m.HttpRetriesTotal, 1)
}

// Snapshot returns a point-in-time copy of the metrics.
func (m *AuthMetrics) Snapshot() map[string]uint64 {
	return map[string]uint64{
		"auth_attempts_total":      atomic.LoadUint64(&m.AuthAttemptsTotal),
		"auth_success_total":       atomic.LoadUint64(&m.AuthSuccessTotal),
		"auth_failures_total":      atomic.LoadUint64(&m.AuthFailuresTotal),
		"session_refreshes_total":  atomic.LoadUint64(&m.SessionRefreshesTotal),
		"http_retries_total":       atomic.LoadUint64(&m.HttpRetriesTotal),
		"last_exchange_duration_ms": uint64(atomic.LoadInt64(&m.LastExchangeDuration) / int64(time.Millisecond)),
	}
}
