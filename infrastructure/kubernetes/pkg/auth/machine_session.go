package auth

import (
	"fmt"
	"sync"
	"time"
)

const (
	// DefaultSessionSafetyMargin ensures tokens are refreshed before they reach actual expiration (e.g., 60s).
	DefaultSessionSafetyMargin = 60 * time.Second
)

// MachineSession holds an active in-memory machine session token issued by SecretVault.
type MachineSession struct {
	accessToken       string
	tokenType         string
	expiresAt         time.Time
	issuedAt          time.Time
	machineIdentityID string
	machineName       string
	workspaceID       string
	mu                sync.RWMutex
}

// NewMachineSession constructs a new thread-safe MachineSession.
func NewMachineSession(
	rawToken string,
	tokenType string,
	expiresInSeconds int64,
	machineIdentityID string,
	machineName string,
	workspaceID string,
) *MachineSession {
	now := time.Now()
	if tokenType == "" {
		tokenType = "Bearer"
	}
	if expiresInSeconds <= 0 {
		expiresInSeconds = 600 // Default 10 minutes
	}

	return &MachineSession{
		accessToken:       rawToken,
		tokenType:         tokenType,
		expiresAt:         now.Add(time.Duration(expiresInSeconds) * time.Second),
		issuedAt:          now,
		machineIdentityID: machineIdentityID,
		machineName:       machineName,
		workspaceID:       workspaceID,
	}
}

// IsValid reports whether the session is active and not within the expiration safety margin.
func (s *MachineSession) IsValid(now time.Time, safetyMargin time.Duration) bool {
	if s == nil {
		return false
	}
	s.mu.RLock()
	defer s.mu.RUnlock()

	if s.accessToken == "" {
		return false
	}
	if safetyMargin <= 0 {
		safetyMargin = DefaultSessionSafetyMargin
	}

	return now.Add(safetyMargin).Before(s.expiresAt)
}

// IsExpired reports whether the session has passed its absolute expiration time.
func (s *MachineSession) IsExpired(now time.Time) bool {
	if s == nil {
		return true
	}
	s.mu.RLock()
	defer s.mu.RUnlock()

	return now.After(s.expiresAt) || s.accessToken == ""
}

// GetAuthorizationHeader formats the HTTP Authorization header string.
func (s *MachineSession) GetAuthorizationHeader() string {
	if s == nil {
		return ""
	}
	s.mu.RLock()
	defer s.mu.RUnlock()

	if s.accessToken == "" {
		return ""
	}
	return fmt.Sprintf("%s %s", s.tokenType, s.accessToken)
}

// GetRawToken returns the raw access token for authorized client use only.
func (s *MachineSession) GetRawToken() string {
	if s == nil {
		return ""
	}
	s.mu.RLock()
	defer s.mu.RUnlock()

	return s.accessToken
}

// GetExpiresAt returns the token expiration timestamp.
func (s *MachineSession) GetExpiresAt() time.Time {
	if s == nil {
		return time.Time{}
	}
	s.mu.RLock()
	defer s.mu.RUnlock()

	return s.expiresAt
}

// GetMachineIdentityID returns the associated machine identity ID.
func (s *MachineSession) GetMachineIdentityID() string {
	if s == nil {
		return ""
	}
	s.mu.RLock()
	defer s.mu.RUnlock()

	return s.machineIdentityID
}

// GetMachineName returns the machine identity name.
func (s *MachineSession) GetMachineName() string {
	if s == nil {
		return ""
	}
	s.mu.RLock()
	defer s.mu.RUnlock()

	return s.machineName
}

// GetWorkspaceID returns the target workspace ID.
func (s *MachineSession) GetWorkspaceID() string {
	if s == nil {
		return ""
	}
	s.mu.RLock()
	defer s.mu.RUnlock()

	return s.workspaceID
}

// String implements fmt.Stringer and strictly redacts the token value.
func (s *MachineSession) String() string {
	if s == nil {
		return "<nil MachineSession>"
	}
	s.mu.RLock()
	defer s.mu.RUnlock()

	return fmt.Sprintf("MachineSession{machineID=%s, machineName=%s, workspaceID=%s, expiresAt=%s, token=[REDACTED]}",
		s.machineIdentityID, s.machineName, s.workspaceID, s.expiresAt.UTC().Format(time.RFC3339))
}

// GoString implements fmt.GoStringer and strictly redacts the token value.
func (s *MachineSession) GoString() string {
	return s.String()
}
