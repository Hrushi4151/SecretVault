# Domain Event Taxonomy & Redaction Guarantees

## 1. Domain Event Canonical Model

All events inherit from `BaseDomainEvent` and are persisted as JSON in the `event_outbox` table.

```json
{
  "eventId": "a78d0f19-913b-419b-98b7-6a4ec701e85a",
  "eventType": "SECRET_ROTATION_COMPLETED",
  "eventVersion": 1,
  "occurredAt": "2026-10-03T19:20:00Z",
  "recordedAt": "2026-10-03T19:20:01Z",
  "workspaceId": "f28bb3f8-6627-4a0b-9df0-9b81bcf772e4",
  "projectId": "87e382b6-4bdf-4eb9-a868-b716f9f21f7c",
  "environmentId": "1a08465d-0044-4869-90d2-dfc79b9ef9c2",
  "secretId": "e12f9bf2-72ee-449e-8be1-f67ce0ffea6a",
  "actorType": "SYSTEM",
  "actorId": "00000000-0000-0000-0000-000000000000",
  "correlationId": "corr-81920-xyz",
  "causationId": "cause-10293-abc",
  "source": "SecretVault-RotationEngine",
  "severity": "INFO",
  "metadata": {
    "secretKey": "DATABASE_URL",
    "version": 4,
    "strategy": "DUAL_CREDENTIAL",
    "gracePeriodMinutes": 60
  }
}
```

## 2. Event Types by Domain Category

### 2.1 Secret Lifecycle & Versions
- `SECRET_CREATED`
- `SECRET_UPDATED`
- `SECRET_DELETED`
- `SECRET_REVEALED`
- `SECRET_VERSION_CREATED`
- `SECRET_VERSION_ACTIVATED`
- `SECRET_VERSION_REVOKED`
- `SECRET_VERSION_ROLLBACK`
- `SECRET_PROMOTED`
- `SECRET_BRANCH_CREATED`
- `SECRET_BRANCH_MERGED`
- `SECRET_COMPROMISED`

### 2.2 Rotations
- `ROTATION_POLICY_CREATED`, `ROTATION_POLICY_UPDATED`, `ROTATION_POLICY_DISABLED`
- `ROTATION_TRIGGERED`, `ROTATION_STARTED`
- `ROTATION_GENERATING`, `ROTATION_GENERATED`
- `ROTATION_VALIDATING`, `ROTATION_VALIDATED`
- `ROTATION_STAGED`, `ROTATION_ACTIVATED`
- `ROTATION_GRACE_STARTED`
- `ROTATION_REVOKING`, `ROTATION_COMPLETED`
- `ROTATION_FAILED`, `ROTATION_ROLLED_BACK`

### 2.3 Leases & Consumers
- `LEASE_CREATED`, `LEASE_RENEWED`, `LEASE_EXPIRED`, `LEASE_REVOKED`
- `CONSUMER_REGISTERED`, `CONSUMER_HEARTBEAT`, `CONSUMER_STALE`, `CONSUMER_RECOVERED`, `CONSUMER_DISABLED`

### 2.4 Machines & Access
- `MACHINE_CREATED`, `MACHINE_DISABLED`, `MACHINE_SUSPENDED`, `MACHINE_REVOKED`
- `ACCESS_GRANTED`, `ACCESS_REVOKED`, `JIT_REQUESTED`, `JIT_APPROVED`, `JIT_EXPIRED`

### 2.5 Security Findings & Incidents
- `SECURITY_FINDING_CREATED`, `SECURITY_FINDING_RESOLVED`
- `SECURITY_INCIDENT_CREATED`, `SECURITY_INCIDENT_UPDATED`, `SECURITY_INCIDENT_RESOLVED`
- `SECURITY_POLICY_VIOLATION`, `ANOMALY_DETECTED`

## 3. Strict Zero-Plaintext Leakage Invariant

All event metadata undergoes automated redaction before insertion into the transactional outbox:
1. Keys matching sensitive patterns (`*password*`, `*secret*`, `*token*`, `*dek*`, `*plaintext*`, `*apikey*`) are scrubbed or replaced with `[REDACTED]`.
2. Plaintext secret values, raw private keys, symmetric DEKs, and user passwords MUST NEVER appear in any event attribute.
3. Violation of this invariant triggers an automated unit test failure and alerts security operations.
