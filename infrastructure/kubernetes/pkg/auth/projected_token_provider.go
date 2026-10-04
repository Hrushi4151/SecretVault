package auth

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"sync"
	"time"
	"unicode/utf8"
)

const (
	// DefaultProjectedTokenPath is the standard Kubernetes projected ServiceAccount token mount location.
	DefaultProjectedTokenPath = "/var/run/secrets/kubernetes.io/serviceaccount/token"

	// MaxTokenSizeBytes prevents denial-of-service / memory exhaustion from oversized token files (64 KB).
	MaxTokenSizeBytes = 64 * 1024
)

// ProjectedTokenProvider reads, pre-validates, and monitors Kubernetes projected ServiceAccount tokens.
type ProjectedTokenProvider interface {
	// GetToken reads and pre-validates the current projected token from the filesystem.
	GetToken() (string, error)

	// InspectClaims parses and inspects the non-cryptographic JWT claims for client-side pre-validation.
	InspectClaims() (*JwtClaims, error)

	// GetTokenPath returns the configured token file path.
	GetTokenPath() string
}

// JwtClaims represents the unverified claims extracted from the JWT payload for pre-validation only.
type JwtClaims struct {
	Issuer          string   `json:"iss,omitempty"`
	Subject         string   `json:"sub,omitempty"`
	Audiences       []string `json:"aud,omitempty"`
	ExpirationTime  int64    `json:"exp,omitempty"`
	NotBefore       int64    `json:"nbf,omitempty"`
	IssuedAt        int64    `json:"iat,omitempty"`
	ServiceAccount  string   `json:"kubernetes.io/serviceaccount/service-account.name,omitempty"`
	Namespace       string   `json:"kubernetes.io/serviceaccount/namespace,omitempty"`
	PodName         string   `json:"kubernetes.io/serviceaccount/pod.name,omitempty"`
	PodUID          string   `json:"kubernetes.io/serviceaccount/pod.uid,omitempty"`
}

// UnmarshalJSON handles both single string and array of strings for "aud".
func (c *JwtClaims) UnmarshalJSON(data []byte) error {
	type Alias JwtClaims
	aux := &struct {
		Aud interface{} `json:"aud,omitempty"`
		*Alias
	}{
		Alias: (*Alias)(c),
	}

	if err := json.Unmarshal(data, &aux); err != nil {
		return err
	}

	if aux.Aud != nil {
		switch v := aux.Aud.(type) {
		case string:
			c.Audiences = []string{v}
		case []interface{}:
			var auds []string
			for _, item := range v {
				if s, ok := item.(string); ok {
					auds = append(auds, s)
				}
			}
			c.Audiences = auds
		}
	}
	return nil
}

// DefaultProjectedTokenProvider is the production implementation of ProjectedTokenProvider.
type DefaultProjectedTokenProvider struct {
	tokenPath        string
	expectedAudience string
	expectedIssuer   string
	mu               sync.RWMutex
}

// TokenProviderOptions configures the DefaultProjectedTokenProvider.
type TokenProviderOptions struct {
	TokenPath        string
	ExpectedAudience string
	ExpectedIssuer   string
}

// NewProjectedTokenProvider creates a new token provider with the given options.
func NewProjectedTokenProvider(opts TokenProviderOptions) (*DefaultProjectedTokenProvider, error) {
	path := opts.TokenPath
	if strings.TrimSpace(path) == "" {
		path = DefaultProjectedTokenPath
	}

	return &DefaultProjectedTokenProvider{
		tokenPath:        path,
		expectedAudience: strings.TrimSpace(opts.ExpectedAudience),
		expectedIssuer:   strings.TrimSpace(opts.ExpectedIssuer),
	}, nil
}

// GetTokenPath returns the active token path.
func (p *DefaultProjectedTokenProvider) GetTokenPath() string {
	return p.tokenPath
}

