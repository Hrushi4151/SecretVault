# Team Invitation & Role-Based Access Control (RBAC) Architecture

## 1. Executive Summary

SecretVault implements an enterprise-grade, in-app team invitation system coupled with a hierarchical Role-Based Access Control (RBAC) and granular permission evaluation engine. This document specifies the invitation lifecycle, in-app recipient delivery mechanism, real-time user lookup UX, authorization path, transactional invariants, security guarantees, and full RBAC matrix.

---

## 2. Invitation Domain Model & Lifecycle

### 2.1 Domain Schema

Workspace invitations are stored securely in the `workspace_invitations` table with the following attributes:

* `id` (`UUID`): Primary key.
* `workspace_id` (`UUID`): Target workspace.
* `invited_by` (`UUID`): User ID of the inviter.
* `email` (`VARCHAR(255)`): Normalized (lowercase) recipient email address.
* `role` (`VARCHAR(32)`): Assigned workspace standing role (`OWNER`, `ADMIN`, `DEVELOPER`, `VIEWER`).
* `status` (`VARCHAR(32)`): Current state (`PENDING`, `ACCEPTED`, `REVOKED`, `EXPIRED`).
* `token_hash` (`VARCHAR(64)`): SHA-256 hash of the cryptographically generated single-use token. Plaintext tokens are **never** stored in database, logs, or audit records.
* `expires_at` (`TIMESTAMP WITH TIME ZONE`): Expiration timestamp (1 to 30 days).
* `accepted_at` (`TIMESTAMP WITH TIME ZONE`): Nullable timestamp when invitation was accepted.
* `revoked_at` (`TIMESTAMP WITH TIME ZONE`): Nullable timestamp when invitation was revoked.
* `created_at` (`TIMESTAMP WITH TIME ZONE`): Issuance timestamp.

### 2.2 State Lifecycle Machine

```
              ┌─────────────────────────────┐
              │           PENDING           │
              └──────────────┬──────────────┘
                             │
            ┌────────────────┼────────────────┐
            ▼                ▼                ▼
   ┌─────────────────┐ ┌───────────┐ ┌─────────────────┐
   │    ACCEPTED     │ │  REVOKED  │ │     EXPIRED     │
   │ (Member Active) │ │ (Inviter) │ │ (Timeout Clock) │
   └─────────────────┘ └───────────┘ └─────────────────┘
```

* **PENDING**: The invitation is issued, unexpired, and visible in the recipient's in-app notification center.
* **ACCEPTED**: The recipient verified their identity, and a new workspace membership record was atomically created.
* **REVOKED**: An authorized workspace administrator (`OWNER` or `ADMIN`) invalidated the pending invitation.
* **EXPIRED**: The invitation exceeded `expires_at` and cannot be accepted.

---

## 3. In-App Invitation Delivery Architecture

SecretVault delivers invitations **directly within the application interface**, removing any dependency on external SMTP/email delivery infrastructure.

### 3.1 Recipient Discovery Flow

```
1. Administrator issues invitation for target email "bob@company.com"
       ↓
2. Backend creates record with status = PENDING and target email = "bob@company.com"
       ↓
3. User Bob authenticates into SecretVault
       ↓
4. Frontend queries GET /api/v1/invitations/me
       ↓
5. Backend resolves Bob's email from JWT principal and queries:
   WHERE LOWER(email) = LOWER(:principalEmail)
     AND status = 'PENDING'
     AND expires_at > NOW()
       ↓
6. In-App Notification Bell in AppShell lights up with pending badge count "1 New"
       ↓
7. Bob opens "Pending Workspace Invitations" modal, reviews workspace name, inviter, role, and expiration
       ↓
8. Bob clicks "Accept & Join"
       ↓
9. Backend validates, locks invitation row, creates WorkspaceMember, updates status to ACCEPTED
       ↓
10. Workspace list refreshes automatically; Bob immediately gains access with assigned role
```

---

## 4. Real-Time Email User Lookup & Anti-Enumeration UX

### 4.1 Lookup Workflow & Frontend Debouncing

