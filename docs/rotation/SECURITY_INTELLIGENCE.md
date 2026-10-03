# Security Intelligence Rules: Rotation & Lease Hygiene

## 1. Overview

Phase 12 integrates two primary rules into the **Security Center** intelligence engine:

1. **`RotationRiskRule`** (`SECRET_ROTATION_RISK`)
2. **`SecretLeaseRiskRule`** (`SECRET_LEASE_RISK`)

---

## 2. Rotation Risk Detection (`RotationRiskRule`)

- **Overdue Secret Rotation**: Detects secrets where `next_rotation_due_at` has passed without a successful rotation job completion.
- **Disabled Production Policy**: Flags production secrets that have disabled or missing automated rotation policies.
- **Failed Rotation Jobs**: Alerts security teams immediately when a rotation job enters `FAILED` or `VALIDATION_FAILED` status.

---

## 3. Lease & Consumer Risk Detection (`SecretLeaseRiskRule`)

- **Active Leases on Inactive Machine Identities**: Detects orphaned runtime tokens still active after a machine identity has been suspended or deleted.
- **Excessively Long Lease Lifetime**: Flags any lease configured with `maxLifetimeSeconds > 7 days`, enforcing short-lived ephemeral credential hygiene.
- **Stale Consumers on Deprecated Versions**: Surfaces workloads that have missed heartbeats or failed to transition to the current active secret version.
