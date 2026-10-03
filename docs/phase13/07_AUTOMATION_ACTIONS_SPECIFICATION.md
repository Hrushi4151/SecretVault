# Automation Actions Specification

## 1. Supported Action Types

Automation policies execute actions defined in `actionsJson`. The following actions are supported:

| Action Type | Blast Radius | Requires Approval by Default? | Description |
| :--- | :--- | :--- | :--- |
| `NOTIFY` | Low | No | Dispatches in-app, webhook, or email notification to team. |
| `ADD_AUDIT_EVENT` | Low | No | Appends explicit security finding note to immutable audit ledger. |
| `CREATE_SECURITY_FINDING` | Low | No | Registers finding in Security Center posture dashboard. |
| `CREATE_SECURITY_INCIDENT` | Medium | No | Declares formal security incident and begins triage lifecycle. |
| `TRIGGER_ROTATION` | Medium | No | Requests immediate secret rotation via `RotationService`. |
| `REVOKE_LEASE` | High | Yes (Configurable) | Forcefully revokes dynamic secret lease and drops credentials. |
| `DISABLE_CONSUMER` | High | Yes (Configurable) | Marks workload consumer disabled to prevent secret access. |
| `SUSPEND_MACHINE` | High | Yes (Configurable) | Temporarily suspends machine identity tokens and mTLS sessions. |
| `REVOKE_MACHINE` | Critical | Yes | Permanently revokes machine identity and credentials. |
| `CREATE_WEBHOOK_DELIVERY`| Medium | No | Forwards payload to third-party webhook URL. |

## 2. Parameter Schema & Examples

### 2.1 Trigger Rotation Action
```json
{
  "type": "TRIGGER_ROTATION",
  "parameters": {
    "secretId": "e12f9bf2-72ee-449e-8be1-f67ce0ffea6a",
    "reason": "Automated rotation triggered due to detected drift"
  }
}
```

### 2.2 Revoke Lease Action
```json
{
  "type": "REVOKE_LEASE",
  "parameters": {
    "leaseId": "48b6f3c1-bfa0-482a-a92c-569269bb24e1",
    "cascadeRevocation": true
  }
}
```

### 2.3 Declare Security Incident Action
```json
{
  "type": "CREATE_SECURITY_INCIDENT",
  "parameters": {
    "title": "Suspected Credential Compromise",
    "severity": "CRITICAL",
    "category": "COMPROMISE"
  }
}
```
