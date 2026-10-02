# Phase 9: REST API Reference

All endpoints are rooted under `/api/v1` and require standard JWT or Machine Bearer authentication unless designated as public.

---

## 1. OIDC Token Exchange (Public / Workload)

### `POST /api/v1/auth/oidc/token`
Exchanges an OIDC JWT ID token for a SecretVault machine bearer token.

**Request Body**:
```json
{
  "oidcProviderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "machineIdentityId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "idToken": "eyJhbGciOiJSUzI1NiIs..."
}
```

**Response (200 OK)**:
```json
{
  "accessToken": "sv_machine_7f8a9b2c3d4e5f6a...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "machineIdentityId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "workspaceId": "e1f2a3b4-c5d6-e7f8-a9b2-c3d4e5f6a7b8"
}
```

---

## 2. Machine Identities API

### `GET /api/v1/workspaces/{workspaceId}/machines`
Lists all machine identities in the workspace.

### `POST /api/v1/workspaces/{workspaceId}/machines`
Creates a new machine identity.
```json
{
  "name": "github-deploy-runner",
  "description": "Production CD Runner",
  "type": "CI_CD",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

### `GET /api/v1/workspaces/{workspaceId}/machines/{machineId}`
Gets details of a machine identity, including trust policies and grants.

### `PUT /api/v1/workspaces/{workspaceId}/machines/{machineId}`
Updates machine name, description, or expiration.

### `POST /api/v1/workspaces/{workspaceId}/machines/{machineId}/disable`
Disables the machine identity and invalidates active sessions.

### `POST /api/v1/workspaces/{workspaceId}/machines/{machineId}/enable`
Re-enables a disabled machine identity.

### `POST /api/v1/workspaces/{workspaceId}/machines/{machineId}/revoke`
Permanently revokes the machine identity.

### `DELETE /api/v1/workspaces/{workspaceId}/machines/{machineId}`
Soft-deletes the machine identity.

---

## 3. Machine Access Grants API

### `GET /api/v1/workspaces/{workspaceId}/machines/{machineId}/grants`
Lists active access grants for the machine.

### `POST /api/v1/workspaces/{workspaceId}/machines/{machineId}/grants`
Creates an access grant.
```json
{
  "scope": "ENVIRONMENT",
  "projectId": "f3b7d120-...",
  "environmentId": "a82b99c1-...",
  "permission": "READ_SECRET"
}
```

### `DELETE /api/v1/workspaces/{workspaceId}/machines/{machineId}/grants/{grantId}`
Removes an access grant.

---

## 4. Machine Sessions API

### `GET /api/v1/workspaces/{workspaceId}/machines/{machineId}/sessions`
Lists active sessions with IP and user-agent metadata.

### `DELETE /api/v1/workspaces/{workspaceId}/machines/{machineId}/sessions/{sessionId}`
Revokes a specific active session.

---

## 5. OIDC Providers API

### `GET /api/v1/workspaces/{workspaceId}/oidc-providers`
Lists configured OIDC providers.

### `POST /api/v1/workspaces/{workspaceId}/oidc-providers`
Registers a new OIDC provider.
```json
{
  "name": "GitHub Actions",
  "type": "GITHUB_ACTIONS",
  "issuerUrl": "https://token.actions.githubusercontent.com",
  "audience": "secretvault",
  "jwksUrl": "https://token.actions.githubusercontent.com/.well-known/jwks"
}
```

### `PUT /api/v1/workspaces/{workspaceId}/oidc-providers/{providerId}`
Updates provider configuration.

### `DELETE /api/v1/workspaces/{workspaceId}/oidc-providers/{providerId}`
Deletes provider configuration.

---

## 6. OIDC Trust Policies API

### `GET /api/v1/workspaces/{workspaceId}/machines/{machineId}/trust-policies`
Lists trust policies bound to a machine identity.

### `POST /api/v1/workspaces/{workspaceId}/machines/{machineId}/trust-policies`
Creates a trust policy with claim rules.
```json
{
  "oidcProviderId": "3fa85f64-...",
  "name": "Release Workflow",
  "description": "Enforces main branch only",
  "rules": [
    {
      "claimKey": "repository",
      "operator": "EQUALS",
      "expectedValue": "Hrushi4151/Rally"
    },
    {
      "claimKey": "ref",
      "operator": "EQUALS",
      "expectedValue": "refs/heads/main"
    }
  ]
}
```

### `DELETE /api/v1/workspaces/{workspaceId}/machines/{machineId}/trust-policies/{policyId}`
Deletes a trust policy.
