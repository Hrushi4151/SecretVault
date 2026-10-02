# SecretVault — Security Center & Timeline API Specification

## 1. Executive Overview

The **Security Center** is the centralized governance, risk visualization, and audit telemetry hub within SecretVault. It provides security administrators and engineering leads with real-time visibility into workspace security posture, threat detections, active vulnerabilities, risk breakdown, and a unified audit timeline.

All Security Center endpoints are scoped strictly under `/api/v1/workspaces/{workspaceId}/security/*` and protected by canonical authorization permissions (`SECURITY_VIEW`, `SECURITY_MANAGE`).

---

## 2. Authorization & RBAC Contract

| Action / Endpoint | Required Permission | Workspace Role Default Mapping | Description |
|---|---|---|---|
| View Overview / Posture / Risk | `SECURITY_VIEW` | `OWNER`, `ADMIN`, `DEVELOPER`, `VIEWER` | Read-only inspection of security dashboard metrics and score. |
| View Findings & Timeline | `SECURITY_VIEW` | `OWNER`, `ADMIN`, `DEVELOPER`, `VIEWER` | Browse security finding inventory and unified timeline. |
| Update Finding Status / Assignee | `SECURITY_MANAGE` | `OWNER`, `ADMIN` | Acknowledge, resolve, or assign findings for remediation. |
| Record Security Telemetry Event | `SECURITY_MANAGE` | `OWNER`, `ADMIN` | Ingest external or integration security audit events. |
| Trigger Security Analysis | `SECURITY_MANAGE` | `OWNER`, `ADMIN` | Manually run on-demand workspace rule scan. |

---

## 3. REST API Specification

### 3.1 Executive Overview & Posture

#### `GET /api/v1/workspaces/{workspaceId}/security/overview`
Returns high-level executive dashboard summary metrics, risk score, top findings, and recent timeline snippets.
- **Permission:** `SECURITY_VIEW`
- **Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "workspaceId": "d888c210-1dc5-4a04-9291-f834cb82c767",
    "overallRiskScore": 28,
    "overallRiskLevel": "MEDIUM",
    "openFindingCount": 3,
    "criticalFindingCount": 0,
    "highFindingCount": 1,
    "recentSecurityEvents24h": 42,
    "authorizationDenials24h": 1,
    "accessReviewStatus": "HEALTHY",
    "topFindings": [
      {
        "id": "dafb8e91-5056-4908-a90b-3b04ae8a3840",
        "category": "EXCESSIVE_PRIVILEGE",
        "severity": "HIGH",
        "confidence": "HIGH",
        "status": "OPEN",
        "title": "Unscoped standing administrator privileges detected",
        "safeDescription": "User holds workspace OWNER role without scoping restrictions.",
        "remediationGuidance": "Transition user to scoped project roles or ephemeral JIT elevation.",
        "fingerprint": "a3f5b7...",
        "occurrenceCount": 1,
        "firstObservedAt": "2026-10-02T14:48:31.370Z",
        "lastObservedAt": "2026-10-02T14:48:31.370Z",
        "assigneeUserId": null
      }
    ],
    "recentTimeline": [
      {
        "id": "e4c19b02-39c2-487b-a192-3c129e92d8f1",
        "source": "SECURITY_EVENT",
        "eventType": "JIT_APPROVED",
        "description": "Security Event: JIT_APPROVED (Outcome: SUCCESS)",
        "severity": "MEDIUM",
        "outcome": "SUCCESS",
        "actorUserId": "fa72e900-407e-4a86-964f-3ce595665885",
        "projectId": null,
        "environmentId": null,
        "timestamp": "2026-10-02T14:48:30.000Z",
        "metadata": {
          "justification": "Production deployment debugging"
        }
      }
    ],
    "calculatedAt": "2026-10-02T14:48:32.000Z"
  }
}
```

---

#### `GET /api/v1/workspaces/{workspaceId}/security/posture`
Returns deep, granular posture metrics including dormant privileged users, unused grants, and review status.
- **Permission:** `SECURITY_VIEW`
- **Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "workspaceId": "d888c210-1dc5-4a04-9291-f834cb82c767",
    "overallRiskScore": 28,
    "overallRiskLevel": "MEDIUM",
    "openFindingCount": 3,
    "criticalFindingCount": 0,
    "highFindingCount": 1,
    "mediumFindingCount": 1,
    "lowFindingCount": 1,
    "unresolvedAccessFindings": 2,
    "jitActivity24h": 4,
    "authorizationDenials24h": 1,
    "adminChanges24h": 3,
    "accessReviewStatus": "HEALTHY",
    "privilegedUserCount": 2,
    "unusedGrantCount": 0,
    "dormantPrivilegedUserCount": 0,
    "riskFactors": [
      {
        "factorName": "Active High Severity Findings",
        "scoreContribution": 15,
        "category": "FINDINGS",
        "explanation": "1 high severity finding(s) require immediate remediation"
      }
    ],
    "calculatedAt": "2026-10-02T14:48:32.000Z"
  }
}
```

