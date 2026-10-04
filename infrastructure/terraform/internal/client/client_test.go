package client

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func TestClient_BasicCRUD(t *testing.T) {
	wsID := "550e8400-e29b-41d4-a716-446655440000"
	projID := "550e8400-e29b-41d4-a716-446655440001"
	envID := "550e8400-e29b-41d4-a716-446655440002"
	secID := "550e8400-e29b-41d4-a716-446655440003"

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")

		// Verify Workspace Scoping Header
		if r.Header.Get("X-Workspace-ID") != wsID {
			http.Error(w, `{"success":false,"message":"Missing or invalid X-Workspace-ID"}`, http.StatusBadRequest)
			return
		}

		// Verify Bearer Token Header
		if r.Header.Get("Authorization") != "Bearer secret-token-xyz" {
			http.Error(w, `{"success":false,"message":"Unauthorized"}`, http.StatusUnauthorized)
			return
		}

		switch {
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/workspaces/"+wsID:
			json.NewEncoder(w).Encode(ApiResponse[WorkspaceResponse]{
				Success: true,
				Data: WorkspaceResponse{
					ID:        wsID,
					Name:      "Core Platform",
					Slug:      "core-platform",
					CreatedAt: time.Now(),
					UpdatedAt: time.Now(),
				},
			})

		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/workspaces/"+wsID+"/projects":
			var req CreateProjectRequest
			json.NewDecoder(r.Body).Decode(&req)
			json.NewEncoder(w).Encode(ApiResponse[ProjectResponse]{
				Success: true,
				Data: ProjectResponse{
					ID:          projID,
					WorkspaceID: wsID,
					Name:        req.Name,
					Slug:        req.Slug,
					CreatedAt:   time.Now(),
					UpdatedAt:   time.Now(),
				},
			})

		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/workspaces/"+wsID+"/projects/"+projID:
			json.NewEncoder(w).Encode(ApiResponse[ProjectResponse]{
				Success: true,
				Data: ProjectResponse{
					ID:          projID,
					WorkspaceID: wsID,
					Name:        "Backend App",
					Slug:        "backend-app",
					CreatedAt:   time.Now(),
					UpdatedAt:   time.Now(),
				},
			})

		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/workspaces/"+wsID+"/projects/"+projID+"/environments":
			var req CreateEnvironmentRequest
			json.NewDecoder(r.Body).Decode(&req)
			json.NewEncoder(w).Encode(ApiResponse[EnvironmentResponse]{
				Success: true,
				Data: EnvironmentResponse{
					ID:          envID,
					WorkspaceID: wsID,
					ProjectID:   projID,
					Name:        req.Name,
					Slug:        req.Slug,
					Type:        req.Type,
					IsProtected: req.IsProtected,
					CreatedAt:   time.Now(),
					UpdatedAt:   time.Now(),
				},
			})

		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/workspaces/"+wsID+"/projects/"+projID+"/environments/"+envID+"/secrets":
			var req CreateSecretRequest
			json.NewDecoder(r.Body).Decode(&req)
			json.NewEncoder(w).Encode(ApiResponse[SecretMetadataResponse]{
				Success: true,
				Data: SecretMetadataResponse{
					ID:            secID,
					WorkspaceID:   wsID,
					ProjectID:     projID,
					EnvironmentID: envID,
					Name:          req.Name,
					ContentType:   req.ContentType,
					Version:       1,
					Status:        "ACTIVE",
					CreatedAt:     time.Now(),
					UpdatedAt:     time.Now(),
				},
			})

		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/workspaces/"+wsID+"/projects/"+projID+"/environments/"+envID+"/secrets/"+secID:
			// Read metadata - ZERO plaintext in response
			json.NewEncoder(w).Encode(ApiResponse[SecretMetadataResponse]{
				Success: true,
				Data: SecretMetadataResponse{
					ID:            secID,
					WorkspaceID:   wsID,
					ProjectID:     projID,
					EnvironmentID: envID,
					Name:          "DATABASE_PASSWORD",
					ContentType:   "TEXT",
					Version:       1,
					Status:        "ACTIVE",
					CreatedAt:     time.Now(),
					UpdatedAt:     time.Now(),
				},
			})

		case r.Method == http.MethodDelete:
			w.WriteHeader(http.StatusNoContent)

		default:
			http.NotFound(w, r)
		}
	}))
	defer server.Close()

	c, err := NewClient(Config{
		BaseURL:            server.URL,
		Auth:               &StaticTokenAuth{Token: "secret-token-xyz"},
		DefaultWorkspaceID: wsID,
		Timeout:            5 * time.Second,
		MaxRetries:         2,
	})
	if err != nil {
		t.Fatalf("NewClient failed: %v", err)
	}

	// 1. Get Workspace
	ws, err := c.GetWorkspace(context.Background(), wsID)
	if err != nil || ws.ID != wsID {
		t.Errorf("GetWorkspace failed: %v", err)
	}

	// 2. Create Project
	proj, err := c.CreateProject(context.Background(), wsID, CreateProjectRequest{
		Name: "Backend App",
		Slug: "backend-app",
	})
	if err != nil || proj.ID != projID {
		t.Errorf("CreateProject failed: %v", err)
	}

	// 3. Create Environment
	env, err := c.CreateEnvironment(context.Background(), wsID, projID, CreateEnvironmentRequest{
		Name:        "Production",
		Slug:        "prod",
		Type:        "PRODUCTION",
		IsProtected: true,
	})
	if err != nil || env.ID != envID {
		t.Errorf("CreateEnvironment failed: %v", err)
	}

	// 4. Create Secret (Write-only value)
	sec, err := c.CreateSecret(context.Background(), wsID, projID, envID, CreateSecretRequest{
		Name:        "DATABASE_PASSWORD",
		Value:       "super-secret-pw",
		ContentType: "TEXT",
	})
	if err != nil || sec.ID != secID {
		t.Errorf("CreateSecret failed: %v", err)
	}

	// 5. Read Secret Metadata (STRICTLY Zero-Plaintext)
	meta, err := c.GetSecretMetadata(context.Background(), wsID, projID, envID, secID)
	if err != nil || meta.Name != "DATABASE_PASSWORD" {
		t.Errorf("GetSecretMetadata failed: %v", err)
	}

	// 6. Delete Secret
	err = c.DeleteSecret(context.Background(), wsID, projID, envID, secID)
	if err != nil {
		t.Errorf("DeleteSecret failed: %v", err)
	}
}

