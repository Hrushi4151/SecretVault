package client

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestTenantIsolation_WorkspaceBoundary(t *testing.T) {
	wsA := "11111111-1111-1111-1111-111111111111"
	wsB := "22222222-2222-2222-2222-222222222222"

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		tenantHeader := r.Header.Get("X-Workspace-ID")
		if r.URL.Path == "/api/v1/workspaces/"+wsB {
			http.Error(w, `{"success":false,"message":"Cross-tenant IDOR access denied"}`, http.StatusForbidden)
			return
		}
		if tenantHeader == wsA {
			w.WriteHeader(http.StatusOK)
			w.Write([]byte(`{"success":true,"data":{"id":"` + wsA + `","name":"Workspace A"}}`))
			return
		}
		http.Error(w, `{"success":false,"message":"Invalid tenant"}`, http.StatusUnauthorized)
	}))
	defer server.Close()

	clientA, err := NewClient(Config{
		BaseURL:            server.URL,
		Auth:               &StaticTokenAuth{Token: "token-a"},
		DefaultWorkspaceID: wsA,
	})
	if err != nil {
		t.Fatalf("NewClient failed: %v", err)
	}

	// 1. Authorized access to own workspace
	ws, err := clientA.GetWorkspace(context.Background(), wsA)
	if err != nil {
		t.Errorf("expected success for own workspace, got %v", err)
	}
	if ws.ID != wsA {
		t.Errorf("expected workspace ID %s, got %s", wsA, ws.ID)
	}

	// 2. Cross-tenant attempt to access Workspace B through Client A must fail with 403 Forbidden
	_, err = clientA.GetWorkspace(context.Background(), wsB)
	if err == nil {
		t.Fatalf("expected IDOR error when querying workspace B with tenant A header, got nil")
	}
	if !IsForbidden(err) {
		t.Errorf("expected IsForbidden to be true, got %v", err)
	}
}