// GetToken reads, checks limits, and validates the projected token file.
func (p *DefaultProjectedTokenProvider) GetToken() (string, error) {
	p.mu.RLock()
	defer p.mu.RUnlock()

	// 1. File existence and metadata check
	info, err := os.Stat(p.tokenPath)
	if err != nil {
		if os.IsNotExist(err) {
			return "", NewAuthError("read_token", fmt.Sprintf("token file not found at %s", p.tokenPath), ErrTokenFileNotFound)
		}
		return "", NewAuthError("read_token", "failed to inspect token file", ErrTokenFileInvalid)
	}

	// 2. Reject non-regular files (e.g. directories, sockets, devices)
	if !info.Mode().IsRegular() {
		return "", NewAuthError("read_token", "token path is not a regular file", ErrTokenFileInvalid)
	}

	// 3. File size constraints
	size := info.Size()
	if size == 0 {
		return "", NewAuthError("read_token", "token file is empty", ErrTokenFileInvalid)
	}
	if size > MaxTokenSizeBytes {
		return "", NewAuthError("read_token", fmt.Sprintf("token file exceeds maximum permitted size (%d > %d bytes)", size, MaxTokenSizeBytes), ErrTokenFileInvalid)
	}

	// 4. Read file content into memory
	data, err := os.ReadFile(p.tokenPath)
	if err != nil {
		return "", NewAuthError("read_token", "unable to read token file contents", ErrTokenFileInvalid)
	}

	// 5. UTF-8 & sanity validation
	if !utf8.Valid(data) {
		return "", NewAuthError("read_token", "token file contains invalid non-UTF-8 binary data", ErrTokenFileInvalid)
	}

	tokenStr := strings.TrimSpace(string(data))
	if tokenStr == "" {
		return "", NewAuthError("read_token", "token file contains only whitespace", ErrTokenFileInvalid)
	}

	// 6. Pre-validate JWT structure
	claims, err := preValidateJwt(tokenStr)
	if err != nil {
		return "", err
	}

	// 7. Check expiration if present
	if claims.ExpirationTime > 0 {
		expTime := time.Unix(claims.ExpirationTime, 0)
		if time.Now().After(expTime) {
			return "", NewAuthError("pre_validate_jwt", fmt.Sprintf("projected token expired at %s", expTime.UTC().Format(time.RFC3339)), ErrTokenExpired)
		}
	}

	// 8. Audience check if configured
	if p.expectedAudience != "" {
		hasAudience := false
		for _, aud := range claims.Audiences {
			if aud == p.expectedAudience {
				hasAudience = true
				break
			}
		}
		if !hasAudience {
			return "", NewAuthError("pre_validate_jwt", fmt.Sprintf("token does not contain required audience '%s'", p.expectedAudience), ErrAuthenticationRejected)
		}
	}

	// 9. Issuer check if configured
	if p.expectedIssuer != "" && claims.Issuer != "" {
		if !strings.EqualFold(strings.TrimRight(claims.Issuer, "/"), strings.TrimRight(p.expectedIssuer, "/")) {
			return "", NewAuthError("pre_validate_jwt", fmt.Sprintf("token issuer '%s' does not match expected '%s'", claims.Issuer, p.expectedIssuer), ErrAuthenticationRejected)
		}
	}

	return tokenStr, nil
}

// InspectClaims extracts and decodes claims from the token without network or crypto calls.
func (p *DefaultProjectedTokenProvider) InspectClaims() (*JwtClaims, error) {
	token, err := p.GetToken()
	if err != nil {
		return nil, err
	}
	return preValidateJwt(token)
}

// preValidateJwt verifies 3 segments and decodes payload JSON.
func preValidateJwt(token string) (*JwtClaims, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return nil, NewAuthError("pre_validate_jwt", "token is not a valid 3-segment JWT", ErrTokenFileInvalid)
	}

	headerSegment, payloadSegment, sigSegment := parts[0], parts[1], parts[2]
	if headerSegment == "" || payloadSegment == "" || sigSegment == "" {
		return nil, NewAuthError("pre_validate_jwt", "token has empty JWT segments", ErrTokenFileInvalid)
	}

	// Base64URL decode payload
	payloadBytes, err := decodeBase64Url(payloadSegment)
	if err != nil {
		return nil, NewAuthError("pre_validate_jwt", "failed to base64url-decode JWT payload", ErrTokenFileInvalid)
	}

	var claims JwtClaims
	decoder := json.NewDecoder(bytes.NewReader(payloadBytes))
	if err := decoder.Decode(&claims); err != nil {
		return nil, NewAuthError("pre_validate_jwt", "failed to parse JWT payload JSON", ErrTokenFileInvalid)
	}

	return &claims, nil
}

func decodeBase64Url(s string) ([]byte, error) {
	// Standard base64 RawURLEncoding (without padding)
	return base64.RawURLEncoding.DecodeString(s)
}
