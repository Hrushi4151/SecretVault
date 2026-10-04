package auth

import (
	"errors"
	"fmt"
)

var (
	// ErrTokenFileNotFound is returned when the projected ServiceAccount token file cannot be found.
	ErrTokenFileNotFound = errors.New("projected ServiceAccount token file not found")

	// ErrTokenFileInvalid is returned when the token file is not a regular readable file or exceeds limits.
	ErrTokenFileInvalid = errors.New("projected ServiceAccount token file is invalid or unreadable")

	// ErrTokenExpired is returned when the token's exp claim has already passed.
	ErrTokenExpired = errors.New("projected ServiceAccount token has expired")

	// ErrOidcExchangeFailed is returned when the SecretVault OIDC token exchange endpoint returns an error.
	ErrOidcExchangeFailed = errors.New("SecretVault OIDC token exchange failed")

	// ErrAuthenticationRejected is returned when the backend explicitly rejects authentication (HTTP 401).
	ErrAuthenticationRejected = errors.New("SecretVault workload authentication rejected")

	// ErrAuthorizationDenied is returned when the workload is forbidden from the requested scope (HTTP 403).
	ErrAuthorizationDenied = errors.New("SecretVault workload authorization denied")

	// ErrMachineSessionExpired is returned when an active machine session has expired and cannot be refreshed.
	ErrMachineSessionExpired = errors.New("SecretVault machine session expired")

	// ErrTlsFailure is returned when TLS handshake, certificate validation, or CA pool fails.
	ErrTlsFailure = errors.New("TLS verification or connection failed")

	// ErrNetworkFailure is returned on non-transient or exhausted network errors.
	ErrNetworkFailure = errors.New("network connection to SecretVault failed")

	// ErrRateLimited is returned when the client is rate limited (HTTP 429) after exhausting retries.
	ErrRateLimited = errors.New("SecretVault API rate limit exceeded")

	// ErrInvalidConfiguration is returned when client configuration parameters fail validation.
	ErrInvalidConfiguration = errors.New("invalid SecretVault client configuration")
)

// AuthError represents a typed, safe authentication error that is guaranteed not to leak tokens or credentials.
type AuthError struct {
	Op      string // Operation name (e.g. "read_token", "oidc_exchange", "validate_session")
	Reason  string // High-level, non-sensitive failure reason
	Err     error  // Underlying root error
	SafeMsg string // Human-readable safe message suitable for status/logs
}

func (e *AuthError) Error() string {
	if e.SafeMsg != "" {
		return fmt.Sprintf("SecretVault Auth [%s]: %s", e.Op, e.SafeMsg)
	}
	if e.Err != nil {
		return fmt.Sprintf("SecretVault Auth [%s]: %s (%v)", e.Op, e.Reason, e.Err)
	}
	return fmt.Sprintf("SecretVault Auth [%s]: %s", e.Op, e.Reason)
}

func (e *AuthError) Unwrap() error {
	return e.Err
}

// NewAuthError creates a new safe AuthError without sensitive details.
func NewAuthError(op, reason string, err error) *AuthError {
	return &AuthError{
		Op:      op,
		Reason:  reason,
		Err:     err,
		SafeMsg: reason,
	}
}