When an administrator inputs an email in the invitation dialog, the frontend debounces requests by **350ms** after input syntax validation (`user@domain.tld`) before issuing a lookup request.

```
Input Change ──► Format Valid? ──► Debounce (350ms) ──► GET /api/v1/workspaces/{id}/invitations/lookup
```

### 4.2 State Handling Matrix

| Directory State | HTTP Response Payload | UI Presentation | Submit Button State |
|---|---|---|---|
| **Existing User Found** | `{"exists": true, "user": {"id": "...", "name": "Alice Doe", "email": "alice@company.com"}, "isMember": false, "hasPendingInvitation": false}` | 🟢 **User found** card with avatar, name, and email. Confirms direct in-app delivery. | **Enabled** ("Send In-App Invitation") |
| **Already a Member** | `{"exists": true, "user": {...}, "isMember": true, "hasPendingInvitation": false}` | ⚠️ **Already a member of this workspace** card. Explains user is enrolled. | **Disabled** (Prevents duplicate memberships) |
| **Pending Invitation Exists** | `{"exists": true, "user": {...}, "isMember": false, "hasPendingInvitation": true}` | ⚠️ **Invitation already pending** card. Directs admin to Pending tab to revoke first. | **Disabled** (Prevents duplicate invitations) |
| **Unknown / Unregistered Email** | `{"exists": false, "user": null, "isMember": false, "hasPendingInvitation": false}` | ℹ️ **No existing account found.** User can register with this email and accept. | **Enabled** ("Send In-App Invitation") |

### 4.3 Anti-Enumeration Protection

* The `/api/v1/workspaces/{workspaceId}/invitations/lookup` endpoint is strictly guarded: **only callers who are active `OWNER` or `ADMIN` members of the specified workspace** can execute lookups.
* Non-members and unauthorized users receive `403 FORBIDDEN`.
* Lookup returns minimal sanitized identity data (`id`, `name`, `email`) and never exposes passwords, organization memberships, or external workspace affiliations.

---

## 5. Security Invariants & Transactional Integrity

### 5.1 Target Identity Verification

To prevent invitation theft or token sharing attacks:
* When accepting an invitation via `/api/v1/invitations/{id}/accept` or `/api/v1/invitations/accept`, the backend compares:
  $$\text{normalized}(\text{invitation}.\text{email}) == \text{normalized}(\text{authenticatedUser}.\text{email})$$
* If an unauthorized user (User C) attempts to accept an invitation issued to User B, the backend rejects with `403 FORBIDDEN` and logs a security warning.

### 5.2 Concurrency & Replay Protection

