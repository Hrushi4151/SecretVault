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
  "timestamp": "2026-09-16T18:00:00.000Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Validation failed for one or more fields",
  "requestId": "4fa81566-0749-410a-8dc4-b77873ad9be0",
  "errors": [
    {
      "field": "name",
      "message": "Secret name must be between 1 and 255 characters"
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

### 4.2 Authentication & Workspace [PLANNED]
- `POST /api/v1/auth/register` — Create user account & tenant organization.
- `POST /api/v1/auth/login` — Authenticate and receive JWT + refresh token.
- `POST /api/v1/auth/mfa/verify` — Verify TOTP / hardware key during step-up auth.
- `GET /api/v1/workspaces` — List workspaces for the authenticated user.

### 4.3 Projects & Environments [PLANNED]
- `GET /api/v1/projects` — List projects within active workspace.
- `POST /api/v1/projects` — Create a new project.
- `GET /api/v1/projects/{projectId}/environments` — List environments (Dev, Staging, Prod).

### 4.4 Secret Engine [PLANNED]
- `GET /api/v1/environments/{envId}/secrets` — List secrets (Values are masked: `••••••••`).
- `POST /api/v1/environments/{envId}/secrets` — Create secret (Encrypts payload).
- `POST /api/v1/secrets/{secretId}/reveal` — Explicit reveal request (Requires `secret.reveal` permission & audits action).
- `POST /api/v1/secrets/{secretId}/rotate` — Trigger manual or provider rotation.
- `POST /api/v1/secrets/{secretId}/rollback` — Roll back to prior version number.
- `GET /api/v1/secrets/{secretId}/versions` — List immutable version history.

### 4.5 Synchronization & Integrations [PLANNED]
- `POST /api/v1/sync/environments/{envId}` — Trigger asynchronous provider sync job.
- `GET /api/v1/sync/jobs/{jobId}` — Inspect async sync job status and logs.
- `GET /api/v1/sync/drift/environments/{envId}` — Evaluate drift against provider state.

---

## 5. Pagination, Sorting & Filtering

Collection endpoints accept standard query parameters:
- `page`: 0-indexed page number (default: `0`).
- `size`: Items per page (default: `20`, maximum: `100`).
- `sort`: Comma-separated sort expressions (e.g. `sort=createdAt,desc`).
- `search`: Filter by secret name, tag, or description.