func TestClient_RetryLogic(t *testing.T) {
	var attempts int32

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		count := atomic.AddInt32(&attempts, 1)
		if count < 3 {
			w.Header().Set("Retry-After", "1")
			http.Error(w, `{"success":false,"message":"Rate limit exceeded"}`, http.StatusTooManyRequests)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(ApiResponse[WorkspaceResponse]{
			Success: true,
			Data: WorkspaceResponse{
				ID:   "ws-123",
				Name: "Recovered Workspace",
			},
		})
	}))
	defer server.Close()

	c, err := NewClient(Config{
		BaseURL:      server.URL,
		Auth:         &StaticTokenAuth{Token: "test"},
		MaxRetries:   3,
		RetryWaitMin: 10 * time.Millisecond,
		RetryWaitMax: 50 * time.Millisecond,
	})
	if err != nil {
		t.Fatalf("NewClient failed: %v", err)
	}

	ws, err := c.GetWorkspace(context.Background(), "ws-123")
	if err != nil {
		t.Fatalf("expected successful retry, got error: %v", err)
	}
	if ws.Name != "Recovered Workspace" {
		t.Errorf("expected recovered workspace, got %s", ws.Name)
	}
	if atomic.LoadInt32(&attempts) != 3 {
		t.Errorf("expected 3 attempts, got %d", atomic.LoadInt32(&attempts))
	}
}