* Invitation acceptance employs pessimistic write locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)` / `SELECT ... FOR UPDATE`) on the invitation record.
* Transactions are executed with `@Transactional` isolation:
  1. Acquire pessimistic lock on invitation row.
  2. Verify status is `PENDING`.
  3. Verify `expires_at > Instant.now()`.
  4. Verify target email matches authenticated caller.
  5. Verify user is not already a member in target workspace.
  6. Insert new `WorkspaceMember` record.
  7. Set invitation status to `ACCEPTED` and populate `accepted_at`.
  8. Write immutable audit event (`INVITATION_ACCEPTED`).
* Concurrent acceptance attempts safely fail with `400 BAD_REQUEST: Invitation is no longer active`.

### 5.3 Last Owner Protection

* In `WorkspaceService.java`, member removal and role demotion check the remaining count of active `OWNER` principals.
* If a removal or demotion would reduce the number of workspace owners to zero, the request is rejected with `400 BAD_REQUEST: Cannot remove or demote the last OWNER of a workspace`.

### 5.4 Token Security

* Raw invitation tokens are generated using high-entropy `SecureRandom` (32 bytes / Base64URL).
* Only `SHA-256(rawToken)` is stored in the database.
* Tokens are never logged in application logs or stored in audit payloads.

---

## 6. Comprehensive RBAC & Effective Access Matrix

### 6.1 Workspace Role Authority Matrix

| Capability / Action | OWNER | ADMIN | DEVELOPER | VIEWER |
|---|:---:|:---:|:---:|:---:|
| **View Workspace Details** | ✅ | ✅ | ✅ | ✅ |
| **List Workspace Members** | ✅ | ✅ | ✅ | ✅ |
| **Lookup Users for Invitation** | ✅ | ✅ | ❌ | ❌ |
| **Create Workspace Invitations** | ✅ | ✅ | ❌ | ❌ |
| **Assign OWNER Role via Invite** | ✅ | ❌ | ❌ | ❌ |
| **Assign ADMIN / DEV / VIEWER** | ✅ | ✅ | ❌ | ❌ |
| **Revoke Workspace Invitations** | ✅ | ✅ | ❌ | ❌ |
| **Update Member Roles** | ✅ | ✅ *(Except OWNER)* | ❌ | ❌ |
| **Remove Workspace Members** | ✅ | ✅ *(Except OWNER)* | ❌ | ❌ |
| **Leave Workspace (Self-Removal)** | ✅ *(If >1 OWNER)* | ✅ | ✅ | ✅ |
| **Manage Workspace Settings** | ✅ | ❌ | ❌ | ❌ |

### 6.2 Resource Authorization Path & `EffectiveAccessService`

Workspace roles represent standing baseline permissions. Secret access is governed by the unified **`EffectiveAccessService`** evaluation pipeline:

```
                          Standing Workspace Role
                                     │
                     ┌───────────────┴───────────────┐
                     ▼                               ▼
               OWNER / ADMIN                DEVELOPER / VIEWER
                     │                               │
           (Full Workspace Scope)         ┌──────────┴──────────┐
                     │                    ▼                     ▼
                     │             Project Member?      Environment Perms?
                     │                    │                     │
                     └───────────────┬────┴─────────────────────┘
                                     │
                     ┌───────────────┴───────────────┐
                     ▼                               ▼
            Active AccessGrant?              Active JIT Grant?
            (Explicit Grants)            (Time-bound elevation)
                     │                               │
                     └───────────────┬───────────────┘
                                     │
                                     ▼
                            Security Policies
                        (Require MFA / Dual Control)
                                     │
                                     ▼
                           FINAL DECISION (ALLOW / DENY)
