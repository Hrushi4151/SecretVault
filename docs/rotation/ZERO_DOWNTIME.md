# Zero-Downtime Rollouts & Safe Rollbacks

## 1. Zero-Downtime Dual-User Database Rollover

Rotating database credentials in a live production microservice architecture without zero-downtime controls causes immediate connection drops and database authentication errors.

SecretVault implements **Dual-User Alternating Credential Rollover** for PostgreSQL and MySQL:

```
Time --------------------------------------------------------------------------------->
Old User (app_user_a): [================ ACTIVE ================] [== GRACE ==] [REVOKED / DROPPED]
New User (app_user_b):                                 [ STAGING ] [=== ACTIVE =====================>
SDK / App Clients:                                     Using user_a  Grace Switch  Fully on user_b
```

### PostgreSQL & MySQL Dual-User Lifecycle

1. **Pre-flight & Configuration Parsing**:
   - The `DatabaseRotator` parses database generator configurations (`baseUsername`, `database`, `schema`, `engine`, `jdbcUrl`, `adminUsername`).
   - Strict regex identifier validation (`^[a-zA-Z][a-zA-Z0-9_]{0,62}$`) is enforced across all identifiers (usernames, databases, schemas, roles) to completely prevent SQL injection in DDL commands.

2. **Alternate User Creation & DDL Staging**:
   - For target version $v_N$, `DatabaseRotator` derives the alternate username (e.g. `base_user_a` if odd version, `base_user_b` if even version).
   - A cryptographically secure password is generated with high entropy via `SecretGenerationEngine`.
   - **PostgreSQL**: Executes `CREATE USER "app_user_b" WITH PASSWORD '...' LOGIN NOSUPERUSER`, grants roles, and grants schema USAGE/DML privileges.
   - **MySQL**: Executes `CREATE USER IF NOT EXISTS `app_user_b`@'%' IDENTIFIED BY '...'` and grants database DML privileges followed by `FLUSH PRIVILEGES`.

3. **Immediate Connectivity Validation**:
   - Before transitioning to `STAGED`, `DatabaseRotator` establishes a live JDBC connection using the candidate credentials (`app_user_b`) and runs `conn.isValid(5)`.
   - If connectivity fails, the lifecycle transitions to `VALIDATION_FAILED` and candidate credentials are not promoted.

4. **Activation & Secret Versioning**:
   - Staged secret is encrypted under AES-256-GCM envelope encryption and saved as new immutable `SecretVersion`.
   - Secret state becomes `ACTIVE`.
   - Outbox domain event `ROTATION_ACTIVATED` is transactionally published with metadata-only `RotationProviderPushEvent` (strictly zero plaintext secret material).

5. **Dual-Credential Grace Window**:
   - The previous user (`app_user_a`) remains fully active and operational on the database for the configured `gracePeriodSeconds` (e.g., 24 hours).
   - Application pods and external consumers update their database connection pools to `app_user_b` without downtime.

6. **Decommissioning & Cleanup**:
   - When the grace period expires, `RotationSchedulerService` triggers `revokePrevious()`.
   - **PostgreSQL**: Revokes permissions, drops the old user (`DROP USER IF EXISTS "app_user_a"`), or falls back to `ALTER USER "app_user_a" NOLOGIN` if objects remain owned.
   - **MySQL**: Executes `DROP USER IF EXISTS `app_user_a`@'%'` and flushes privileges.

7. **Rollback Safety**:
   - If activation fails or rollback is invoked during staging, `rollback()` executes DDL to safely drop the newly created candidate user without touching the primary active user.

---

## 2. Immutable Versioning & Rollback Invariants

All secret versions in SecretVault are **append-only and immutable**:
- Rollback **NEVER** deletes history or modifies previous versions.
- A rollback creates a new version $v_{N+1}$ mirroring the payload of the chosen target version.
- An audit event `ROTATION_ROLLED_BACK` and domain event `ROTATION_ROLLED_BACK` are recorded with full actor traceability.

---

## 3. Transactional Outbox Event Integration

All rotation lifecycle transitions publish domain events into the transactional outbox:
- Events include `ROTATION_POLICY_CREATED`, `ROTATION_POLICY_UPDATED`, `ROTATION_TRIGGERED`, `ROTATION_STARTED`, `ROTATION_ACTIVATED`, `ROTATION_GRACE_STARTED`, `ROTATION_COMPLETED`, `ROTATION_FAILED`, `ROTATION_ROLLED_BACK`, and `SECRET_COMPROMISED`.
- **Zero Plaintext Invariant**: Event metadata is scrubbed by `BaseDomainEvent` sanitization rules. Plaintext secret values, generated passwords, DEKs, and sensitive tokens are strictly prohibited from event payloads.
- The `RotationProviderPushEvent` boundary provides safe metadata (`secretId`, `secretName`, `newVersionNumber`, `previousVersionNumber`, `jobId`, `policyId`, `triggerType`, `activatedAt`) for downstream provider workers.
