package client

import (
	"fmt"
	"net/http"
	"regexp"
	"strings"
)

// Common error types for Terraform diagnostics.
type APIError struct {
	StatusCode int
	Message    string
	TraceId    string
	RawBody    string
}

func (e *APIError) Error() string {
	cleanMsg := RedactSensitiveInfo(e.Message)
	if e.TraceId != "" {
		return fmt.Sprintf("SecretVault API error (status %d, trace_id %s): %s", e.StatusCode, e.TraceId, cleanMsg)
	}
	return fmt.Sprintf("SecretVault API error (status %d): %s", e.StatusCode, cleanMsg)
}

// IsNotFound returns true if the error indicates a 404 Not Found response.
func IsNotFound(err error) bool {
	if apiErr, ok := err.(*APIError); ok {
		return apiErr.StatusCode == http.StatusNotFound
	}
	return false
}

// IsConflict returns true if the error indicates a 409 Conflict response.
func IsConflict(err error) bool {
	if apiErr, ok := err.(*APIError); ok {
		return apiErr.StatusCode == http.StatusConflict
	}
	return false
}

// IsRateLimited returns true if the error indicates a 429 Too Many Requests response.
func IsRateLimited(err error) bool {
	if apiErr, ok := err.(*APIError); ok {
		return apiErr.StatusCode == http.StatusTooManyRequests
	}
	return false
}

// IsUnauthorized returns true if the error indicates a 401 Unauthorized response.
func IsUnauthorized(err error) bool {
	if apiErr, ok := err.(*APIError); ok {
		return apiErr.StatusCode == http.StatusUnauthorized
	}
	return false
}

// IsForbidden returns true if the error indicates a 403 Forbidden response.
func IsForbidden(err error) bool {
	if apiErr, ok := err.(*APIError); ok {
		return apiErr.StatusCode == http.StatusForbidden
	}
	return false
}

var (
	bearerPattern = regexp.MustCompile(`(?i)bearer\s+[a-zA-Z0-9\-_.]+`)
	secretPattern = regexp.MustCompile(`(?i)(token|secret|password|key|authorization)=["']?[^"'\s&]+["']?`)
)

// RedactSensitiveInfo scrubs tokens, passwords, and private keys from strings before logging or diagnostics.
func RedactSensitiveInfo(input string) string {
	if input == "" {
		return ""
	}
	redacted := bearerPattern.ReplaceAllString(input, "Bearer [REDACTED]")
	redacted = secretPattern.ReplaceAllStringFunc(redacted, func(match string) string {
		parts := strings.SplitN(match, "=", 2)
		if len(parts) == 2 {
			return parts[0] + "=[REDACTED]"
		}
		return match
	})
	return redacted
}
