package client

import "time"

// ApiResponse is the standard envelope for SecretVault REST responses.
type ApiResponse[T any] struct {
	Success   bool      `json:"success"`
	Data      T         `json:"data"`
	Message   string    `json:"message,omitempty"`
	Timestamp time.Time `json:"timestamp"`
	TraceId   string    `json:"traceId,omitempty"`
}

// WorkspaceResponse represents a SecretVault Workspace.
type WorkspaceResponse struct {
	ID          string    `json:"id"`
	Name        string    `json:"name"`
	Slug        string    `json:"slug"`
	Description string    `json:"description,omitempty"`
	CreatedAt   time.Time `json:"createdAt"`
	UpdatedAt   time.Time `json:"updatedAt"`
}

// CreateWorkspaceRequest payload for creating a workspace.
type CreateWorkspaceRequest struct {
	Name        string `json:"name"`
	Slug        string `json:"slug"`
	Description string `json:"description,omitempty"`
}

// ProjectResponse represents a SecretVault Project.
type ProjectResponse struct {
	ID          string    `json:"id"`
	WorkspaceID string    `json:"workspaceId"`
	Name        string    `json:"name"`
	Slug        string    `json:"slug"`
	Description string    `json:"description,omitempty"`
	CreatedAt   time.Time `json:"createdAt"`
	UpdatedAt   time.Time `json:"updatedAt"`
}

// CreateProjectRequest payload for creating a project.
type CreateProjectRequest struct {
	Name        string `json:"name"`
	Slug        string `json:"slug"`
	Description string `json:"description,omitempty"`
}

// UpdateProjectRequest payload for updating a project.
type UpdateProjectRequest struct {
	Name        string `json:"name,omitempty"`
	Description string `json:"description,omitempty"`
}

// EnvironmentResponse represents a deployment tier in a Project.
type EnvironmentResponse struct {
	ID          string    `json:"id"`
	WorkspaceID string    `json:"workspaceId"`
	ProjectID   string    `json:"projectId"`
	Name        string    `json:"name"`
	Slug        string    `json:"slug"`
	Type        string    `json:"type"`
	IsProtected bool      `json:"isProtected"`
	CreatedAt   time.Time `json:"createdAt"`
	UpdatedAt   time.Time `json:"updatedAt"`
}

// CreateEnvironmentRequest payload for creating an environment.
type CreateEnvironmentRequest struct {
	Name        string `json:"name"`
	Slug        string `json:"slug"`
	Type        string `json:"type"`
	IsProtected bool   `json:"isProtected"`
}

// UpdateEnvironmentRequest payload for updating an environment.
type UpdateEnvironmentRequest struct {
	Name        string `json:"name,omitempty"`
	Type        string `json:"type,omitempty"`
	IsProtected *bool  `json:"isProtected,omitempty"`
}

// SecretMetadataResponse represents non-sensitive secret metadata (never contains plaintext).
type SecretMetadataResponse struct {
	ID            string    `json:"id"`
	WorkspaceID   string    `json:"workspaceId"`
	ProjectID     string    `json:"projectId"`
	EnvironmentID string    `json:"environmentId"`
	Name          string    `json:"name"`
	ContentType   string    `json:"contentType"`
	Version       int64     `json:"version"`
	Status        string    `json:"status"`
	Comment       string    `json:"comment,omitempty"`
	CreatedAt     time.Time `json:"createdAt"`
	UpdatedAt     time.Time `json:"updatedAt"`
	CreatedBy     string    `json:"createdBy,omitempty"`
	UpdatedBy     string    `json:"updatedBy,omitempty"`
}

// CreateSecretRequest payload for creating a secret (write-only).
type CreateSecretRequest struct {
	Name        string `json:"name"`
	Value       string `json:"value"`
	ContentType string `json:"contentType,omitempty"`
	Comment     string `json:"comment,omitempty"`
}

// UpdateSecretRequest payload for updating/appending a new secret version.
type UpdateSecretRequest struct {
	Value       string `json:"value,omitempty"`
	ContentType string `json:"contentType,omitempty"`
	Comment     string `json:"comment,omitempty"`
}

// MachineIdentityResponse represents a machine/workload identity.
type MachineIdentityResponse struct {
	ID                 string    `json:"id"`
	WorkspaceID        string    `json:"workspaceId"`
	Name               string    `json:"name"`
	Description        string    `json:"description,omitempty"`
	Role               string    `json:"role"`
	MaxTokenTtlSeconds int64     `json:"maxTokenTtlSeconds"`
	Status             string    `json:"status"`
	CreatedAt          time.Time `json:"createdAt"`
	UpdatedAt          time.Time `json:"updatedAt"`
}

// CreateMachineIdentityRequest payload for creating a machine identity.
type CreateMachineIdentityRequest struct {
	Name               string `json:"name"`
	Description        string `json:"description,omitempty"`
	Role               string `json:"role,omitempty"`
	MaxTokenTtlSeconds int64  `json:"maxTokenTtlSeconds,omitempty"`
}

// UpdateMachineIdentityRequest payload for updating a machine identity.
type UpdateMachineIdentityRequest struct {
	Name               string `json:"name,omitempty"`
	Description        string `json:"description,omitempty"`
	Role               string `json:"role,omitempty"`
	MaxTokenTtlSeconds int64  `json:"maxTokenTtlSeconds,omitempty"`
	Enabled            *bool  `json:"enabled,omitempty"`
}

// ProviderIntegrationResponse represents an external provider integration.
type ProviderIntegrationResponse struct {
	ID           string            `json:"id"`
	WorkspaceID  string            `json:"workspaceId"`
	Name         string            `json:"name"`
	ProviderType string            `json:"providerType"`
	Status       string            `json:"status"`
	Config       map[string]string `json:"config,omitempty"`
	CreatedAt    time.Time         `json:"createdAt"`
	UpdatedAt    time.Time         `json:"updatedAt"`
}

// CreateProviderIntegrationRequest payload for creating a provider integration.
type CreateProviderIntegrationRequest struct {
	Name         string            `json:"name"`
	ProviderType string            `json:"providerType"`
	Config       map[string]string `json:"config,omitempty"`
	Credentials  map[string]string `json:"credentials,omitempty"`
}

// UpdateProviderIntegrationRequest payload for updating a provider integration.
type UpdateProviderIntegrationRequest struct {
	Name        string            `json:"name,omitempty"`
	Config      map[string]string `json:"config,omitempty"`
	Credentials map[string]string `json:"credentials,omitempty"`
	Enabled     *bool             `json:"enabled,omitempty"`
}

// OidcTokenExchangeRequest payload for exchanging workload tokens.
type OidcTokenExchangeRequest struct {
	Audience         string `json:"audience"`
	SubjectToken     string `json:"subjectToken"`
	SubjectTokenType string `json:"subjectTokenType"`
	GrantType        string `json:"grantType"`
}

// OidcTokenResponse response from OIDC exchange.
type OidcTokenResponse struct {
	AccessToken string `json:"accessToken"`
	TokenType   string `json:"tokenType"`
	ExpiresIn   int64  `json:"expiresIn"`
	Scope       string `json:"scope,omitempty"`
}
