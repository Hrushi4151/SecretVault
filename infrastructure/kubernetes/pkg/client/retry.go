package client

import (
	"crypto/rand"
	"errors"
	"io"
	"math"
	"math/big"
	"net"
	"net/http"
	"net/url"
	"os"
	"syscall"
	"time"
)

// IsRetryableStatus reports whether an HTTP status code represents a transient, retryable condition.
func IsRetryableStatus(statusCode int) bool {
	switch statusCode {
	case http.StatusTooManyRequests, // 429
		http.StatusBadGateway,          // 502
		http.StatusServiceUnavailable, // 503
		http.StatusGatewayTimeout:      // 504
		return true
	default:
		// 400, 401, 403, 404, 500 are non-transient and must NOT be retried in basic loop
		return false
	}
}

// IsRetryableError reports whether an underlying error is transient network or timeout related.
func IsRetryableError(err error) bool {
	if err == nil {
		return false
	}

	if errors.Is(err, io.EOF) || errors.Is(err, io.ErrUnexpectedEOF) {
		return true
	}

	var netErr net.Error
	if errors.As(err, &netErr) {
		if netErr.Timeout() {
			return true
		}
	}

	var opErr *net.OpError
	if errors.As(err, &opErr) {
		return true
	}

	var urlErr *url.Error
	if errors.As(err, &urlErr) {
		if urlErr.Timeout() {
			return true
		}
		var syscallErr *os.SyscallError
		if errors.As(urlErr.Err, &syscallErr) {
			if errors.Is(syscallErr.Err, syscall.ECONNRESET) ||
				errors.Is(syscallErr.Err, syscall.ECONNREFUSED) ||
				errors.Is(syscallErr.Err, syscall.ETIMEDOUT) {
				return true
			}
		}
	}

	return false
}

// CalculateBackoff computes exponential backoff with full jitter to avoid synchronized retry storms.
func CalculateBackoff(attempt int, cfg RetryConfig) time.Duration {
	if attempt <= 0 {
		return cfg.InitialBackoff
	}

	multiplier := math.Pow(cfg.BackoffMultiplier, float64(attempt))
	delayNanos := float64(cfg.InitialBackoff.Nanoseconds()) * multiplier

	maxNanos := float64(cfg.MaxBackoff.Nanoseconds())
	if delayNanos > maxNanos {
		delayNanos = maxNanos
	}

	delay := time.Duration(delayNanos)

	if !cfg.EnableJitter || delay <= 0 {
		return delay
	}

	// Full jitter: uniform random duration between 0 and delay
	nBig, err := rand.Int(rand.Reader, big.NewInt(delay.Nanoseconds()))
	if err != nil {
		// Fallback to 50% fixed jitter on entropy failure
		return delay / 2
	}
	return time.Duration(nBig.Int64())
}
