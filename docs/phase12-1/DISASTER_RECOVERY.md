# SecretVault Phase 12.1 — Disaster Recovery & High Availability Specification

## 1. Scope & System Architecture

SecretVault is architected as a stateless application layer supported by a highly available distributed state persistence topology:
- **Relational Metadata & Ciphertext Storage:** PostgreSQL 16 (Cluster with WAL archiving and streaming replication)
- **Distributed Lock Coordinator & Transient Cache:** Redis 7 (Sentinel / Redis Cluster)
- **Key Management & Envelope Decryption:** Local or Cloud Hardware Security Module (KMS / HSM)

---

## 2. Component Failure Recovery Procedures

### 2.1 Backend / Worker Process Termination
- **During State `GENERATING` / `VALIDATING`:**
  - If a worker crashes mid-generation, the distributed lock expires after TTL (5 minutes).
  - A surviving worker or scheduler node queries `findRetryableJobs` for active jobs where `status IN ('QUEUED', 'VALIDATION_FAILED')`.
  - The job is safely retried with attempt count incremented.
- **During State `STAGED` / `ACTIVATING`:**
  - The newly encrypted secret version record $v_{N+1}$ is already persisted in PostgreSQL within a transactional boundary.
  - If activation is interrupted, the job is resumed at `ACTIVATING` without re-generating a conflicting secret value.
- **During State `GRACE_PERIOD`:**
  - Grace period expiration is decoupled from process memory. `RotationSchedulerService.processGracePeriodExpirations` evaluates `gracePeriodEndsAt <= now()` across all nodes and completes the lifecycle when due.

### 2.2 Redis Cluster Outage / Restart
- **Locking Fallback:** `RotationDistributedLock` catches Redis connection exceptions and safely falls back to node-local concurrent hash maps.
- **Authorization Invariant:** Redis unavailability **NEVER** causes authorization or encryption to fail open. All policy and permission checks resolve directly against PostgreSQL and local in-memory cryptographic verifications.

### 2.3 Database Crash / Point-in-Time Recovery (PITR)
- **RPO (Recovery Point Objective):** $< 5$ minutes (Continuous WAL archiving).
- **RTO (Recovery Time Objective):** $< 15$ minutes (Automated replica promotion).
- **Version Integrity:** Because all secret versions are immutable and numbered sequentially with authenticated ciphertext ($GCM$ tags with AAD binding secret ID, environment ID, and version number), restoring an earlier database backup preserves cryptographic integrity without corrupted states.

---

## 3. Disaster Recovery Runbook Checklist

- [x] Primary database failover verification
- [x] Redis cache flush and restart without data loss
- [x] In-flight rotation job state reconciliation on service boot
- [x] Ephemeral lease TTL expiration background sweep resumption
- [x] Stale consumer background detector resumption
