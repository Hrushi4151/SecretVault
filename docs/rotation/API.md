# REST API Specification: Phase 12 Endpoints

All endpoints are authenticated with Bearer tokens or Session cookies and authorized via `EffectiveAccessService`.

---

## 1. Rotation Policy Endpoints

### Create Policy
`POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/secrets/{secretId}/rotation-policy`
- Permission: `SECRET_ROTATION_CREATE`
- Request: `CreateRotationPolicyRequest`
- Response: `RotationPolicyResponse`

### Get Policy
`GET /api/v1/workspaces/{workspaceId}/rotation-policies/{policyId}`
- Permission: `SECRET_ROTATION_READ`

### Get Policy by Secret
`GET /api/v1/workspaces/{workspaceId}/secrets/{secretId}/rotation-policy`
- Permission: `SECRET_ROTATION_READ`

### List Policies in Workspace
`GET /api/v1/workspaces/{workspaceId}/rotation-policies`
- Permission: `SECRET_ROTATION_READ`

### Update Policy
`PUT /api/v1/workspaces/{workspaceId}/rotation-policies/{policyId}`
- Permission: `SECRET_ROTATION_POLICY_MANAGE`

### Delete Policy
`DELETE /api/v1/workspaces/{workspaceId}/rotation-policies/{policyId}`
- Permission: `SECRET_ROTATION_POLICY_MANAGE`

---

## 2. Rotation Job Endpoints

### Trigger Rotation
`POST /api/v1/workspaces/{workspaceId}/secrets/{secretId}/rotations`
- Permission: `SECRET_ROTATION_CREATE` (or `SECRET_ROTATION_EMERGENCY`)
- Request: `TriggerRotationRequest`
- Response: `RotationJobResponse`

### Get Job Details
`GET /api/v1/workspaces/{workspaceId}/rotation-jobs/{jobId}`
- Permission: `SECRET_ROTATION_READ`

### List Jobs
`GET /api/v1/workspaces/{workspaceId}/rotation-jobs?secretId={secretId}&page=0&size=20`
- Permission: `SECRET_ROTATION_READ`

### Cancel Job
`POST /api/v1/workspaces/{workspaceId}/rotation-jobs/{jobId}/cancel`
- Permission: `SECRET_ROTATION_CANCEL`

### Rollback Job
`POST /api/v1/workspaces/{workspaceId}/rotation-jobs/{jobId}/rollback`
- Permission: `SECRET_ROTATION_ROLLBACK`

### Mark Secret Compromised
`POST /api/v1/workspaces/{workspaceId}/secrets/{secretId}/compromised`
- Permission: `SECRET_ROTATION_EMERGENCY`
- Request: `MarkCompromisedRequest`

---

## 3. Impact Analysis Endpoint

### Compute Impact
`GET /api/v1/workspaces/{workspaceId}/secrets/{secretId}/rotation-impact`
- Permission: `SECRET_ROTATION_READ`
- Response: `RotationImpactResponse`

---

## 4. Secret Lease Endpoints

### Create Lease
`POST /api/v1/workspaces/{workspaceId}/leases`
- Permission: `SECRET_READ`
- Request: `CreateSecretLeaseRequest`
- Response: `SecretLeaseResponse`

### Get Lease
`GET /api/v1/workspaces/{workspaceId}/leases/{leaseId}`
- Permission: `SECRET_LEASE_READ`

### List Leases
`GET /api/v1/workspaces/{workspaceId}/leases?secretId={secretId}&status={status}&page=0&size=20`
- Permission: `SECRET_LEASE_READ`

### Renew Lease
`POST /api/v1/workspaces/{workspaceId}/leases/{leaseId}/renew`
- Permission: `SECRET_READ`
- Request: `RenewSecretLeaseRequest`
- Response: `SecretLeaseResponse`

### Revoke Lease
`DELETE /api/v1/workspaces/{workspaceId}/leases/{leaseId}`
- Permission: `SECRET_LEASE_MANAGE`

---

## 5. Consumer Registry Endpoints

### Register Consumer
`POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/consumers`
- Permission: `CONSUMER_MANAGE`
- Request: `RegisterSecretConsumerRequest`
- Response: `SecretConsumerResponse`

### Consumer Heartbeat
`POST /api/v1/workspaces/{workspaceId}/consumers/{consumerId}/heartbeat`
- Request: `ConsumerHeartbeatRequest`
- Response: `SecretConsumerResponse`

### Get Consumer
`GET /api/v1/workspaces/{workspaceId}/consumers/{consumerId}`
- Permission: `SECRET_READ`

### List Consumers
`GET /api/v1/workspaces/{workspaceId}/consumers?page=0&size=20`
- Permission: `SECRET_READ`

### Disable Consumer
`POST /api/v1/workspaces/{workspaceId}/consumers/{consumerId}/disable`
- Permission: `CONSUMER_MANAGE`
