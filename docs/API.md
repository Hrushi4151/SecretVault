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

### 4.6 Secret Management Engine & Envelope Encryption [IMPLEMENTED]

All secret endpoints operate under strict hierarchical scoping: `/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/secrets`. Standard endpoints return metadata ONLY. Plaintext values are never returned unless explicitly called via the reveal endpoint.

- `POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets` — Create a new secret.
  - **Request Body:**
    ```json
    {
      "name": "DATABASE_URL",
      "value": "postgresql://usr:pwd@db.internal:5432/app",
      "description": "Primary PostgreSQL connection string"
    }
    ```
  - **Response (201 Created):** `SecretMetadataResponse` (Version 1, zero plaintext returned).
  - **Headers:** `Location: /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}`

- `GET /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets` — List secrets metadata for an environment.
  - **Query Params:** `status` (`ACTIVE` | `DISABLED` | `DELETED`), `search` (case-insensitive substring filter), `page`, `size`, `sort`.
  - **Response (200 OK):** Page of `SecretMetadataResponse` objects with version counters and timestamps.

- `GET /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}` — Get metadata for a single secret.
  - **Response (200 OK):** `SecretMetadataResponse` (name, status, description, currentVersionNumber, created/updated timestamps).

- `PATCH /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}` — Update secret metadata or rotate value.
  - **Request Body:**
    ```json
    {
      "value": "postgresql://usr:new_pwd@db.internal:5432/app",
      "description": "Updated database credentials",
      "reason": "Quarterly credential rotation",
      "status": "ACTIVE"
    }
    ```
  - **Behavior:** If `value` is present, creates an immutable new version row ($v_{N+1}$), advances `current_version_number`, and preserves historical versions. If only `description`/`status` are provided, modifies metadata in place without incrementing version.

- `DELETE /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}` — Soft-delete secret.
  - **Behavior:** Transitions status to `DELETED`. Blocks subsequent reveals and updates while preserving cryptographic audit history.
  - **Response (204 No Content)**

- `POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/reveal` — Explicit plaintext reveal.
  - **Permissions:** Requires effective `READ` or `WRITE` access.
  - **Response Headers:** `Cache-Control: no-store, no-cache, must-revalidate, private`, `Pragma: no-cache`.
  - **Response (200 OK):**
    ```json
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "name": "DATABASE_URL",
      "versionNumber": 2,
      "value": "postgresql://usr:new_pwd@db.internal:5432/app",
      "revealedAt": "2026-09-17T19:30:00Z"
    }
    ```
  - **Audit:** Records an append-only `SECRET_REVEALED` event in `audit_logs`.

- `POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/batch-import` — Bulk `.env` format import.
  - **Request Body:**
    ```json
    {
      "envContent": "API_KEY=sk_live_12345\nJWT_SECRET=supersecret\nREDIS_HOST=10.0.0.5",
      "overwrite": true
    }
    ```
  - **Response (200 OK):** `BatchImportResponse` detailing imported, updated, skipped, and failed keys.

### 4.6 Secret Versions, Diffs, Tags & Rollback [IMPLEMENTED]

- `GET /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/versions` — Paginated version history.
  - **Query Params:** `page`, `size`, `versionType` (`INITIAL`, `VALUE_UPDATE`, `ROLLBACK`, `PROMOTION`, `BRANCH_COMMIT`, `MERGE`).
  - **Response (200 OK):** `Page<SecretVersionResponse>` with metadata, tags, and `isCurrent` boolean.

- `GET /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/versions/{versionNumber}` — Version metadata.

- `POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/versions/{versionNumber}/reveal` — Explicit historical reveal.
  - **Response Headers:** `Cache-Control: no-store, no-cache, must-revalidate, private`.
  - **Audit:** Emits `SECRET_HISTORICAL_REVEALED` event.

- `GET /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/versions/compare?from=1&to=2` — Metadata comparison & Shannon entropy analysis.

- `GET /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/versions/diff?from=1&to=2` — Secure value line-by-line diff.
  - **Protection:** Enforces 64KB DoS ceiling (`PAYLOAD_TOO_LARGE` / 413) and zeroization.

- `POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/rollback` — Rollback as new version ($v_N \to v_{N+1}$).
  - **Request Body:**
    ```json
    {
      "targetVersion": 1,
      "expectedCurrentVersion": 3,
      "reason": "Rollback following latency regression"
    }
    ```
  - **Response (201 Created):** New `SecretVersionResponse` with `versionType: "ROLLBACK"`.

- `POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/versions/{vNum}/tags` — Add tag.
- `DELETE /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/versions/{vNum}/tags/{name}` — Delete tag.

### 4.7 Secret Feature Branching & 3-Way Merge [IMPLEMENTED]

- `POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/branches` — Create feature branch.
  - **Request Body:** `{ "name": "feature/auth-v2", "fromVersion": 2, "description": "Auth revamp" }`
- `GET /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/branches` — List secret branches (including `main`).
- `POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/branches/{bId}/versions` — Commit to branch (main trunk remains isolated).
- `GET /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/branches/{bId}/compare` — Compare branch with main.
- `POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{secretId}/branches/{bId}/merge` — 3-Way merge into main trunk with conflict detection (`409 Conflict`).

### 4.8 Cross-Environment Secret Promotion [IMPLEMENTED]

- `POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{sourceEnvId}/promote/preview` — Dry-run preview.
  - **Request Body:** `{ "destinationEnvironmentId": "...", "secretNames": ["DATABASE_URL"] }`
  - **Response (200 OK):** `PromotionPreviewResponse` with `ADDED`, `MODIFIED`, `UNCHANGED`, and `BLOCKED_DISABLED` counts.
- `POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{sourceEnvId}/promote` — Execute atomic promotion with fresh destination encryption keys and lineage binding.

---

## 5. Pagination, Sorting & Filtering

Collection endpoints accept standard query parameters:
- `page`: 0-indexed page number (default: `0`).
- `size`: Items per page (default: `20`, maximum: `100`).
- `sort`: Comma-separated sort expressions (e.g. `sort=createdAt,desc`).
- `search`: Filter by project name, tag, or description.