---

#### `GET /api/v1/workspaces/{workspaceId}/security/risk`
Returns isolated risk assessment engine computation with factor breakdown.
- **Permission:** `SECURITY_VIEW`
- **Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "workspaceId": "d888c210-1dc5-4a04-9291-f834cb82c767",
    "score": 28,
    "level": "MEDIUM",
    "factors": [
      {
        "factorName": "Active High Severity Findings",
        "scoreContribution": 15,
        "category": "FINDINGS",
        "explanation": "1 high severity finding(s) require immediate remediation"
      }
    ]
  }
}
```

---

### 3.2 Unified Security Timeline

#### `GET /api/v1/workspaces/{workspaceId}/security/timeline`
Returns chronological, unified security events, audit actions, and detection signals.
- **Permission:** `SECURITY_VIEW`
- **Query Parameters:**
  - `limit` (Optional, Integer, default: `50`, max: `100`): Maximum timeline items to retrieve.
- **Response (200 OK):** Returns array of `SecurityTimelineEventResponse`.

---

### 3.3 Security Findings Inventory & Management

#### `GET /api/v1/workspaces/{workspaceId}/security/findings`
Lists paginated security findings with rich filtering options.
- **Permission:** `SECURITY_VIEW`
- **Query Parameters:**
  - `status` (`OPEN`, `ACKNOWLEDGED`, `IN_PROGRESS`, `RESOLVED`, `FALSE_POSITIVE`)
  - `severity` (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`)
  - `category` (`EXCESSIVE_PRIVILEGE`, `PRIVILEGE_ESCALATION_PATTERN`, `SUSPICIOUS_JIT_ACTIVITY`, etc.)
  - `projectId` (UUID)
  - `environmentId` (UUID)
  - `search` (String: title / description search)
  - `page` (Integer, default `0`)
  - `size` (Integer, default `20`, max `100`)
  - `sort` (String, default `lastObservedAt,desc`) — Whitelisted sort fields: `id`, `createdAt`, `updatedAt`, `severity`, `status`, `lastObservedAt`, `occurrenceCount`, `category`.

---

#### `PATCH /api/v1/workspaces/{workspaceId}/security/findings/{findingId}/status`
Updates finding lifecycle state.
- **Permission:** `SECURITY_MANAGE`
- **Request Body:**
```json
{
  "status": "RESOLVED",
  "resolutionReason": "Migrated user to scoped project developer role."
}
```
- **Response (200 OK):** Updated `SecurityFindingResponse`.

---

#### `PATCH /api/v1/workspaces/{workspaceId}/security/findings/{findingId}/assign`
Assigns or reassigns remediation owner.
- **Permission:** `SECURITY_MANAGE`
- **Request Body:**
```json
{
  "assigneeUserId": "fa72e900-407e-4a86-964f-3ce595665885"
}
```
- **Response (200 OK):** Updated `SecurityFindingResponse`.

---

### 3.4 Manual Analysis & Ingestion

#### `POST /api/v1/workspaces/{workspaceId}/security/analyze`
Triggers on-demand deterministic rule scan across workspace state.
- **Permission:** `SECURITY_MANAGE`
- **Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "workspaceId": "d888c210-1dc5-4a04-9291-f834cb82c767",
    "rulesExecuted": 10,
    "findingsEvaluated": 3,
    "findingsUpserted": 1,
    "executionTimeMs": 42,
    "executedAt": "2026-10-02T14:48:32.000Z"
  }
}
```

---

#### `POST /api/v1/workspaces/{workspaceId}/security/events`
Records a sanitized security event.
- **Permission:** `SECURITY_MANAGE`
- **Request Body:**
```json
{
  "eventType": "AUTH_LOGIN_FAILURE",
  "severity": "MEDIUM",
  "outcome": "FAILURE",
  "actorUserId": "fa72e900-407e-4a86-964f-3ce595665885",
  "projectId": null,
  "environmentId": null,
  "metadata": {
    "ipAddress": "192.168.1.50",
    "userAgent": "Mozilla/5.0"
  }
}
```
- **Response (201 Created):** Created `SecurityEventResponse`.
