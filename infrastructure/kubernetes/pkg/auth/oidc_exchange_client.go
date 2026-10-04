package auth

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"
)

const (
	// DefaultOidcExchangePath is the primary OIDC token exchange endpoint in SecretVault backend.
	DefaultOidcExchangePath = "/api/v1/auth/oidc/token"

	// FallbackOidcExchangePath is the legacy alias endpoint.
	FallbackOidcExchangePath = "/api/v1/oidc/auth/exchange"

	// MaxExchangeResponseBodyBytes limits response size to 1 MB to avoid memory exhaustion.
	MaxExchangeResponseBodyBytes = 1024 * 1024
)

// OidcTokenExchanger manages the OIDC exchange lifecycle and session caching.
type OidcTokenExchanger interface {
	// Exchange forces a fresh OIDC token exchange with the SecretVault backend.
	Exchange(ctx context.Context) (*MachineSession, error)

	// GetValidSession returns a valid cached machine session or refreshes it if near expiry.
	GetValidSession(ctx context.Context) (*MachineSession, error)

	// InvalidateSession clears the cached machine session.
	InvalidateSession()
}

// OidcExchangeOptions configures the OIDC exchange client.
type OidcExchangeOptions struct {
	BaseURL       string
	ExchangePath  string
	ProviderID    string
	Issuer        string
	TokenProvider ProjectedTokenProvider
	HTTPClient    *http.Client
	SafetyMargin  time.Duration
}

// OidcExchangeClient implements OidcTokenExchanger.
type OidcExchangeClient struct {
	baseURL       string
	exchangePath  string
	providerID    string
	issuer        string
	tokenProvider ProjectedTokenProvider
	httpClient    *http.Client
	safetyMargin  time.Duration

	activeSession *MachineSession
	mu            sync.Mutex
}

// NewOidcExchangeClient creates an instance of OidcExchangeClient.
func NewOidcExchangeClient(opts OidcExchangeOptions) (*OidcExchangeClient, error) {
	if strings.TrimSpace(opts.BaseURL) == "" {
		return nil, NewAuthError("init", "SecretVault base URL is required", ErrInvalidConfiguration)
	}
	if opts.TokenProvider == nil {
		return nil, NewAuthError("init", "ProjectedTokenProvider is required", ErrInvalidConfiguration)
	}

	exchangePath := opts.ExchangePath
	if strings.TrimSpace(exchangePath) == "" {
		exchangePath = DefaultOidcExchangePath
	}

	httpClient := opts.HTTPClient
	if httpClient == nil {
		httpClient = &http.Client{
			Timeout: 15 * time.Second,
		}
	}

	safetyMargin := opts.SafetyMargin
	if safetyMargin <= 0 {
		safetyMargin = DefaultSessionSafetyMargin
	}

	return &OidcExchangeClient{
		baseURL:       strings.TrimRight(opts.BaseURL, "/"),
		exchangePath:  exchangePath,
		providerID:    strings.TrimSpace(opts.ProviderID),
		issuer:        strings.TrimSpace(opts.Issuer),
		tokenProvider: opts.TokenProvider,
		httpClient:    httpClient,
		safetyMargin:  safetyMargin,
	}, nil
}

// OidcExchangePayload is the JSON request sent to the exchange endpoint.
type OidcExchangePayload struct {
	ProviderID *string `json:"providerId,omitempty"`
	Issuer     *string `json:"issuer,omitempty"`
	Token      string  `json:"token"`
}

// OidcExchangeApiResponse mirrors com.secretvault.common.dto.ApiResponse<OidcTokenResponse>.
type OidcExchangeApiResponse struct {
	Success bool                     `json:"success"`
	Message string                   `json:"message,omitempty"`
	Data    *OidcExchangeDataPayload `json:"data,omitempty"`
	Error   *OidcErrorDetail         `json:"error,omitempty"`
}

// OidcExchangeDataPayload mirrors OidcDtos.OidcTokenResponse.
type OidcExchangeDataPayload struct {
	AccessToken     string                 `json:"accessToken"`
	TokenType       string                 `json:"tokenType"`
	ExpiresIn       int64                  `json:"expiresIn"`
	MachineIdentity *MachineIdentityDetail `json:"machineIdentity,omitempty"`
}

// MachineIdentityDetail holds metadata about the authenticated machine.
type MachineIdentityDetail struct {
	ID          string `json:"id"`
	WorkspaceID string `json:"workspaceId"`
	Name        string `json:"name"`
	Status      string `json:"status"`
}

