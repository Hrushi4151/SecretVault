package client

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"
)

func TestStaticTokenAuth(t *testing.T) {
	auth := &StaticTokenAuth{Token: "test-token-123"}
	token, err := auth.GetToken(context.Background(), http.DefaultClient, "http://localhost")
	if err != nil {
		t.Fatalf("StaticTokenAuth.GetToken failed: %v", err)
	}
	if token != "test-token-123" {
		t.Errorf("expected token 'test-token-123', got %q", token)
	}

	emptyAuth := &StaticTokenAuth{Token: ""}
	_, err = emptyAuth.GetToken(context.Background(), http.DefaultClient, "http://localhost")
	if err == nil {
		t.Errorf("expected error for empty static token, got nil")
	}
}

func TestMachineIdentityAuth_OidcExchangeAndCaching(t *testing.T) {
	var exchangeCount int
	var mu sync.Mutex

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/api/v1/auth/oidc/token" {
			mu.Lock()
			exchangeCount++
			mu.Unlock()

			var req OidcTokenExchangeRequest
			if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
				http.Error(w, err.Error(), http.StatusBadRequest)
				return
			}

			if req.Token == "" && req.SubjectToken == "" {
				http.Error(w, "missing token", http.StatusBadRequest)
				return
			}

			w.Header().Set("Content-Type", "application/json")
			json.NewEncoder(w).Encode(ApiResponse[OidcTokenResponse]{
				Success: true,
				Data: OidcTokenResponse{
					AccessToken: "issued-machine-session-token",
					TokenType:   "Bearer",
					ExpiresIn:   3600,
				},
				Message:   "Authenticated",
				Timestamp: time.Now(),
			})
			return
		}
		http.NotFound(w, r)
	}))
	defer server.Close()

	auth := &MachineIdentityAuth{
		WorkspaceID:  "ws-123",
		Audience:     "secretvault-api",
		SubjectToken: "jwt.header.payload.signature",
		TokenType:    "urn:ietf:params:oauth:token-type:jwt",
	}

	// 1. Initial exchange
	token1, err := auth.GetToken(context.Background(), server.Client(), server.URL)
	if err != nil {
		t.Fatalf("first GetToken failed: %v", err)
	}
	if token1 != "issued-machine-session-token" {
		t.Errorf("expected 'issued-machine-session-token', got %q", token1)
	}

	// 2. Second call should use cache without hitting server again
	token2, err := auth.GetToken(context.Background(), server.Client(), server.URL)
	if err != nil {
		t.Fatalf("second GetToken failed: %v", err)
	}
	if token2 != "issued-machine-session-token" {
		t.Errorf("expected 'issued-machine-session-token', got %q", token2)
	}

	mu.Lock()
	count := exchangeCount
	mu.Unlock()
	if count != 1 {
		t.Errorf("expected exactly 1 exchange due to caching, got %d", count)
	}

	// 3. Test concurrent access safety
	var wg sync.WaitGroup
	for i := 0; i < 20; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			tok, err := auth.GetToken(context.Background(), server.Client(), server.URL)
			if err != nil || tok != "issued-machine-session-token" {
				t.Errorf("concurrent GetToken failed: %v, tok: %s", err, tok)
			}
		}()
	}
	wg.Wait()
}
