# Zero-Downtime Rollouts & Safe Rollbacks

## 1. Zero-Downtime Secret Rollover

Rotating a credential in a live production microservice architecture without zero-downtime controls causes service outages due to clock skew, in-flight HTTP requests, and cache propagation latency.

SecretVault implements **Dual-Credential Overlapping Grace Periods**:

```
Time ----------------------------------------------------------------------->
Old Version:  [================ ACTIVE ================] [== GRACE ==] [REVOKED]
New Version:                                 [ STAGING ] [=== ACTIVE ============>
SDK Clients:                                 Fetch vN    Receive vN+1  Fully on vN+1
```

### Staging Phase
1. `SecretRotator` provisions the new credential (e.g. creating `app_user_v2` in PostgreSQL or adding an auxiliary API key in Stripe).
2. The old credential remains 100% active and servicing live client traffic.

### Validation Probe
1. `RotationValidationEngine` tests the new candidate credential against synthetic endpoints or the real database.
2. If validation fails, the job immediately aborts and tears down the candidate without touching the production secret.

### Dual-Credential Grace Window
1. New version is activated as primary in SecretVault.
2. The old credential remains valid in the external provider for `gracePeriodSeconds` (e.g. 30 minutes).
3. SecretVault SDK instances receive background version notifications or poll dynamic changes and update their local in-memory caches.

### Deprovisioning & Cleanup
1. Once `gracePeriodEndsAt` expires, the `RotationSchedulerService` or `RotationService` triggers the `REVOKING` phase.
2. `rotator.revokePrevious()` deprovisions the previous credential from the target system.

---

## 2. Immutable Versioning & Rollback Safety

All secret versions in SecretVault are **append-only and immutable**. When an operator rolls back to a previous version:
- The system **NEVER** deletes history or overwrites existing version numbers.
- A rollback creates a new version: **$v_{N+1}$**, duplicating the payload of the chosen target historical version.
- An explicit audit event `ROTATION_ROLLED_BACK` is recorded with full actor traceability and rationale.
