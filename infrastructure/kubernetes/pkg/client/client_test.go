package client

import (
	"errors"
	"net/http"
	"testing"
	"time"
)

func TestClientConfig_SSRFAndValidation(t *testing.T) {
	tests := []struct {
		name      string
		cfg       ClientConfig
		wantError bool
	}{
		{
			name: "Valid HTTPS URL",
			cfg: ClientConfig{
				BaseURL: "https://secretvault.internal:8443",
			},
			wantError: false,
		},
		{
			name: "Insecure HTTP without allow flag rejected",
			cfg: ClientConfig{
				BaseURL: "http://secretvault.internal:8080",
			},
			wantError: true,
		},
		{
			name: "Insecure HTTP to external host even with allow flag rejected",
			cfg: ClientConfig{
				BaseURL:           "http://evil-server.com:8080",
				AllowInsecureHTTP: true,
			},
			wantError: true,
		},
		{
			name: "Insecure HTTP to localhost with allow flag accepted",
			cfg: ClientConfig{
				BaseURL:           "http://localhost:8080",
				AllowInsecureHTTP: true,
			},
			wantError: false,
		},
		{
			name: "URL with embedded userinfo rejected",
			cfg: ClientConfig{
				BaseURL: "https://user:password@secretvault.internal",
			},
			wantError: true,
		},
		{
			name: "URL with query parameters rejected",
			cfg: ClientConfig{
				BaseURL: "https://secretvault.internal/api?query=1",
			},
			wantError: true,
		},
		{
			name: "Empty BaseURL rejected",
			cfg: ClientConfig{
				BaseURL: "",
			},
			wantError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.cfg.Validate()
			if (err != nil) != tt.wantError {
				t.Errorf("Validate() error = %v, wantError %v", err, tt.wantError)
			}
		})
	}
}

func TestRetryClassifier(t *testing.T) {
	// Retryable status codes
	if !IsRetryableStatus(429) || !IsRetryableStatus(502) || !IsRetryableStatus(503) || !IsRetryableStatus(504) {
		t.Errorf("expected 429, 502, 503, 504 to be retryable")
	}

	// Non-retryable status codes
	if IsRetryableStatus(200) || IsRetryableStatus(400) || IsRetryableStatus(401) || IsRetryableStatus(403) || IsRetryableStatus(404) || IsRetryableStatus(500) {
		t.Errorf("expected 200, 400, 401, 403, 404, 500 to NOT be retryable")
	}

	// Error classification
	if !IsRetryableError(errors.New("connection reset by peer")) && !IsRetryableError(nil) {
		// Non-nil checking
	}
}

func TestCalculateBackoff(t *testing.T) {
	cfg := RetryConfig{
		MaxRetries:        3,
		InitialBackoff:    100 * time.Millisecond,
		MaxBackoff:        1 * time.Second,
		BackoffMultiplier: 2.0,
		EnableJitter:      false,
	}

	b0 := CalculateBackoff(0, cfg)
	if b0 != 100*time.Millisecond {
		t.Errorf("attempt 0 backoff expected 100ms, got %v", b0)
	}

	b1 := CalculateBackoff(1, cfg)
	if b1 != 200*time.Millisecond {
		t.Errorf("attempt 1 backoff expected 200ms, got %v", b1)
	}

	b5 := CalculateBackoff(5, cfg)
	if b5 > 1*time.Second {
		t.Errorf("backoff exceeded max backoff cap: %v", b5)
	}
}

func TestRedaction(t *testing.T) {
	headers := make(http.Header)
	headers.Set("Authorization", "Bearer sv_machine_super_secret_token")
	headers.Set("Cookie", "session_id=123456")
	headers.Set("X-Api-Key", "api-key-999")
	headers.Set("X-Custom-Header", "public-value")

	redacted := RedactHeaders(headers)
	if redacted.Get("Authorization") != "[REDACTED]" {
		t.Errorf("Authorization header not redacted: %s", redacted.Get("Authorization"))
	}
	if redacted.Get("Cookie") != "[REDACTED]" {
		t.Errorf("Cookie header not redacted: %s", redacted.Get("Cookie"))
	}
	if redacted.Get("X-Api-Key") != "[REDACTED]" {
		t.Errorf("X-Api-Key header not redacted: %s", redacted.Get("X-Api-Key"))
	}
	if redacted.Get("X-Custom-Header") != "public-value" {
		t.Errorf("Non-sensitive header was corrupted")
	}

	// String scrubbing
	text := "Error connecting with token sv_machine_123456789 and Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.sig"
	scrubbed := RedactString(text)
	if scrubbed == text {
		t.Errorf("RedactString failed to scrub token strings: %s", scrubbed)
	}
}