```

#### Secret Permission Hierarchy

* **`SECRET_READ`**: Read secret metadata, keys, version history, and tags. Available to `DEVELOPER`, `ADMIN`, `OWNER`.
* **`SECRET_REVEAL`**: Decrypt and view plaintext AES-256-GCM secret payload. Available to `ADMIN`, `OWNER`, or via approved granular grant / JIT session. `VIEWER` is strictly denied.
* **`SECRET_CREATE` / `SECRET_UPDATE`**: Write new secrets or versions. Available to `DEVELOPER`, `ADMIN`, `OWNER`.
* **`SECRET_DELETE`**: Delete secrets or environments. Restricted to `ADMIN`, `OWNER`.
* **`SECRET_BRANCH` / `SECRET_ROLLBACK`**: Branching and rollbacks. Available to `DEVELOPER`, `ADMIN`, `OWNER`.
* **`ENVIRONMENT_PROMOTE`**: Promotion across environments. Governed by environment promotion policies.

---

## 7. API Reference Specification

### 7.1 Invitation Endpoints

#### User Lookup
* **`GET /api/v1/workspaces/{workspaceId}/invitations/lookup?email={email}`**
* **Headers:** `Authorization: Bearer <JWT>`
* **Authorization:** Caller must have `OWNER` or `ADMIN` role in `workspaceId`.
* **Response `200 OK`:**
  ```json
  {
    "exists": true,
    "user": {
      "id": "21fbf503-bece-4a5c-91c8-322ae5d57149",
      "name": "Bob Smith",
      "email": "bob@company.com"
    },
    "isMember": false,
    "hasPendingInvitation": false
  }
  ```

#### Create Workspace Invitation
* **`POST /api/v1/workspaces/{workspaceId}/invitations`**
* **Headers:** `Authorization: Bearer <JWT>`
* **Authorization:** Caller must have `OWNER` or `ADMIN` role.
* **Request Body:**
  ```json
  {
    "email": "bob@company.com",
    "role": "DEVELOPER",
    "expiresInDays": 7
  }
  ```
* **Response `201 CREATED`:**
  ```json
  {
    "id": "13560586-0c38-4784-914a-90c366eb952f",
    "workspaceId": "d73924be-b7b6-43d9-933c-6d595eed148a",
    "email": "bob@company.com",
    "role": "DEVELOPER",
    "status": "PENDING",
    "expiresAt": "2026-10-09T10:00:00Z",
    "rawToken": "sv_inv_..."
  }
  ```

#### In-App Recipient Invitations
* **`GET /api/v1/invitations/me`**
* **Headers:** `Authorization: Bearer <JWT>`
* **Authorization:** Authenticated user.
* **Response `200 OK`:**
  ```json
  {
    "items": [
      {
        "id": "13560586-0c38-4784-914a-90c366eb952f",
        "workspaceId": "d73924be-b7b6-43d9-933c-6d595eed148a",
        "workspaceName": "Acme Core Infrastructure",
        "invitedBy": {
          "id": "a4cb84ac-5518-46d4-ad00-bb480ac8c14b",
          "name": "Alice Administrator"
        },
        "role": "DEVELOPER",
        "status": "PENDING",
        "expiresAt": "2026-10-09T10:00:00Z",
        "createdAt": "2026-10-02T10:00:00Z"
      }
    ]
  }
  ```

#### Accept Invitation by ID (In-App)
* **`POST /api/v1/invitations/{id}/accept`**
* **Headers:** `Authorization: Bearer <JWT>`
* **Response `200 OK`:**
  ```json
  {
    "id": "13560586-0c38-4784-914a-90c366eb952f",
    "workspaceId": "d73924be-b7b6-43d9-933c-6d595eed148a",
    "status": "ACCEPTED",
    "role": "DEVELOPER",
    "acceptedAt": "2026-10-02T10:05:00Z"
  }
  ```

#### Decline Invitation by ID (In-App)
* **`POST /api/v1/invitations/{id}/decline`**
* **Headers:** `Authorization: Bearer <JWT>`
* **Response `200 OK`:**
  ```json
  {
    "id": "13560586-0c38-4784-914a-90c366eb952f",
    "status": "REVOKED"
  }
  ```

#### Revoke Workspace Invitation (Admin)
* **`POST /api/v1/workspaces/{workspaceId}/invitations/{invitationId}/revoke`**
* **Headers:** `Authorization: Bearer <JWT>`
* **Response `200 OK`:**
  ```json
  {
    "id": "13560586-0c38-4784-914a-90c366eb952f",
    "status": "REVOKED",
    "revokedAt": "2026-10-02T10:06:00Z"
  }
  ```

---

## 8. Audit Event Telemetry

All invitation and membership lifecycle actions emit structured, immutable audit log records via `AuditService`:

| Event Action | Resource Type | Target ID | Logged Metadata |
|---|---|---|---|
| `INVITATION_CREATED` | `WORKSPACE_INVITATION` | Invitation ID | `{ "email": "...", "role": "DEVELOPER", "expiresAt": "..." }` |
| `INVITATION_ACCEPTED` | `WORKSPACE_INVITATION` | Invitation ID | `{ "userId": "...", "workspaceId": "...", "role": "DEVELOPER" }` |
| `INVITATION_DECLINED` | `WORKSPACE_INVITATION` | Invitation ID | `{ "declinedBy": "...", "workspaceId": "..." }` |
| `INVITATION_REVOKED` | `WORKSPACE_INVITATION` | Invitation ID | `{ "revokedBy": "...", "workspaceId": "..." }` |
| `MEMBER_ROLE_CHANGED`| `WORKSPACE_MEMBER` | Target User ID | `{ "oldRole": "DEVELOPER", "newRole": "ADMIN" }` |
| `MEMBER_REMOVED` | `WORKSPACE_MEMBER` | Target User ID | `{ "removedBy": "...", "selfRemoval": false }` |

* **Zero-Leak Guarantee:** Audit records **never** contain plaintext invitation tokens, token hashes, secrets, DEKs, or JWT strings.

---

## 9. Test Verification & Coverage Summary

### 9.1 Test Suite Status

The test suite contains 212 automated tests across units, integration flows, concurrency, and security boundaries:

* **Backend Test Suite:** `212 / 212 Passing` (0 failures, 0 errors, 0 skipped).
* **Frontend Production Bundle:** `1639 modules transformed`, clean production build (`dist/assets/index-wGhmg3pp.js`).
* **Test Classes:**
  * `MemberAccessServiceTest`: Unit testing of complete project & environment access matrix calculation, batch updates, cross-workspace hierarchy tampering rejection, and permission checks.
  * `MemberAccessControllerTest`: Full HTTP MockMvc end-to-end multi-project access configuration, persistence verification, live effective permission inspection, and unauthorized developer rejection.
  * `WorkspaceInvitationServiceTest`: Unit testing of lookup states, in-app delivery filtering, target email verification, token hashing, expiration, and conflict management.
  * `WorkspaceInvitationControllerTest`: Full HTTP integration flow covering user lookup $\to$ invitation creation $\to$ target mismatch rejection $\to$ in-app acceptance $\to$ role change $\to$ member removal.
  * `WorkspaceServiceTest`: Unit testing of membership management, role validation, and last-owner protection.
  * `EffectiveAccessServiceTest`: End-to-end authorization engine evaluation across workspace, project, environment, granular grant, and JIT layers.

---

## 10. Known Considerations

1. **Pre-Registration Invitations:** If an email is invited before the user registers a SecretVault account, the invitation is persisted with `status = PENDING`. Once the user registers with the matching email, the pending invitation will automatically appear in their `/api/v1/invitations/me` list.
2. **Session Role Freshness:** Workspace switching and role updates reload the active workspace context in the frontend without requiring the user to log out or log back in.

---

## 11. Per-Member Project → Environment → Permission Access Governance

### 11.1 Architecture & Core Principle

SecretVault enables authorized administrators (`OWNER` and `ADMIN`) to configure fine-grained, per-member access across projects, environments, and granular permissions without mutating the user's global standing workspace role.

```text
Workspace (Standing Role: DEVELOPER)
    ↓
