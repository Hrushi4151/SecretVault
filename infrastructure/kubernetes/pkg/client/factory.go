package client

import (
	"context"
	"fmt"
	"sync"
	"time"

	"github.com/secretvault/operator/pkg/auth"
)

// ClientFactory creates and manages authenticated SecretVaultClients for Kubernetes workloads.
type ClientFactory interface {
	// GetClient returns an authenticated SecretVaultClient configured for the specified workspace and auth parameters.
	GetClient(ctx context.Context, namespace string, authOpts auth.TokenProviderOptions, providerID, issuer string) (*SecretVaultClient, error)
}

// DefaultClientFactory implements ClientFactory using caching and connection pooling.
type DefaultClientFactory struct {
	baseConfig ClientConfig
	clients    map[string]*SecretVaultClient
	mu         sync.RWMutex
}

// NewClientFactory creates a new DefaultClientFactory.
func NewClientFactory(baseConfig ClientConfig) *DefaultClientFactory {
	return &DefaultClientFactory{
		baseConfig: baseConfig,
		clients:    make(map[string]*SecretVaultClient),
	}
}

// GetClient constructs or returns a cached SecretVaultClient with workload OIDC exchange.
func (f *DefaultClientFactory) GetClient(
	ctx context.Context,
	namespace string,
	authOpts auth.TokenProviderOptions,
	providerID string,
	issuer string,
) (*SecretVaultClient, error) {
	cacheKey := fmt.Sprintf("%s:%s:%s:%s:%s", namespace, authOpts.TokenPath, authOpts.ExpectedAudience, providerID, issuer)

	f.mu.RLock()
	if c, ok := f.clients[cacheKey]; ok {
		f.mu.RUnlock()
		return c, nil
	}
	f.mu.RUnlock()

	f.mu.Lock()
	defer f.mu.Unlock()

	if c, ok := f.clients[cacheKey]; ok {
		return c, nil
	}

	tokenProvider, err := auth.NewProjectedTokenProvider(authOpts)
	if err != nil {
		return nil, fmt.Errorf("failed to create projected token provider: %w", err)
	}

	exchanger, err := auth.NewOidcExchangeClient(auth.OidcExchangeOptions{
		BaseURL:       f.baseConfig.BaseURL,
		ProviderID:    providerID,
		Issuer:        issuer,
		TokenProvider: tokenProvider,
		SafetyMargin:  60 * time.Second,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to create OIDC exchange client: %w", err)
	}

	client, err := NewSecretVaultClient(f.baseConfig, exchanger)
	if err != nil {
		return nil, fmt.Errorf("failed to create SecretVault client: %w", err)
	}

	f.clients[cacheKey] = client
	return client, nil
}