// OidcErrorDetail holds error metadata from backend.
type OidcErrorDetail struct {
	Code    string `json:"code,omitempty"`
	Message string `json:"message,omitempty"`
}

// GetValidSession returns the active session if valid, or re-authenticates if expired.
func (c *OidcExchangeClient) GetValidSession(ctx context.Context) (*MachineSession, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	now := time.Now()
	if c.activeSession != nil && c.activeSession.IsValid(now, c.safetyMargin) {
		return c.activeSession, nil
	}

	// Session is either nil, expired, or near expiration - exchange for a new session
	session, err := c.performExchange(ctx)
	if err != nil {
		return nil, err
	}

	c.activeSession = session
	return session, nil
}

// Exchange forces an immediate token exchange.
func (c *OidcExchangeClient) Exchange(ctx context.Context) (*MachineSession, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	session, err := c.performExchange(ctx)
	if err != nil {
		return nil, err
	}

	c.activeSession = session
	return session, nil
}

// InvalidateSession clears the active session.
func (c *OidcExchangeClient) InvalidateSession() {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.activeSession = nil
}

// performExchange executes the HTTP POST request to exchange the projected token.
func (c *OidcExchangeClient) performExchange(ctx context.Context) (*MachineSession, error) {
	// 1. Read projected token from file
	token, err := c.tokenProvider.GetToken()
	if err != nil {
		return nil, NewAuthError("exchange", "failed to read projected token", err)
	}

	// 2. Prepare request payload
	payload := OidcExchangePayload{
		Token: token,
	}
	if c.providerID != "" {
		pID := c.providerID
		payload.ProviderID = &pID
	}
	if c.issuer != "" {
		iss := c.issuer
		payload.Issuer = &iss
	}

	bodyBytes, err := json.Marshal(payload)
	if err != nil {
		return nil, NewAuthError("exchange", "failed to serialize exchange payload", err)
	}

	endpoint := fmt.Sprintf("%s%s", c.baseURL, c.exchangePath)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, NewAuthError("exchange", "failed to create exchange HTTP request", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "SecretVault-Kubernetes-Operator/v1alpha1")

	// 3. Execute HTTP request
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, NewAuthError("exchange", "HTTP connection to SecretVault failed", ErrNetworkFailure)
	}
	defer resp.Body.Close()

	// 4. Read response body (bounded)
	respBody, err := io.ReadAll(io.LimitReader(resp.Body, MaxExchangeResponseBodyBytes))
	if err != nil {
		return nil, NewAuthError("exchange", "failed to read response body", ErrOidcExchangeFailed)
	}

	// 5. Handle HTTP status codes
	switch resp.StatusCode {
	case http.StatusOK:
		var apiResp OidcExchangeApiResponse
		if err := json.Unmarshal(respBody, &apiResp); err != nil {
			return nil, NewAuthError("exchange", "failed to parse exchange response JSON", ErrOidcExchangeFailed)
		}

		if !apiResp.Success || apiResp.Data == nil || apiResp.Data.AccessToken == "" {
			msg := "OIDC exchange rejected by backend"
			if apiResp.Message != "" {
				msg = apiResp.Message
			}
			return nil, NewAuthError("exchange", msg, ErrAuthenticationRejected)
		}

		data := apiResp.Data
		var machineID, machineName, workspaceID string
		if data.MachineIdentity != nil {
			machineID = data.MachineIdentity.ID
			machineName = data.MachineIdentity.Name
			workspaceID = data.MachineIdentity.WorkspaceID
		}

		session := NewMachineSession(
			data.AccessToken,
			data.TokenType,
			data.ExpiresIn,
			machineID,
			machineName,
			workspaceID,
		)
		return session, nil

	case http.StatusUnauthorized:
		return nil, NewAuthError("exchange", "workload authentication rejected: invalid or untrusted OIDC token", ErrAuthenticationRejected)

	case http.StatusForbidden:
		return nil, NewAuthError("exchange", "workload authorization denied by OIDC trust policy", ErrAuthorizationDenied)

	case http.StatusTooManyRequests:
		return nil, NewAuthError("exchange", "rate limit exceeded on OIDC exchange endpoint", ErrRateLimited)

	default:
		return nil, NewAuthError("exchange", fmt.Sprintf("OIDC exchange failed with HTTP status %d", resp.StatusCode), ErrOidcExchangeFailed)
	}
}