Project A (E-Commerce: VIEWER / READ)
    ├── Development   → READ
    ├── Staging       → READ
    └── Production    → No Access
    ↓
Project B (Payment API: DEVELOPER / WRITE)
    ├── Development   → READ / WRITE
    ├── Staging       → READ / WRITE
    └── Production    → READ
    ↓
Project C (Admin Portal: VIEWER / READ)
    ├── Development   → READ
    ├── Staging       → No Access
    └── Production    → READ
```

### 11.2 Authoritative Decision Engine & Inheritance Invariants

Authorization decisions are computed strictly by `EffectiveAccessService` using the following hierarchy:

1. **Project Effective Role:**
   $$\text{EffectiveProjectRole} = \min(\text{WorkspaceRole}, \text{ProjectRole})$$
   * A member with workspace role `DEVELOPER` assigned `VIEWER` on Project A is effectively a `VIEWER` for Project A.
   * A member with workspace role `VIEWER` can never receive `DEVELOPER` or `ADMIN` effective capabilities on a project.

2. **Environment Effective Permission:**
   $$\text{EffectiveEnvPermission} = \min(\text{EffectiveProjectRole}, \text{EnvGrant})$$
   * `VIEWER` effective project role restricts all environments to at most `READ`.
   * `DEVELOPER` effective project role restricts all environments to at most `WRITE` (cannot receive `MANAGE`).
   * `OWNER` / `ADMIN` workspace role can configure any valid scope.

3. **Granular Grants & Sensitive Actions:**
   * Secret plaintext reveal (`SECRET_REVEAL`) remains independently governed by granular grants or Just-In-Time (JIT) access approvals and is never automatically implied by metadata `READ` access.

### 11.3 Real-World Multi-Project Configuration Example

| Workspace | User | Workspace Role | Project | Project Access | Environment | Environment Permission | Effective Permissions |
|---|---|---|---|---|---|---|---|
| **Acme** | **Rahul Sharma** | `DEVELOPER` | **E-Commerce** | `VIEWER` | Development | `READ` | `secret.read` (✓), `secret.reveal` (✗), `secret.create` (✗) |
| | | | | | Staging | `READ` | `secret.read` (✓), `secret.reveal` (✗), `secret.create` (✗) |
| | | | | | Production | `NONE` | All Denied (✗) |
| | | | **Payment API** | `DEVELOPER` | Development | `WRITE` | `secret.read` (✓), `secret.reveal` (✓), `secret.create` (✓), `secret.update` (✓) |
| | | | | | Staging | `WRITE` | `secret.read` (✓), `secret.reveal` (✓), `secret.create` (✓), `secret.update` (✓) |
| | | | | | Production | `READ` | `secret.read` (✓), `secret.reveal` (✗), `secret.create` (✗) |
| | | | **Admin Portal**| `VIEWER` | Development | `READ` | `secret.read` (✓), `secret.reveal` (✗), `secret.create` (✗) |
| | | | | | Staging | `NONE` | All Denied (✗) |
| | | | | | Production | `READ` | `secret.read` (✓), `secret.reveal` (✗), `secret.create` (✗) |

### 11.4 Backend REST API Endpoints

#### 1. Retrieve Member Access Overview Matrix
* **`GET /api/v1/workspaces/{workspaceId}/members/{userId}/access`**
* **Headers:** `Authorization: Bearer <JWT>`
* **Response `200 OK`:**
  ```json
  {
    "success": true,
    "data": {
      "member": {
        "id": "4896a26f-3dc2-4395-b0eb-c00c82d974d9",
        "email": "rahul@example.com",
        "fullName": "Rahul Sharma",
        "workspaceRole": "DEVELOPER"
      },
      "projects": [
        {
          "projectId": "d329210e-1610-4e33-9be0-7ce8f73252d2",
          "projectName": "E-Commerce",
          "projectSlug": "e-comm",
          "explicitProjectRole": "VIEWER",
          "effectiveProjectRole": "VIEWER",
          "environments": [
            {
              "environmentId": "84cecabf-a8d1-4ad0-b330-4457dcac8363",
              "name": "Development",
              "envType": "DEVELOPMENT",
              "isProtected": false,
              "explicitPermissionLevel": "READ",
              "effectivePermissionLevel": "READ"
            }
          ]
        }
      ],
      "granularGrants": [],
      "activeJitGrants": []
    }
  }
  ```

#### 2. Atomic Batch Update Member Access
* **`PUT /api/v1/workspaces/{workspaceId}/members/{userId}/access`**
* **Headers:** `Authorization: Bearer <JWT>`, `Content-Type: application/json`
* **Request Body:**
  ```json
  {
    "projectConfigs": [
      {
        "projectId": "d329210e-1610-4e33-9be0-7ce8f73252d2",
        "role": "VIEWER",
        "environmentConfigs": [
          { "environmentId": "84cecabf-a8d1-4ad0-b330-4457dcac8363", "permissionLevel": "READ" }
        ]
      }
    ]
  }
  ```

#### 3. Inspect Effective Permissions & Why-Access Lineage
* **`GET /api/v1/workspaces/{workspaceId}/access/effective?userId={userId}&projectId={projectId}&environmentId={envId}`**
* **Headers:** `Authorization: Bearer <JWT>`
* **Response `200 OK`:** Returns array of `EffectiveAccessExplanation` records detailing decision (`ALLOW` / `DENY`), source type (`WORKSPACE_ROLE`, `PROJECT_ACCESS`, `ENVIRONMENT_ACCESS`, `GRANULAR_GRANT`, `JIT_GRANT`), source reference, and reasoning.

### 11.5 Security & Hierarchy Invariants

* **Tenant Isolation:** Rejects any request where the target user, project, or environment does not belong to the specified workspace (`400 BAD_REQUEST` / `404 NOT_FOUND`).
* **Hierarchy Validation:** Rejects any attempt to configure an environment under a project to which it does not belong.
* **Mass-Assignment Protection:** Explicit role and permission inputs are strictly validated against supported enumerations and the caller's authorization rank.
* **Auditability:** Emits immutable `PROJECT_ACCESS_GRANTED`, `PROJECT_ACCESS_REVOKED`, `ENVIRONMENT_ACCESS_GRANTED`, `ENVIRONMENT_ACCESS_REVOKED` audit events containing target and actor principal IDs without leaking sensitive metadata or plaintext.

---

## 12. User Workspace & Project Discovery Architecture & IDOR Protection

### 12.1 Overview & Core Principles

In SecretVault, authenticated users discover only the resources they are authorized to access. Project visibility is computed **server-side** by `EffectiveAccessService` and `ProjectService`. The frontend never fetches unauthorized projects to hide them locally.

```
User Login
    ↓
