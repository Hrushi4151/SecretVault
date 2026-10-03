# Phase 12: Repositories & Connection Management

## 1. Overview

SecretVault manages repositories within the workspace boundary, tracking connection configurations, credentials, synchronization status, and historical scan telemetry.

---

## 2. Repository Entity Model (`RepositoryEntity`)

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Primary key |
| `workspaceId` | UUID | Owning workspace ID |
| `name` | String | Repository name (e.g., `secretvault-backend`) |
| `owner` | String | Organization or user namespace (e.g., `acme-corp`) |
| `vcsProvider` | `VcsProvider` | `GITHUB`, `GITLAB`, `BITBUCKET`, `AZURE_DEVOPS`, `LOCAL` |
| `cloneUrl` | String | HTTPS or SSH Git clone URL |
| `defaultBranch` | String | Default trunk branch (e.g., `main`, `master`) |
| `visibility` | `RepositoryVisibility`| `PUBLIC`, `PRIVATE`, `INTERNAL` |
| `authType` | `AuthType` | `NONE`, `PERSONAL_ACCESS_TOKEN`, `SSH_KEY`, `APP_INSTALLATION` |
| `credentialSecretId` | UUID | Reference to SecretVault-managed credential for cloning |
| `autoScanEnabled` | Boolean | Whether automated scans run on push/PR events |
| `lastScannedAt` | Instant | Timestamp of the most recent scan |
| `riskScore` | Double | Computed aggregate repository risk score (0.0 – 100.0) |

---

## 3. Supported VCS Providers

1. **GitHub**:
   - Authentication via GitHub App installation or fine-grained PAT.
   - Webhook integration for `push` and `pull_request` events.
2. **GitLab**:
   - Authentication via Project or Group Access Tokens.
   - GitLab CI pipeline integration.
3. **Local Workspace**:
   - Scanned via CLI `secretvault scan` without remote credentials.
4. **Generic Git**:
   - Any standard HTTPS or SSH Git server.

---

## 4. Lifecycle Operations

- **Connect**: Register a repository with provider credentials and initial policy assignment.
- **Scan**: Initiate ad-hoc or scheduled scan job.
- **Policy Enforcement**: Apply custom detection rules, exclusion paths, and minimum entropy thresholds.
- **Archive Upload**: Ingest and scan standalone `.zip` archives directly via REST API or CLI.
