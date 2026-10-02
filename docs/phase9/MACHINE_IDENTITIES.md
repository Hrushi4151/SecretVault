# Phase 9: Machine Identities Lifecycle & Management

## Concept & Purpose

A **Machine Identity** in SecretVault represents a non-human workload, such as a CI/CD job runner, a Kubernetes daemon, a scheduled script, or an external cloud microservice.

Unlike human user accounts (which authenticate via username/password, OAuth2, and MFA), Machine Identities authenticate exclusively through **Cryptographic Workload Identity Federation (OIDC)** and possess explicit, fine-grained access grants.

---

## Machine Identity Attributes

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the machine identity. |
| `workspaceId` | UUID | Workspace to which this machine identity belongs. |
| `name` | String | Unique, slug-friendly or descriptive name (e.g. `github-rally-ci`). |
| `description` | String | Optional human-readable description of the workload's purpose. |
| `type` | Enum | `CI_CD`, `KUBERNETES`, `SERVICE`, `CUSTOM`. |
| `status` | Enum | `ACTIVE`, `DISABLED`, `REVOKED`. |
| `expiresAt` | Timestamp | Optional expiration date. Once reached, token exchanges are blocked. |
| `createdAt` | Timestamp | Timestamp when the machine identity was created. |
| `updatedAt` | Timestamp | Timestamp of last modification. |
| `createdBy` | UUID | User ID of the administrator who created the machine identity. |

---

## Lifecycle States

```
                 +-----------+
                 |  CREATED  |
                 +-----------+
                       |
                       v
                 +-----------+   Disable    +------------+
                 |  ACTIVE   | <----------> |  DISABLED  |
                 +-----------+    Enable    +------------+
                   |       |                      |
      Expires (TTL)|       | Revoke               | Revoke
                   v       v                      v
             +-----------+ +------------------------+
             |  EXPIRED  | |        REVOKED         |
             +-----------+ +------------------------+
```

1. **ACTIVE**: The machine identity can perform token exchanges (subject to valid OIDC trust policies) and access secrets according to its grants.
2. **DISABLED**: Temporarily suspended by an administrator. Existing sessions and new token exchanges are rejected immediately with `401 Unauthorized` / `MACHINE_DISABLED`.
3. **EXPIRED**: Reached its configured `expiresAt` lifecycle date. Token exchange rejected with `MACHINE_EXPIRED`.
4. **REVOKED**: Permanently terminated. All active sessions are purged and cannot be re-activated.
5. **DELETED**: Soft-deleted from the workspace.

---

## Machine Access Grants

Machines do **not** inherit workspace administrator or owner roles automatically. Access is granted explicitly via `MachineAccessGrant` at one of four granular scopes:

- **WORKSPACE Scope**: Grants access across all projects, environments, and secrets in the workspace.
- **PROJECT Scope**: Scopes permissions strictly to secrets within a designated Project.
- **ENVIRONMENT Scope**: Scopes permissions strictly to secrets within a designated Environment (e.g. `Development`, `Staging`, `Production`).
- **SECRET Scope**: Scopes permissions strictly to a specific individual Secret.

### Supported Permissions
- `READ_SECRET`: Read secret metadata and key names.
- `REVEAL_SECRET`: Decrypt envelope and reveal the plaintext secret value.
- `WRITE_SECRET`: Create or update secret versions.
- `DELETE_SECRET`: Delete or soft-delete secrets.
- `LIST_SECRETS`: List secrets within the scope.
- `ADMIN`: Grant management rights within the designated scope.

---

## Audit & Governance Integration

Every lifecycle mutation generates a structured audit log entry in `audit_events`:
- `MACHINE_IDENTITY_CREATE`
- `MACHINE_IDENTITY_UPDATE`
- `MACHINE_IDENTITY_DISABLE`
- `MACHINE_IDENTITY_ENABLE`
- `MACHINE_IDENTITY_EXPIRE`
- `MACHINE_IDENTITY_REVOKE`
- `MACHINE_IDENTITY_DELETE`
- `MACHINE_GRANT_ADD`
- `MACHINE_GRANT_REMOVE`

Access reviews and Security Center actively monitor machine identities for excessive permissions, stale inactive tokens, and expired lifecycle thresholds.
