package auth

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"
)

// Helper to create a fake signed/unsigned JWT string for testing
func createTestJwt(claims map[string]interface{}) string {
	header := map[string]string{"alg": "RS256", "typ": "JWT"}
	headerBytes, _ := json.Marshal(header)
	headerB64 := base64.RawURLEncoding.EncodeToString(headerBytes)

	payloadBytes, _ := json.Marshal(claims)
	payloadB64 := base64.RawURLEncoding.EncodeToString(payloadBytes)

	sigB64 := base64.RawURLEncoding.EncodeToString([]byte("fake_signature_for_test"))
	return fmt.Sprintf("%s.%s.%s", headerB64, payloadB64, sigB64)
}

func TestProjectedTokenProvider_ValidToken(t *testing.T) {
	tmpDir := t.TempDir()
	tokenFile := filepath.Join(tmpDir, "token")

	claims := map[string]interface{}{
		"iss": "https://kubernetes.default.svc",
		"sub": "system:serviceaccount:default:payment-app",
		"aud": []string{"https://secretvault.internal"},
		"exp": time.Now().Add(1 * time.Hour).Unix(),
		"kubernetes.io/serviceaccount/service-account.name": "payment-app",
		"kubernetes.io/serviceaccount/namespace":           "default",
	}
	jwtStr := createTestJwt(claims)
	if err := os.WriteFile(tokenFile, []byte(jwtStr), 0600); err != nil {
		t.Fatalf("failed to write test token file: %v", err)
	}

	provider, err := NewProjectedTokenProvider(TokenProviderOptions{
		TokenPath:        tokenFile,
		ExpectedAudience: "https://secretvault.internal",
	})
	if err != nil {
		t.Fatalf("failed to create token provider: %v", err)
	}

	token, err := provider.GetToken()
	if err != nil {
		t.Fatalf("unexpected error reading valid token: %v", err)
	}
	if token != jwtStr {
		t.Errorf("token mismatch, got %s, want %s", token, jwtStr)
	}

	parsedClaims, err := provider.InspectClaims()
	if err != nil {
		t.Fatalf("unexpected error inspecting claims: %v", err)
	}
	if parsedClaims.ServiceAccount != "payment-app" {
		t.Errorf("expected service account 'payment-app', got '%s'", parsedClaims.ServiceAccount)
	}
}

func TestProjectedTokenProvider_MissingFile(t *testing.T) {
	provider, _ := NewProjectedTokenProvider(TokenProviderOptions{
		TokenPath: "/non/existent/path/to/token",
	})
	_, err := provider.GetToken()
	if err == nil {
		t.Fatalf("expected error for missing token file, got nil")
	}
}

func TestProjectedTokenProvider_EmptyFile(t *testing.T) {
	tmpDir := t.TempDir()
	tokenFile := filepath.Join(tmpDir, "empty_token")
	_ = os.WriteFile(tokenFile, []byte(""), 0600)

	provider, _ := NewProjectedTokenProvider(TokenProviderOptions{
		TokenPath: tokenFile,
	})
	_, err := provider.GetToken()
	if err == nil {
		t.Fatalf("expected error for empty token file, got nil")
	}
}

func TestProjectedTokenProvider_OversizedFile(t *testing.T) {
	tmpDir := t.TempDir()
	tokenFile := filepath.Join(tmpDir, "huge_token")
	hugeData := make([]byte, MaxTokenSizeBytes+1024)
	_ = os.WriteFile(tokenFile, hugeData, 0600)

	provider, _ := NewProjectedTokenProvider(TokenProviderOptions{
		TokenPath: tokenFile,
	})
	_, err := provider.GetToken()
	if err == nil {
		t.Fatalf("expected error for oversized token file, got nil")
	}
}

func TestProjectedTokenProvider_ExpiredToken(t *testing.T) {
	tmpDir := t.TempDir()
	tokenFile := filepath.Join(tmpDir, "expired_token")

	claims := map[string]interface{}{
		"iss": "https://kubernetes.default.svc",
		"sub": "system:serviceaccount:default:app",
		"exp": time.Now().Add(-10 * time.Minute).Unix(),
	}
	jwtStr := createTestJwt(claims)
	_ = os.WriteFile(tokenFile, []byte(jwtStr), 0600)

	provider, _ := NewProjectedTokenProvider(TokenProviderOptions{
		TokenPath: tokenFile,
	})
	_, err := provider.GetToken()
	if err == nil {
		t.Fatalf("expected error for expired token, got nil")
	}
}

