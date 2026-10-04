package client

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/secretvault/operator/pkg/auth"
)

// SecretVaultClient provides a secure, authenticated interface to SecretVault.
type SecretVaultClient struct {
	config     ClientConfig
	httpClient *http.Client
	exchanger  auth.OidcTokenExchanger
}

// NewSecretVaultClient constructs and initializes an authenticated SecretVault API client.
func NewSecretVaultClient(cfg ClientConfig, exchanger auth.OidcTokenExchanger) (*SecretVaultClient, error) {
	if err := cfg.Validate(); err != nil {
		return nil, err
	}
	if exchanger == nil {
		return nil, auth.NewAuthError("client_init", "OidcTokenExchanger is required", auth.ErrInvalidConfiguration)
	}

	httpClient, err := cfg.BuildHTTPClient()
	if err != nil {
		return nil, err
	}

	return &SecretVaultClient{
		config:     cfg,
		httpClient: httpClient,
		exchanger:  exchanger,
	}, nil
}

// Do executes an authenticated HTTP request with automatic token injection, retry on transient errors, and 401 re-exchange.
func (c *SecretVaultClient) Do(ctx context.Context, req *http.Request) (*http.Response, error) {
	// Preserve request body for retries if present
	var bodyBytes []byte
	if req.Body != nil {
		var err error
		bodyBytes, err = io.ReadAll(req.Body)
		if err != nil {
			return nil, auth.NewAuthError("request", "failed to buffer request body", err)
		}
		_ = req.Body.Close()
	}

	maxRetries := c.config.RetryConfig.MaxRetries
	if maxRetries < 0 {
		maxRetries = 0
	}

	var lastResp *http.Response
	var lastErr error
	reExchanged := false

	for attempt := 0; attempt <= maxRetries; attempt++ {
		if attempt > 0 {
			backoff := CalculateBackoff(attempt, c.config.RetryConfig)
			select {
			case <-ctx.Done():
				return nil, ctx.Err()
			case <-time.After(backoff):
			}
		}

		// 1. Obtain valid machine session
		session, err := c.exchanger.GetValidSession(ctx)
		if err != nil {
			return nil, auth.NewAuthError("client_auth", "failed to obtain valid machine session", err)
		}

		// 2. Clone request for execution
		var reqBodyReader io.Reader
		if bodyBytes != nil {
			reqBodyReader = bytes.NewReader(bodyBytes)
		}

		execReq, err := http.NewRequestWithContext(ctx, req.Method, req.URL.String(), reqBodyReader)
		if err != nil {
			return nil, auth.NewAuthError("request", "failed to clone request", err)
		}

		// Copy original headers
		for k, vv := range req.Header {
			execReq.Header[k] = append([]string(nil), vv...)
		}

		// Inject Authorization header
		execReq.Header.Set("Authorization", session.GetAuthorizationHeader())
		if execReq.Header.Get("User-Agent") == "" {
			execReq.Header.Set("User-Agent", "SecretVault-Kubernetes-Operator/v1alpha1")
		}

		// 3. Execute HTTP request
		resp, err := c.httpClient.Do(execReq)
		if err != nil {
			lastErr = err
			if IsRetryableError(err) {
				continue
			}
			return nil, auth.NewAuthError("http_exec", "non-retryable network error", err)
		}

		// 4. Handle 401 Unauthorized with single re-exchange attempt
		if resp.StatusCode == http.StatusUnauthorized && !reExchanged {
			_ = resp.Body.Close()
			c.exchanger.InvalidateSession()
			reExchanged = true
			continue
		}

		// 5. Handle transient HTTP status codes (429, 502, 503, 504)
		if IsRetryableStatus(resp.StatusCode) {
			_ = resp.Body.Close()
			lastResp = resp
			lastErr = auth.NewAuthError("http_exec", fmt.Sprintf("received transient HTTP status %d", resp.StatusCode), nil)
			continue
		}

		return resp, nil
	}

	if lastResp != nil && lastResp.StatusCode == http.StatusTooManyRequests {
		return nil, auth.NewAuthError("http_exec", "rate limit exceeded after exhausting retries", auth.ErrRateLimited)
	}
	if lastErr != nil {
		return nil, auth.NewAuthError("http_exec", "request failed after exhausting retries", lastErr)
	}
	return nil, auth.NewAuthError("http_exec", "request failed with unknown error", auth.ErrNetworkFailure)
}

// SecretResponse models the decrypted secret response from SecretVault.
type SecretResponse struct {
	ID          string            `json:"id"`
	Name        string            `json:"name"`
	WorkspaceID string            `json:"workspaceId"`
	ProjectID   string            `json:"projectId"`
	Environment string            `json:"environment"`
	Version     int               `json:"version"`
	Value       string            `json:"value"` // Memory-only decrypted value
	ContentType string            `json:"contentType,omitempty"`
	Metadata    map[string]string `json:"metadata,omitempty"`
	UpdatedAt   time.Time         `json:"updatedAt"`
}

// SecretSummary models a summary item returned by environment secret listings.
type SecretSummary struct {
	ID          string            `json:"id"`
	Name        string            `json:"name"`
	Version     int               `json:"version"`
	ContentType string            `json:"contentType,omitempty"`
	Tags        []string          `json:"tags,omitempty"`
	Metadata    map[string]string `json:"metadata,omitempty"`
}

// LeaseResponse models the response when creating an ephemeral lease.
type LeaseResponse struct {
	LeaseID      string    `json:"leaseId"`
	SecretID     string    `json:"secretId"`
	ExpiresAt    time.Time `json:"expiresAt"`
	ConsumerType string    `json:"consumerType"`
	TTLSeconds   int64     `json:"ttlSeconds"`
}

// GenericApiResponse wraps standard SecretVault backend API responses.
type GenericApiResponse[T any] struct {
	Success bool   `json:"success"`
	Message string `json:"message,omitempty"`
	Data    T      `json:"data"`
}

// GetSecret fetches a single secret from the SecretVault backend.
func (c *SecretVaultClient) GetSecret(
	ctx context.Context,
	workspaceId string,
	projectId string,
	environment string,
	secretName string,
	version *int,
) (*SecretResponse, error) {
	if workspaceId == "" || projectId == "" || environment == "" || secretName == "" {
		return nil, auth.NewAuthError("get_secret", "workspace, project, environment, and secretName are required", auth.ErrInvalidConfiguration)
	}

	endpoint := fmt.Sprintf("%s/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s",
		c.config.BaseURL,
		url.PathEscape(workspaceId),
		url.PathEscape(projectId),
		url.PathEscape(environment),
		url.PathEscape(secretName),
	)

	if version != nil && *version > 0 {
		endpoint = fmt.Sprintf("%s?version=%d", endpoint, *version)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, auth.NewAuthError("get_secret", "failed to create secret request", err)
	}
	req.Header.Set("Accept", "application/json")

	resp, err := c.Do(ctx, req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, auth.NewAuthError("get_secret", fmt.Sprintf("failed to get secret: HTTP %d", resp.StatusCode), nil)
	}

	respBody, err := io.ReadAll(io.LimitReader(resp.Body, c.config.MaxResponseSizeBytes))
	if err != nil {
		return nil, auth.NewAuthError("get_secret", "failed to read response body", err)
	}

	var apiResp GenericApiResponse[SecretResponse]
	if err := json.Unmarshal(respBody, &apiResp); err != nil {
		return nil, auth.NewAuthError("get_secret", "failed to parse secret JSON response", err)
	}

	return &apiResp.Data, nil
}
