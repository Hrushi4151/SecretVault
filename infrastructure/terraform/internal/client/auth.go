package client

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"sync"
	"time"
)

// AuthProvider manages token acquisition and renewal for API calls.
type AuthProvider interface {
	GetToken(ctx context.Context, httpClient *http.Client, baseURL string) (string, error)
}

// StaticTokenAuth provides a pre-configured static token.
type StaticTokenAuth struct {
	Token string
}

func (s *StaticTokenAuth) GetToken(ctx context.Context, httpClient *http.Client, baseURL string) (string, error) {
	if s.Token == "" {
		return "", fmt.Errorf("static token is empty")
	}
	return s.Token, nil
}

// MachineIdentityAuth authenticates via OIDC / Machine Identity token exchange.
type MachineIdentityAuth struct {
	WorkspaceID  string
	Audience     string
	SubjectToken string
	TokenType    string

	mu          sync.RWMutex
	cachedToken string
	expiresAt   time.Time
}

func (m *MachineIdentityAuth) GetToken(ctx context.Context, httpClient *http.Client, baseURL string) (string, error) {
	m.mu.RLock()
	if m.cachedToken != "" && time.Now().Add(60*time.Second).Before(m.expiresAt) {
		token := m.cachedToken
		m.mu.RUnlock()
		return token, nil
	}
	m.mu.RUnlock()

	m.mu.Lock()
	defer m.mu.Unlock()

	// Double-check after acquiring lock
	if m.cachedToken != "" && time.Now().Add(60*time.Second).Before(m.expiresAt) {
		return m.cachedToken, nil
	}

	reqBody := OidcTokenExchangeRequest{
		Audience:         m.Audience,
		SubjectToken:     m.SubjectToken,
		SubjectTokenType: m.TokenType,
		GrantType:        "urn:ietf:params:oauth:grant-type:token-exchange",
	}
	if reqBody.SubjectTokenType == "" {
		reqBody.SubjectTokenType = "urn:ietf:params:oauth:token-type:jwt"
	}
	if reqBody.Audience == "" {
		reqBody.Audience = "secretvault-api"
	}

	bodyBytes, err := json.Marshal(reqBody)
	if err != nil {
		return "", fmt.Errorf("failed to marshal OIDC auth request: %w", err)
	}

	url := fmt.Sprintf("%s/api/v1/auth/oidc/token", baseURL)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewBuffer(bodyBytes))
	if err != nil {
		return "", fmt.Errorf("failed to create OIDC auth request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	if m.WorkspaceID != "" {
		req.Header.Set("X-Workspace-ID", m.WorkspaceID)
	}

	resp, err := httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("OIDC auth exchange network failure: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("OIDC auth exchange failed with status: %d", resp.StatusCode)
	}

	var envelope ApiResponse[OidcTokenResponse]
	if err := json.NewDecoder(resp.Body).Decode(&envelope); err != nil {
		return "", fmt.Errorf("failed to decode OIDC auth response: %w", err)
	}

	m.cachedToken = envelope.Data.AccessToken
	ttl := time.Duration(envelope.Data.ExpiresIn) * time.Second
	if ttl <= 0 {
		ttl = 15 * time.Minute
	}
	m.expiresAt = time.Now().Add(ttl)

	return m.cachedToken, nil
}