My Workspaces (GET /api/v1/workspaces)
    ↓
Select Workspace
    ↓
Authorized Projects (GET /api/v1/workspaces/{workspaceId}/projects)
    ↓
Select Project
    ↓
Authorized Environments (GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments)
    ↓
Authorized Secrets / Resources (GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{envId}/secrets)
```

### 12.2 Server-Side Project Visibility Matrix

| Member Classification | Condition | Visible Projects in `GET /projects` | Direct ID Access (`GET /projects/{id}`) |
| :--- | :--- | :--- | :--- |
| **Workspace Admin / Owner** | Standing role is `OWNER` or `ADMIN` | **All projects** within workspace container | **Allowed** (`200 OK`) |
| **Scoped Member** | User has $\ge 1$ scoped `ProjectAccess`, `EnvironmentAccess`, `AccessGrant`, or active `JIT` in workspace | **Only assigned projects** (matching explicit scoped grant, environment access, grant, or JIT) | **Allowed** for assigned projects; **Rejected** with `403 FORBIDDEN` for unassigned projects |
| **Standing Unrestricted Member** | User has standing `DEVELOPER` / `VIEWER` role and 0 scoped records in workspace | **All projects** within workspace container | **Allowed** (`200 OK`) according to standing role permissions |
| **Dynamic JIT Elevation** | User receives approved temporary JIT elevation for an unassigned project/environment | Dynamically appears in project list during validity window | **Allowed** (`200 OK`) during active window; reverts to `403 FORBIDDEN` upon expiry |
| **Granular AccessGrant** | User is granted a specific `AccessGrant` scoped to project or its environment | Dynamically appears in project list | **Allowed** (`200 OK`) |

### 12.3 Real-World Scoping Example

```
Workspace: Acme
User: Rahul (Standing Workspace Role: DEVELOPER)

