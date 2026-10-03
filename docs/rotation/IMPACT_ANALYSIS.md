# Blast Radius & Rotation Impact Analysis

## 1. Real-Time Impact Computation

Before triggering a secret rotation or marking a secret as compromised, operators can run a live **Blast Radius Impact Analysis** via `RotationImpactService`:

```bash
GET /api/v1/workspaces/{workspaceId}/secrets/{secretId}/rotation-impact
```

### Response Model
```json
{
  "secretId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "secretName": "PAYMENT_GATEWAY_KEY",
  "currentActiveVersion": 2,
  "totalAffectedConsumers": 4,
  "dynamicRefreshCount": 3,
  "restartRequiredCount": 1,
  "activeLeasesCount": 12,
  "affectedEnvironments": ["production", "staging"],
  "consumers": [
    {
      "consumerId": "7a35368a-e99d-42bc-9d33-1498fa7c07b6",
      "name": "checkout-service",
      "type": "APPLICATION",
      "environmentName": "production",
      "currentVersion": 2,
      "dynamicRefreshCapable": true,
      "requiresRestart": false,
      "status": "ACTIVE",
      "lastSeenAt": "2026-10-03T14:30:00Z"
    },
    {
      "consumerId": "8b55368a-e99d-42bc-9d33-1498fa7c07b7",
      "name": "legacy-invoice-sync",
      "type": "CLI_PROCESS",
      "environmentName": "production",
      "currentVersion": 1,
      "dynamicRefreshCapable": false,
      "requiresRestart": true,
      "status": "STALE",
      "lastSeenAt": "2026-10-01T12:00:00Z"
    }
  ],
  "providerIntegrations": [
    "Provider Mapping: AWS Secrets Manager (prod-us-east-1)"
  ],
  "computedAt": "2026-10-03T14:35:00Z"
}
```

---

## 2. Benefits for DevOps & SecOps

1. **Pre-Flight Visibility**: Prevents unexpected production outages by highlighting services that require manual pod restarts.
2. **Blast Radius Scoping**: Outlines exactly which environments and cloud provider mappings are affected.
3. **Lease Invalidation Preview**: Shows how many active runtime tokens will be immediately invalidated during an emergency compromise workflow.
