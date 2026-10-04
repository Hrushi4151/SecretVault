package client

import (
	"bytes"
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"io"
	"math/rand"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

// Config configures the SecretVault API client.
type Config struct {
	BaseURL            string
	Auth               AuthProvider
	DefaultWorkspaceID string
	TLS                TLSConfig
	Timeout            time.Duration
	MaxRetries         int
	RetryWaitMin       time.Duration
	RetryWaitMax       time.Duration
}

// TLSConfig defines TLS validation settings.
type TLSConfig struct {
	CACert             string
	CACertFile         string
	InsecureSkipVerify bool
	ServerName         string
}

// Client is the production-grade HTTP client communicating with SecretVault REST APIs.
type Client struct {
	baseURL            string
	auth               AuthProvider
	defaultWorkspaceID string
	httpClient         *http.Client
	maxRetries         int
	retryWaitMin       time.Duration
	retryWaitMax       time.Duration
}

// NewClient constructs and initializes a hardened SecretVault API client.
func NewClient(cfg Config) (*Client, error) {
	if cfg.BaseURL == "" {
		return nil, fmt.Errorf("baseURL is required")
	}
	cfg.BaseURL = strings.TrimRight(cfg.BaseURL, "/")

	tlsConfig := &tls.Config{
		MinVersion:         tls.VersionTLS12,
		InsecureSkipVerify: cfg.TLS.InsecureSkipVerify,
		ServerName:         cfg.TLS.ServerName,
	}

	if cfg.TLS.CACert != "" || cfg.TLS.CACertFile != "" {
		rootCAs, err := x509.SystemCertPool()
		if err != nil || rootCAs == nil {
			rootCAs = x509.NewCertPool()
		}

		var certData []byte
		if cfg.TLS.CACertFile != "" {
			var err error
			certData, err = os.ReadFile(cfg.TLS.CACertFile)
			if err != nil {
				return nil, fmt.Errorf("failed to read CA cert file: %w", err)
			}
		} else if cfg.TLS.CACert != "" {
			certData = []byte(cfg.TLS.CACert)
		}

		if len(certData) > 0 {
			if ok := rootCAs.AppendCertsFromPEM(certData); !ok {
				return nil, fmt.Errorf("failed to append CA certificate to pool")
			}
			tlsConfig.RootCAs = rootCAs
		}
	}

	transport := &http.Transport{
		TLSClientConfig:     tlsConfig,
		MaxIdleConns:        100,
		MaxIdleConnsPerHost: 20,
		IdleConnTimeout:     90 * time.Second,
		DisableKeepAlives:   false,
	}

	timeout := cfg.Timeout
	if timeout <= 0 {
		timeout = 30 * time.Second
	}

	maxRetries := cfg.MaxRetries
	if maxRetries < 0 {
		maxRetries = 0
	} else if maxRetries == 0 {
		maxRetries = 3
	}

	retryMin := cfg.RetryWaitMin
	if retryMin <= 0 {
		retryMin = 500 * time.Millisecond
	}

	retryMax := cfg.RetryWaitMax
	if retryMax <= 0 {
		retryMax = 5000 * time.Millisecond
	}

	httpClient := &http.Client{
		Transport: transport,
		Timeout:   timeout,
	}

	return &Client{
		baseURL:            cfg.BaseURL,
		auth:               cfg.Auth,
		defaultWorkspaceID: cfg.DefaultWorkspaceID,
		httpClient:         httpClient,
		maxRetries:         maxRetries,
		retryWaitMin:       retryMin,
		retryWaitMax:       retryMax,
	}, nil
}

// executeRequest executes an HTTP request with retries, backoff, and tenant scoping.
func (c *Client) executeRequest(ctx context.Context, method, path string, workspaceID string, reqBody, respData interface{}) error {
	url := fmt.Sprintf("%s%s", c.baseURL, path)

	var bodyBytes []byte
	if reqBody != nil {
		var err error
		bodyBytes, err = json.Marshal(reqBody)
		if err != nil {
			return fmt.Errorf("failed to marshal request body: %w", err)
		}
	}

	effectiveWS := workspaceID
	if effectiveWS == "" {
		effectiveWS = c.defaultWorkspaceID
	}

	retries := 0
	for {
		req, err := http.NewRequestWithContext(ctx, method, url, bytes.NewReader(bodyBytes))
		if err != nil {
			return fmt.Errorf("failed to create HTTP request: %w", err)
		}

		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Accept", "application/json")
		req.Header.Set("User-Agent", "terraform-provider-secretvault/1.0.0")

		if effectiveWS != "" {
			req.Header.Set("X-Workspace-ID", effectiveWS)
		}

		if c.auth != nil {
			token, err := c.auth.GetToken(ctx, c.httpClient, c.baseURL)
			if err != nil {
				return fmt.Errorf("authentication error: %w", err)
			}
			if token != "" {
				req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", token))
			}
		}

		resp, err := c.httpClient.Do(req)
		if err != nil {
			// Only retry idempotent methods on network errors
			if isIdempotentMethod(method) && retries < c.maxRetries {
				retries++
				c.sleepBackoff(ctx, retries, 0)
				continue
			}
			return fmt.Errorf("network communication failure (%s %s): %w", method, path, err)
		}

		respBodyBytes, readErr := io.ReadAll(resp.Body)
		resp.Body.Close()
		if readErr != nil {
			return fmt.Errorf("failed to read response body: %w", readErr)
		}

		// Handle successful responses
		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			if respData != nil && len(respBodyBytes) > 0 {
				var envelope ApiResponse[json.RawMessage]
				if err := json.Unmarshal(respBodyBytes, &envelope); err == nil && envelope.Data != nil {
					if err := json.Unmarshal(envelope.Data, respData); err != nil {
						return fmt.Errorf("failed to unmarshal envelope data: %w", err)
					}
				} else {
					if err := json.Unmarshal(respBodyBytes, respData); err != nil {
						return fmt.Errorf("failed to unmarshal response: %w", err)
					}
				}
			}
			return nil
		}

		// Handle Retry-After / 429
		if resp.StatusCode == http.StatusTooManyRequests && retries < c.maxRetries {
			retryAfterSeconds := parseRetryAfter(resp.Header.Get("Retry-After"))
			retries++
			c.sleepBackoff(ctx, retries, retryAfterSeconds)
			continue
		}

		// Handle transient server errors (502, 503, 504) for idempotent requests
		if (resp.StatusCode == http.StatusBadGateway || resp.StatusCode == http.StatusServiceUnavailable || resp.StatusCode == http.StatusGatewayTimeout) &&
			isIdempotentMethod(method) && retries < c.maxRetries {
			retries++
			c.sleepBackoff(ctx, retries, 0)
			continue
		}

		// Non-retryable status or retries exhausted
		var envelope ApiResponse[any]
		msg := string(respBodyBytes)
		traceId := resp.Header.Get("X-Correlation-ID")
		if json.Unmarshal(respBodyBytes, &envelope) == nil {
			if envelope.Message != "" {
				msg = envelope.Message
			}
			if envelope.TraceId != "" {
				traceId = envelope.TraceId
			}
		}

		return &APIError{
			StatusCode: resp.StatusCode,
			Message:    msg,
			TraceId:    traceId,
			RawBody:    string(respBodyBytes),
		}
	}
}

