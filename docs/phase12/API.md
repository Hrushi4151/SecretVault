# Phase 12: REST API Specification

## 1. Overview

The Repository Security REST API provides programmatically accessible endpoints for repository registration, scan initiation, finding retrieval, "Why Exposed" explainability, and remediation execution.

All endpoints require HTTP Bearer token authentication and validate workspace permissions.

---

## 2. API Endpoints

### Repository Management
- `GET /api/v1/workspaces/{wsId}/repositories`: List connected repositories.
- `POST /api/v1/workspaces/{wsId}/repositories`: Connect a new repository.
- `GET /api/v1/workspaces/{wsId}/repositories/{id}`: Get repository details.
- `DELETE /api/v1/workspaces/{wsId}/repositories/{id}`: Disconnect repository.

### Scans
- `POST /api/v1/workspaces/{wsId}/repositories/{repoId}/scans`: Trigger repository scan.
- `POST /api/v1/workspaces/{wsId}/repositories/scan-archive`: Upload and scan `.zip` archive.
- `GET /api/v1/workspaces/{wsId}/repositories/scans/{scanId}`: Get scan status and summary.
- `GET /api/v1/workspaces/{wsId}/repositories/{repoId}/scans`: List scan history for repository.

### Findings & Triage
- `GET /api/v1/workspaces/{wsId}/findings`: Query findings (supports filters: `repositoryId`, `status`, `severity`, `secretType`, `page`, `size`).
- `GET /api/v1/workspaces/{wsId}/findings/{id}`: Get finding details.
- `GET /api/v1/workspaces/{wsId}/findings/{id}/why-exposed`: Get explainability breakdown.
- `PUT /api/v1/workspaces/{wsId}/findings/{id}/status`: Update status (`CONFIRMED`, `FALSE_POSITIVE`, `IGNORED`).
- `POST /api/v1/workspaces/{wsId}/findings/allowlist`: Add finding allowlist entry.

### Remediation
- `POST /api/v1/workspaces/{wsId}/findings/{id}/remediate`: Trigger finding remediation action.
- `GET /api/v1/workspaces/{wsId}/findings/{id}/remediation-jobs`: Get remediation history.

### Policies
- `GET /api/v1/workspaces/{wsId}/repositories/policies`: Get workspace repository policy.
- `PUT /api/v1/workspaces/{wsId}/repositories/policies`: Update policy rules and thresholds.
