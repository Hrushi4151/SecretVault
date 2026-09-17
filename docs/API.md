# SecretVault — API Design & Contract Standards

## 1. Protocol & Conventions

- **Base Path:** `/api/v1`
- **Format:** JSON (`application/json; charset=UTF-8`)
- **OpenAPI UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec:** `http://localhost:8080/v3/api-docs`

---

## 2. Standard HTTP Headers

### Request Headers
- `Authorization: Bearer <JWT>` — Authentication token for user or service account.
- `X-Correlation-ID: <UUID>` — Client-provided or gateway trace ID (generated automatically if omitted).
- `X-Workspace-ID: <UUID>` — Active workspace context.

### Response Headers
- `X-Correlation-ID: <UUID>` — Tracing identifier echoed in all responses.
- `X-Request-ID: <UUID>` — Unique request ID.

---

## 3. Standard Error Response Schema (`RFC-7807`) [IMPLEMENTED]

All error responses return a standardized, sanitized JSON payload without leaking stack traces, database structure, or internal credentials:

```json
{
  "timestamp": "2026-09-17T18:00:00.000Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Validation failed for one or more fields",
  "requestId": "4fa81566-0749-410a-8dc4-b77873ad9be0",
  "errors": [
    {
      "field": "email",
      "message": "Please provide a valid email address"
    }
  ]
}
```

### Standard Error Codes:
- `400 BAD_REQUEST` / `VALIDATION_ERROR` — Malformed JSON or failed bean validation constraints.
- `401 UNAUTHORIZED` — Missing, expired, or malformed JWT token.
- `403 FORBIDDEN` — Authenticated identity lacks permission for this organization/environment.
- `404 NOT_FOUND` / `RESOURCE_NOT_FOUND` — Entity does not exist within the caller's tenant.
- `409 CONFLICT` / `RESOURCE_CONFLICT` — Duplicate name or concurrent update conflict.
- `429 TOO_MANY_REQUESTS` — Exceeded API rate limit.
- `500 INTERNAL_SERVER_ERROR` — Unexpected failure (sanitized; contact support with `requestId`).

---

## 4. Representative API Endpoints

### 4.1 System & Health [IMPLEMENTED]
- `GET /api/v1/health` — Public baseline service health probe.
- `GET /actuator/health` — Spring Boot Actuator deep health probe (DB, Redis, disk).

### 4.2 Authentication & Workspace [IMPLEMENTED]
- `POST /api/v1/auth/register` — Create user account & tenant organization.
- `POST /api/v1/auth/login` — Authenticate and receive JWT + refresh token.
- `POST /api/v1/auth/refresh` — Issue fresh access token using valid refresh token.
- `GET /api/v1/auth/me` — Inspect caller identity and active organizations.
- `GET /api/v1/workspaces` — List workspaces for the authenticated user.
- `POST /api/v1/workspaces` — Create a new workspace.
- `GET /api/v1/workspaces/{id}` — Get workspace details.
- `GET /api/v1/workspaces/{id}/members` — List all members of a workspace.
- `POST /api/v1/workspaces/{id}/members` — Add a registered user as a workspace member.
- `PATCH /api/v1/workspaces/{id}/members/{userId}` — Update member role (Enforces last OWNER safeguard).
- `DELETE /api/v1/workspaces/{id}/members/{userId}` — Remove member or leave workspace (Enforces last OWNER safeguard).
- `GET /api/v1/workspaces/{id}/settings` — Get workspace configuration and policies.
- `PATCH /api/v1/workspaces/{id}/settings` — Update workspace settings (Requires `OWNER` or `ADMIN`).

### 4.3 Workspace Invitations [IMPLEMENTED]
- `POST /api/v1/workspaces/{workspaceId}/invitations` — Create pending invitation with single-use cryptographically random token (SHA-256 hash persisted).
- `GET /api/v1/workspaces/{workspaceId}/invitations` — List active pending invitations.
- `POST /api/v1/workspaces/{workspaceId}/invitations/{invitationId}/revoke` — Revoke pending invitation.
- `POST /api/v1/invitations/accept` — Accept invitation and join workspace.

### 4.4 Projects & Scoped Access [IMPLEMENTED]
- `GET /api/v1/workspaces/{workspaceId}/projects` — List projects accessible to caller within workspace.
- `POST /api/v1/workspaces/{workspaceId}/projects` — Create project and automatically provision default environments (`development`, `staging`, `production`).
- `GET /api/v1/workspaces/{workspaceId}/projects/{projectId}` — Get project metadata and environment summaries.
- `PATCH /api/v1/workspaces/{workspaceId}/projects/{projectId}` — Update project name, description, or status (Requires `OWNER` or `ADMIN`).
- `DELETE /api/v1/workspaces/{workspaceId}/projects/{projectId}` — Delete project and cascade to associated environments (Requires `OWNER` or `ADMIN`).
- `GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/members` — List explicit scoped project members.
- `POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/members` — Grant scoped project role to a workspace member.
- `PATCH /api/v1/workspaces/{workspaceId}/projects/{projectId}/members/{userId}` — Update scoped project role.
- `DELETE /api/v1/workspaces/{workspaceId}/projects/{projectId}/members/{userId}` — Revoke scoped project access.

### 4.5 Environments & Scoped Access [IMPLEMENTED]
- `GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments` — List all deployment environments for a project.
- `POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments` — Create custom environment tier (Requires `OWNER` or `ADMIN`).
- `GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}` — Get environment details.
- `PATCH /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}` — Update environment settings or protection tier (Requires `OWNER` or `ADMIN`).
- `DELETE /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}` — Delete environment tier (Requires `OWNER` or `ADMIN`).
- `GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/access` — List scoped environment access grants.
- `POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/access` — Grant scoped environment permission level (`READ`, `WRITE`, `MANAGE`).
- `PATCH /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/access/{userId}` — Update scoped environment permission level.
- `DELETE /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/access/{userId}` — Revoke scoped environment access.

### 4.6 Secret Engine [PLANNED — PHASE 3]
- `GET /api/v1/environments/{envId}/secrets` — List secrets (Values are masked: `••••••••`).
- `POST /api/v1/environments/{envId}/secrets` — Create secret (Encrypts payload).
- `POST /api/v1/secrets/{secretId}/reveal` — Explicit reveal request (Requires `secret.reveal` permission & audits action).
- `POST /api/v1/secrets/{secretId}/rotate` — Trigger manual or provider rotation.
- `POST /api/v1/secrets/{secretId}/rollback` — Roll back to prior version number.
- `GET /api/v1/secrets/{secretId}/versions` — List immutable version history.

---

## 5. Pagination, Sorting & Filtering

Collection endpoints accept standard query parameters:
- `page`: 0-indexed page number (default: `0`).
- `size`: Items per page (default: `20`, maximum: `100`).
- `sort`: Comma-separated sort expressions (e.g. `sort=createdAt,desc`).
- `search`: Filter by project name, tag, or description.
