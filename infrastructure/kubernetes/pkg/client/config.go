package client

import (
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/secretvault/operator/pkg/auth"
)

// RetryConfig defines exponential backoff and retry parameters.
type RetryConfig struct {
	MaxRetries        int
	InitialBackoff    time.Duration
	MaxBackoff        time.Duration
	BackoffMultiplier float64
	EnableJitter      bool
}

// DefaultRetryConfig returns standard production retry configuration.
func DefaultRetryConfig() RetryConfig {
	return RetryConfig{
		MaxRetries:        3,
		InitialBackoff:    100 * time.Millisecond,
		MaxBackoff:        3 * time.Second,
		BackoffMultiplier: 2.0,
		EnableJitter:      true,
	}
}

// ClientConfig holds configuration for the SecretVault API client.
type ClientConfig struct {
	// BaseURL is the root address of the SecretVault API (e.g. "https://secretvault.internal:8443").
	BaseURL string

	// AllowInsecureHTTP permits unencrypted HTTP ONLY for localhost / loopback during local development.
	AllowInsecureHTTP bool

	// CACertFile points to a PEM-encoded custom CA certificate file.
	CACertFile string

	// CACertPEM contains raw PEM-encoded CA certificate bytes.
	CACertPEM []byte

	// ConnectTimeout is the maximum duration to wait for establishing a TCP/TLS connection.
	ConnectTimeout time.Duration

	// RequestTimeout is the end-to-end request timeout.
	RequestTimeout time.Duration

	// MaxResponseSizeBytes limits response payload reads to prevent memory exhaustion (default 10 MB).
	MaxResponseSizeBytes int64

	// RetryConfig configures transient retry behavior.
	RetryConfig RetryConfig
}

// DefaultClientConfig returns a hardened production configuration template.
func DefaultClientConfig() ClientConfig {
	return ClientConfig{
		ConnectTimeout:       5 * time.Second,
		RequestTimeout:       15 * time.Second,
		MaxResponseSizeBytes: 10 * 1024 * 1024,
		RetryConfig:          DefaultRetryConfig(),
	}
}

// Validate performs strict SSRF prevention, URL structure checks, and TLS validation.
func (c *ClientConfig) Validate() error {
	rawURL := strings.TrimSpace(c.BaseURL)
	if rawURL == "" {
		return auth.NewAuthError("config", "BaseURL is required", auth.ErrInvalidConfiguration)
	}

	u, err := url.Parse(rawURL)
	if err != nil {
		return auth.NewAuthError("config", fmt.Sprintf("malformed BaseURL '%s'", rawURL), auth.ErrInvalidConfiguration)
	}

	// 1. SSRF Prevention: Enforce HTTPS in production
	scheme := strings.ToLower(u.Scheme)
	if scheme != "https" {
		if scheme == "http" {
			if !c.AllowInsecureHTTP {
				return auth.NewAuthError("config", "HTTPS is strictly required for SecretVault API in production (insecure HTTP rejected)", auth.ErrInvalidConfiguration)
			}
			// If AllowInsecureHTTP is true, strictly verify that host is loopback
			hostOnly := u.Hostname()
			if hostOnly != "localhost" && hostOnly != "127.0.0.1" && hostOnly != "::1" {
				return auth.NewAuthError("config", fmt.Sprintf("insecure HTTP is only permitted for localhost/loopback, but host was '%s'", hostOnly), auth.ErrInvalidConfiguration)
			}
		} else {
			return auth.NewAuthError("config", fmt.Sprintf("unsupported URL scheme '%s' (only https is allowed)", scheme), auth.ErrInvalidConfiguration)
		}
	}

	// 2. SSRF Prevention: Host validation
	if u.Hostname() == "" {
		return auth.NewAuthError("config", "BaseURL must include a valid hostname", auth.ErrInvalidConfiguration)
	}

	// 3. Credential Injection Prevention: Disallow embedded userinfo (e.g. https://user:pass@host)
	if u.User != nil {
		return auth.NewAuthError("config", "BaseURL must not contain embedded user credentials", auth.ErrInvalidConfiguration)
	}

	// 4. Disallow query parameters or fragments in BaseURL
	if u.RawQuery != "" || u.Fragment != "" {
		return auth.NewAuthError("config", "BaseURL must not contain query parameters or fragments", auth.ErrInvalidConfiguration)
	}

	// 5. Timeout sanity checks
	if c.ConnectTimeout <= 0 {
		c.ConnectTimeout = 5 * time.Second
	}
	if c.RequestTimeout <= 0 {
		c.RequestTimeout = 15 * time.Second
	}
	if c.MaxResponseSizeBytes <= 0 {
		c.MaxResponseSizeBytes = 10 * 1024 * 1024
	}

	return nil
}

// BuildHTTPClient creates a hardened http.Client with TLS verification and custom CA support.
func (c *ClientConfig) BuildHTTPClient() (*http.Client, error) {
	if err := c.Validate(); err != nil {
		return nil, err
	}

	tlsConfig := &tls.Config{
		MinVersion:         tls.VersionTLS12,
		InsecureSkipVerify: false, // InsecureSkipVerify is NEVER allowed
	}

	// Load custom CA if provided
	var rootCAs *x509.CertPool
	if len(c.CACertPEM) > 0 {
		rootCAs = x509.NewCertPool()
		if !rootCAs.AppendCertsFromPEM(c.CACertPEM) {
			return nil, auth.NewAuthError("tls_init", "failed to parse custom CA certificate PEM", auth.ErrTlsFailure)
		}
		tlsConfig.RootCAs = rootCAs
	} else if c.CACertFile != "" {
		caBytes, err := os.ReadFile(c.CACertFile)
		if err != nil {
			return nil, auth.NewAuthError("tls_init", fmt.Sprintf("failed to read CA certificate file: %s", c.CACertFile), auth.ErrTlsFailure)
		}
		rootCAs = x509.NewCertPool()
		if !rootCAs.AppendCertsFromPEM(caBytes) {
			return nil, auth.NewAuthError("tls_init", "failed to parse CA certificate file contents", auth.ErrTlsFailure)
		}
		tlsConfig.RootCAs = rootCAs
	}

	transport := &http.Transport{
		Proxy: http.ProxyFromEnvironment,
		DialContext: (&net.Dialer{
			Timeout:   c.ConnectTimeout,
			KeepAlive: 30 * time.Second,
		}).DialContext,
		ForceAttemptHTTP2:     true,
		MaxIdleConns:          100,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   10 * time.Second,
		ExpectContinueTimeout: 1 * time.Second,
		TLSClientConfig:       tlsConfig,
	}

	return &http.Client{
		Transport: transport,
		Timeout:   c.RequestTimeout,
	}, nil
}