func TestProjectedTokenProvider_AudienceMismatch(t *testing.T) {
	tmpDir := t.TempDir()
	tokenFile := filepath.Join(tmpDir, "wrong_aud_token")

	claims := map[string]interface{}{
		"iss": "https://kubernetes.default.svc",
		"aud": []string{"https://other-vault.internal"},
		"exp": time.Now().Add(1 * time.Hour).Unix(),
	}
	jwtStr := createTestJwt(claims)
	_ = os.WriteFile(tokenFile, []byte(jwtStr), 0600)

	provider, _ := NewProjectedTokenProvider(TokenProviderOptions{
		TokenPath:        tokenFile,
		ExpectedAudience: "https://secretvault.internal",
	})
	_, err := provider.GetToken()
	if err == nil {
		t.Fatalf("expected error for audience mismatch, got nil")
	}
}

func TestMachineSession_LifecycleAndRedaction(t *testing.T) {
	session := NewMachineSession("sv_machine_secret_token_12345", "Bearer", 600, "machine-uuid-1", "ci-builder", "ws-uuid-1")

	// Verify safe redaction in String() and GoString()
	str := session.String()
	if str == "" || str == "sv_machine_secret_token_12345" {
		t.Errorf("session.String() leaked raw token: %s", str)
	}
	if session.GetRawToken() != "sv_machine_secret_token_12345" {
		t.Errorf("GetRawToken mismatch")
	}

	// Active session validation
	now := time.Now()
	if !session.IsValid(now, 60*time.Second) {
		t.Errorf("session should be valid with 600s TTL and 60s margin")
	}
	if session.IsExpired(now) {
		t.Errorf("session should not be expired")
	}

	// Simulated future validation (near expiry)
	future := now.Add(550 * time.Second) // 50s remaining < 60s margin
	if session.IsValid(future, 60*time.Second) {
		t.Errorf("session should be marked invalid when within safety margin")
	}
}

func TestOidcExchangeClient_SuccessfulExchange(t *testing.T) {
	tmpDir := t.TempDir()
	tokenFile := filepath.Join(tmpDir, "token")
	jwtStr := createTestJwt(map[string]interface{}{
		"iss": "https://kubernetes.default.svc",
		"exp": time.Now().Add(1 * time.Hour).Unix(),
	})
	_ = os.WriteFile(tokenFile, []byte(jwtStr), 0600)

	provider, _ := NewProjectedTokenProvider(TokenProviderOptions{TokenPath: tokenFile})

	// Mock SecretVault backend OIDC server
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/auth/oidc/token" {
			http.NotFound(w, r)
			return
		}
		var payload OidcExchangePayload
		_ = json.NewDecoder(r.Body).Decode(&payload)
		if payload.Token != jwtStr {
			http.Error(w, "invalid token payload", http.StatusBadRequest)
			return
		}

		resp := OidcExchangeApiResponse{
			Success: true,
			Message: "OIDC workload authenticated successfully",
			Data: &OidcExchangeDataPayload{
				AccessToken: "sv_machine_test_session_xyz789",
				TokenType:   "Bearer",
				ExpiresIn:   600,
				MachineIdentity: &MachineIdentityDetail{
					ID:          "machine-123",
					WorkspaceID: "ws-456",
					Name:        "k8s-operator",
					Status:      "ACTIVE",
				},
			},
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(resp)
	}))
	defer server.Close()

	client, err := NewOidcExchangeClient(OidcExchangeOptions{
		BaseURL:       server.URL,
		TokenProvider: provider,
	})
	if err != nil {
		t.Fatalf("failed to create exchange client: %v", err)
	}

	session, err := client.GetValidSession(context.Background())
	if err != nil {
		t.Fatalf("unexpected exchange error: %v", err)
	}
	if session.GetRawToken() != "sv_machine_test_session_xyz789" {
		t.Errorf("unexpected session token: %s", session.GetRawToken())
	}
	if session.GetMachineName() != "k8s-operator" {
		t.Errorf("unexpected machine name: %s", session.GetMachineName())
	}

	// Verify caching on second call
	cachedSession, err := client.GetValidSession(context.Background())
	if err != nil {
		t.Fatalf("unexpected error on cached session call: %v", err)
	}
	if cachedSession != session {
		t.Errorf("session was not cached")
	}
}
