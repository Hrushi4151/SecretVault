# Rotation Policies & Scheduling

## 1. Policy Model

A `RotationPolicy` defines automated governance, schedule, validation strategy, and rollout mechanics for a secret:

```json
{
  "secretId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "enabled": true,
  "strategy": "SCHEDULED",
  "secretType": "PASSWORD",
  "intervalSeconds": 2592000,
  "minIntervalSeconds": 3600,
  "maxSecretAgeSeconds": 7776000,
  "rotationWindowSeconds": 86400,
  "cronExpression": "0 2 * * *",
  "timezone": "UTC",
  "maxRetries": 3,
  "retryBackoffSeconds": 300,
  "validationType": "AUTHENTICATION",
  "rolloutStrategy": "STAGED",
  "gracePeriodSeconds": 1800,
  "autoRevokePrevious": true,
  "autoRollbackOnFailure": true,
  "requireApproval": false,
  "requireJitApproval": false,
  "secretGeneratorConfig": "{\"length\": 32, \"includeSymbols\": true, \"includeDigits\": true}"
}
```

---

## 2. Scheduling Options

1. **Interval-Based**: Configured via `intervalSeconds` (e.g., 30 days = 2,592,000s). The scheduler calculates `nextRotationDueAt = now + intervalSeconds`.
2. **Cron-Based**: Configured via `cronExpression` (e.g. `0 3 1 * *` for 3:00 AM on the 1st of every month in the target `timezone`).
3. **Emergency / Manual**: Can be triggered ad-hoc via CLI or REST API at any time regardless of the schedule.

---

## 3. Background Scheduler Engine

The `RotationSchedulerService` executes periodically (default every 60 seconds):
- Scans `rotation_policies` where `enabled = true` and `next_rotation_due_at <= now()`.
- Dispatches automated rotation jobs for eligible secrets.
- Enforces minimum interval constraints (`minIntervalSeconds`) to prevent accidental rotation loops.
- Automatically handles retries and backoff on transient network or external API failures.
