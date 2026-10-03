# Phase 13 REST API Specification

## 1. Base URL & Authentication

All API endpoints are mounted under `/api/v1/workspaces/{workspaceId}` and require a valid Bearer token (`Authorization: Bearer <JWT>`).

## 2. Endpoints Summary

### 2.1 Domain Events & Outbox
- `GET /events`: List paginated domain events with optional `eventType` and aggregate filters.
- `GET /events/{eventId}`: Get single event by UUID with redacted payload.
- `POST /event-replays`: Request replay of domain events with side-effect execution flags.
- `GET /event-replays`: List past replay requests.

### 2.2 Automation Policies & Approvals
- `POST /automation-policies`: Create new automation policy.
- `GET /automation-policies`: List workspace automation policies.
- `GET /automation-policies/{policyId}`: Get policy details and DSL conditions.
- `PUT /automation-policies/{policyId}`: Update policy configuration.
- `POST /automation-policies/{policyId}/enable`: Enable policy.
- `POST /automation-policies/{policyId}/disable`: Disable policy.
- `DELETE /automation-policies/{policyId}`: Delete policy.
- `POST /automation-policies/simulate`: Dry-run simulation against sample event.
- `GET /automation-approvals`: List pending or decided approvals.
- `POST /automation-approvals/{approvalId}/decide`: Approve or reject pending automation action.
- `GET /automation-executions`: List execution audit history.

### 2.3 Webhooks
- `POST /webhooks`: Register new webhook endpoint with SSRF validation.
- `GET /webhooks`: List webhook endpoints.
- `GET /webhooks/{webhookId}`: Get endpoint details.
- `PUT /webhooks/{webhookId}`: Update endpoint URL or subscribed events.
- `DELETE /webhooks/{webhookId}`: Delete webhook endpoint.
- `GET /webhook-deliveries`: List delivery logs and HTTP response statuses.
- `POST /webhook-deliveries/{deliveryId}/replay`: Manually replay delivery.

### 2.4 Security Incidents
- `POST /incidents`: Declare a new security incident.
- `GET /incidents`: List incidents with status and severity filters.
- `GET /incidents/{incidentId}`: Get incident details.
- `PUT /incidents/{incidentId}/status`: Update incident lifecycle status.
- `GET /incidents/{incidentId}/events`: Get correlated domain events for an incident.

### 2.5 Notifications
- `GET /notifications`: List in-app notifications.
- `POST /notifications/{notificationId}/read`: Mark notification as read.
- `POST /notifications/{notificationId}/acknowledge`: Acknowledge notification.
- `POST /notifications/read-all`: Mark all notifications as read.
- `GET /notification-preferences`: Get user/workspace preferences.
- `PUT /notification-preferences`: Update notification preferences.

### 2.6 Secret Health
- `GET /health/secrets/{secretId}`: Get single secret health score and breakdown factors.
- `GET /health/secrets`: Get workspace-wide secret health summary and at-risk secrets.