func isIdempotentMethod(method string) bool {
	switch method {
	case http.MethodGet, http.MethodHead, http.MethodOptions, http.MethodPut, http.MethodDelete:
		return true
	default:
		return false
	}
}

func parseRetryAfter(headerVal string) int {
	if headerVal == "" {
		return 0
	}
	if seconds, err := strconv.Atoi(headerVal); err == nil && seconds > 0 {
		return seconds
	}
	return 0
}

func (c *Client) sleepBackoff(ctx context.Context, retryCount int, retryAfterSeconds int) {
	if retryAfterSeconds > 0 {
		timer := time.NewTimer(time.Duration(retryAfterSeconds) * time.Second)
		select {
		case <-ctx.Done():
			timer.Stop()
			return
		case <-timer.C:
			return
		}
	}

	backoff := c.retryWaitMin * time.Duration(1<<uint(retryCount-1))
	jitter := time.Duration(rand.Int63n(int64(c.retryWaitMin)))
	backoff += jitter
	if backoff > c.retryWaitMax {
		backoff = c.retryWaitMax
	}

	timer := time.NewTimer(backoff)
	select {
	case <-ctx.Done():
		timer.Stop()
		return
	case <-timer.C:
		return
	}
}

// --- Workspace API ---

func (c *Client) GetWorkspace(ctx context.Context, workspaceID string) (*WorkspaceResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s", workspaceID)
	var resp WorkspaceResponse
	err := c.executeRequest(ctx, http.MethodGet, path, workspaceID, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

// --- Project API ---

func (c *Client) CreateProject(ctx context.Context, workspaceID string, req CreateProjectRequest) (*ProjectResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/projects", workspaceID)
	var resp ProjectResponse
	err := c.executeRequest(ctx, http.MethodPost, path, workspaceID, req, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) GetProject(ctx context.Context, workspaceID, projectID string) (*ProjectResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/projects/%s", workspaceID, projectID)
	var resp ProjectResponse
	err := c.executeRequest(ctx, http.MethodGet, path, workspaceID, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) UpdateProject(ctx context.Context, workspaceID, projectID string, req UpdateProjectRequest) (*ProjectResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/projects/%s", workspaceID, projectID)
	var resp ProjectResponse
	err := c.executeRequest(ctx, http.MethodPatch, path, workspaceID, req, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) DeleteProject(ctx context.Context, workspaceID, projectID string) error {
	path := fmt.Sprintf("/api/v1/workspaces/%s/projects/%s", workspaceID, projectID)
	return c.executeRequest(ctx, http.MethodDelete, path, workspaceID, nil, nil)
}

// --- Environment API ---

func (c *Client) CreateEnvironment(ctx context.Context, workspaceID, projectID string, req CreateEnvironmentRequest) (*EnvironmentResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/projects/%s/environments", workspaceID, projectID)
	var resp EnvironmentResponse
	err := c.executeRequest(ctx, http.MethodPost, path, workspaceID, req, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) GetEnvironment(ctx context.Context, workspaceID, projectID, envID string) (*EnvironmentResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/projects/%s/environments/%s", workspaceID, projectID, envID)
	var resp EnvironmentResponse
	err := c.executeRequest(ctx, http.MethodGet, path, workspaceID, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) UpdateEnvironment(ctx context.Context, workspaceID, projectID, envID string, req UpdateEnvironmentRequest) (*EnvironmentResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/projects/%s/environments/%s", workspaceID, projectID, envID)
	var resp EnvironmentResponse
	err := c.executeRequest(ctx, http.MethodPatch, path, workspaceID, req, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) DeleteEnvironment(ctx context.Context, workspaceID, projectID, envID string) error {
	path := fmt.Sprintf("/api/v1/workspaces/%s/projects/%s/environments/%s", workspaceID, projectID, envID)
	return c.executeRequest(ctx, http.MethodDelete, path, workspaceID, nil, nil)
}

// --- Secret API (Zero-Plaintext Read) ---

func (c *Client) CreateSecret(ctx context.Context, workspaceID, projectID, envID string, req CreateSecretRequest) (*SecretMetadataResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets", workspaceID, projectID, envID)
	var resp SecretMetadataResponse
	err := c.executeRequest(ctx, http.MethodPost, path, workspaceID, req, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

// GetSecretMetadata strictly retrieves metadata only, NEVER invoking in-memory reveal.
func (c *Client) GetSecretMetadata(ctx context.Context, workspaceID, projectID, envID, secretID string) (*SecretMetadataResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s", workspaceID, projectID, envID, secretID)
	var resp SecretMetadataResponse
	err := c.executeRequest(ctx, http.MethodGet, path, workspaceID, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) UpdateSecret(ctx context.Context, workspaceID, projectID, envID, secretID string, req UpdateSecretRequest) (*SecretMetadataResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s", workspaceID, projectID, envID, secretID)
	var resp SecretMetadataResponse
	err := c.executeRequest(ctx, http.MethodPatch, path, workspaceID, req, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) DeleteSecret(ctx context.Context, workspaceID, projectID, envID, secretID string) error {
	path := fmt.Sprintf("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets/%s", workspaceID, projectID, envID, secretID)
	return c.executeRequest(ctx, http.MethodDelete, path, workspaceID, nil, nil)
}

// --- Machine Identity API ---

func (c *Client) CreateMachineIdentity(ctx context.Context, workspaceID string, req CreateMachineIdentityRequest) (*MachineIdentityResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/machines", workspaceID)
	var resp MachineIdentityResponse
	err := c.executeRequest(ctx, http.MethodPost, path, workspaceID, req, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) GetMachineIdentity(ctx context.Context, workspaceID, machineID string) (*MachineIdentityResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/machines/%s", workspaceID, machineID)
	var resp MachineIdentityResponse
	err := c.executeRequest(ctx, http.MethodGet, path, workspaceID, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) UpdateMachineIdentity(ctx context.Context, workspaceID, machineID string, req UpdateMachineIdentityRequest) (*MachineIdentityResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/machines/%s", workspaceID, machineID)
	var resp MachineIdentityResponse
	err := c.executeRequest(ctx, http.MethodPatch, path, workspaceID, req, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) DeleteMachineIdentity(ctx context.Context, workspaceID, machineID string) error {
	path := fmt.Sprintf("/api/v1/workspaces/%s/machines/%s", workspaceID, machineID)
	return c.executeRequest(ctx, http.MethodDelete, path, workspaceID, nil, nil)
}

// --- Provider Integration API ---

func (c *Client) CreateProviderIntegration(ctx context.Context, workspaceID string, req CreateProviderIntegrationRequest) (*ProviderIntegrationResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/integrations", workspaceID)
	var resp ProviderIntegrationResponse
	err := c.executeRequest(ctx, http.MethodPost, path, workspaceID, req, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) GetProviderIntegration(ctx context.Context, workspaceID, integrationID string) (*ProviderIntegrationResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/integrations/%s", workspaceID, integrationID)
	var resp ProviderIntegrationResponse
	err := c.executeRequest(ctx, http.MethodGet, path, workspaceID, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) UpdateProviderIntegration(ctx context.Context, workspaceID, integrationID string, req UpdateProviderIntegrationRequest) (*ProviderIntegrationResponse, error) {
	path := fmt.Sprintf("/api/v1/workspaces/%s/integrations/%s", workspaceID, integrationID)
	var resp ProviderIntegrationResponse
	err := c.executeRequest(ctx, http.MethodPatch, path, workspaceID, req, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) DeleteProviderIntegration(ctx context.Context, workspaceID, integrationID string) error {
	path := fmt.Sprintf("/api/v1/workspaces/%s/integrations/%s", workspaceID, integrationID)
	return c.executeRequest(ctx, http.MethodDelete, path, workspaceID, nil, nil)
}
