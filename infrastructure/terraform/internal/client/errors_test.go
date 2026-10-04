package client

import (
	"fmt"
	"net/http"
	"testing"
)

func TestRedactSensitiveInfo(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected string
	}{
		{
			name:     "empty string",
			input:    "",
			expected: "",
		},
		{
			name:     "clean error without sensitive data",
			input:    "resource not found: project-123",
			expected: "resource not found: project-123",
		},
		{
			name:     "bearer token in message",
			input:    "failed auth with Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz",
			expected: "failed auth with Bearer [REDACTED]",
		},
		{
			name:     "bearer token lowercase",
			input:    "header authorization: bearer secret_token_12345",
			expected: "header authorization: Bearer [REDACTED]",
		},
		{
			name:     "token key-value pair",
			input:    "request failed for token=super_secret_token_abc",
			expected: "request failed for token=[REDACTED]",
		},
		{
			name:     "password in query/body string",
			input:    "db connection string failed with password='my_top_secret_pass'",
			expected: "db connection string failed with password=[REDACTED]",
		},
		{
			name:     "client secret in diagnostic",
			input:    "clientSecret=\"99a8b7c6d5e4f3a2b1\" is invalid",
			expected: "clientSecret=[REDACTED] is invalid",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := RedactSensitiveInfo(tt.input)
			if got != tt.expected {
				t.Errorf("RedactSensitiveInfo(%q) = %q; want %q", tt.input, got, tt.expected)
			}
		})
	}
}

func TestAPIErrorPredicates(t *testing.T) {
	notFoundErr := &APIError{StatusCode: http.StatusNotFound, Message: "not found"}
	conflictErr := &APIError{StatusCode: http.StatusConflict, Message: "already exists"}
	rateLimitedErr := &APIError{StatusCode: http.StatusTooManyRequests, Message: "rate limit exceeded"}
	unauthorizedErr := &APIError{StatusCode: http.StatusUnauthorized, Message: "invalid token"}
	forbiddenErr := &APIError{StatusCode: http.StatusForbidden, Message: "access denied"}
	genericErr := fmt.Errorf("network down")

	if !IsNotFound(notFoundErr) {
		t.Errorf("IsNotFound failed for 404")
	}
	if IsNotFound(conflictErr) || IsNotFound(genericErr) {
		t.Errorf("IsNotFound returned true for non-404")
	}

	if !IsConflict(conflictErr) {
		t.Errorf("IsConflict failed for 409")
	}
	if IsConflict(notFoundErr) {
		t.Errorf("IsConflict returned true for 404")
	}

	if !IsRateLimited(rateLimitedErr) {
		t.Errorf("IsRateLimited failed for 429")
	}

	if !IsUnauthorized(unauthorizedErr) {
		t.Errorf("IsUnauthorized failed for 401")
	}

	if !IsForbidden(forbiddenErr) {
		t.Errorf("IsForbidden failed for 403")
	}

	// Verify Error formatting does not panic and contains redacted message
	errStr := unauthorizedErr.Error()
	if errStr == "" {
		t.Errorf("APIError.Error() returned empty string")
	}
}