Configured Projects:
  1. E-Commerce   → Scoped ProjectAccess: VIEWER (READ)       → VISIBLE in project list
  2. Payment API  → Scoped ProjectAccess: DEVELOPER (WRITE)   → VISIBLE in project list
  3. Admin Portal → Unassigned / No Access Record             → EXCLUDED from project list
```

* **`GET /api/v1/workspaces/{acmeId}/projects`**: Returns `[E-Commerce, Payment API]`. `Admin Portal` is omitted server-side.
* **Direct IDOR Attempt**: If Rahul sends `GET /api/v1/workspaces/{acmeId}/projects/{adminPortalId}`, backend rejects with:
  ```json
  {
    "status": 403,
    "code": "FORBIDDEN",
    "message": "You are not authorized to access this project"
  }
  ```
* **Environment / Secret IDOR Attempt**: Direct requests to `GET /api/v1/workspaces/{acmeId}/projects/{adminPortalId}/environments` or `.../secrets` are rejected with `403 FORBIDDEN`.

### 12.4 Invariant & Authorization Architecture Alignment

1. **Single Source of Truth:** Reuses `EffectiveAccessService`, `ProjectAccess`, `EnvironmentAccess`, `AccessGrant`, and `JIT` data structures without creating a parallel or duplicate authorization engine.
2. **IDOR Invariant:** Every hierarchical lookup (`ProjectService.getProjectById`, `EnvironmentService.verifyHierarchyAccess`, `SecretService.verifyHierarchy`) asserts `EffectiveAccessService.isUserAuthorizedForProject` before returning metadata or resources.
3. **Audit Defense:** Rejections trigger secure error responses without exposing metadata regarding unassigned internal systems.

