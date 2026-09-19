# SecretVault — Architecture Decision Records (ADR)

## ADR-000: Architecture Decision Record Template

### Status: [Proposed | Accepted | Superseded | Deprecated]
### Date: YYYY-MM-DD
### Context:
What is the business or technical problem being solved? What are the constraints and alternatives considered?

### Decision:
What is the specific architectural or design decision made?

### Consequences:
- **Positive:** What benefits or simplifications are achieved?
- **Negative:** What trade-offs, technical debt, or operational costs are accepted?

---

## ADR-001: Modular Monolith Architecture Baseline

### Status: Accepted
### Date: 2026-09-16
### Context:
SecretVault is being constructed by a two-engineer team. Premature microservices create severe deployment overhead, network latency, distributed transaction failure modes, and developer friction.

### Decision:
SecretVault is implemented as a **Modular Monolith** using Spring Boot 3 (Java 21) with strictly encapsulated domain packages (`com.secretvault.<domain>`).

### Consequences:
- **Positive:** Single atomic deployment, shared JVM memory, zero network latency between domains, simple local Docker setup.
- **Negative:** Requires disciplined code reviews to prevent cross-domain coupling.

---

## ADR-002: AES-256-GCM Envelope Encryption

### Status: Accepted
### Date: 2026-09-16
### Context:
Storing plaintext secrets in PostgreSQL is unacceptable. Direct single-key encryption lacks key rotation agility.

### Decision:
All secret values are encrypted with **AES-256-GCM** authenticated envelope encryption. Each secret version receives a unique Data Encryption Key (DEK), which is wrapped by a master Key Encryption Key (KEK) backed by KMS/HSM.

### Consequences:
- **Positive:** Zero plaintext in database; ciphertext tampering immediately fails AEAD verification; easy key rotation.
- **Negative:** Requires KMS connectivity in cloud environments; slight encryption CPU overhead.

---

## ADR-003: Flyway Database Versioning

### Status: Accepted
### Date: 2026-09-16
### Context:
Database schema changes must be deterministic, repeatable, and automated across environments.

### Decision:
Flyway is the sole schema migration engine. Applied migrations are immutable.

### Consequences:
- **Positive:** Automated, audited schema updates in CI/CD and production.
- **Negative:** Migrations must maintain backward compatibility during deployments.

---

## ADR-004: Asynchronous Provider Synchronization

### Status: Accepted
### Date: 2026-09-16
### Context:
Synchronizing secrets to external cloud providers (AWS, Vercel, Railway, GitHub) can suffer from third-party rate limits, latency, or outages.

### Decision:
Provider synchronization is asynchronous. Requests push jobs to a Redis-backed queue processed by background workers with exponential backoff retries.

### Consequences:
- **Positive:** Fast HTTP response times, resilience against third-party provider downtimes.
- **Negative:** Frontend monitors job completion asynchronously.

---

## ADR-005: Redis for Ephemeral Caching, Locks & Rate Limiting

### Status: Accepted
### Date: 2026-09-16
### Context:
Distributed synchronization jobs and public authentication endpoints require rate limiting and mutex locking without burdening PostgreSQL.

### Decision:
Redis 7 is adopted for sliding-window rate limiting, distributed synchronization locks (`sync:lock:*`), and temporary JIT grants. PostgreSQL remains the sole durable source of truth.

---

## ADR-006: Independent Python / FastAPI AI Service

### Status: Accepted
### Date: 2026-09-16
### Context:
AI capabilities (incident analysis, deployment root cause analysis) require rapid Python ecosystem integration while keeping core secret retrieval 100% reliable.

### Decision:
The AI co-pilot is an independent Python/FastAPI microservice. The core backend never depends on the AI service for core operations, and plaintext secrets are never sent to external LLMs.

---

## ADR-007: Strict Multi-Tenant Hierarchy

### Status: Accepted
### Date: 2026-09-16
### Context:
Enterprise environments require clear isolation between organizations, teams, projects, and deployment stages.

### Decision:
Every resource belongs to an `Organization -> Workspace -> Project -> Environment` hierarchy, enforced at the repository and service layer.

---

## ADR-008: In-Memory Child Process Secret Injection (`secretvault run`)

### Status: Accepted
### Date: 2026-09-16
### Context:
Storing development secrets in `.env` files on developer machines leads to accidental git commits and secret leakage.

### Decision:
The CLI decrypts secrets into memory and injects them directly into the child process environment block without writing plaintext files to disk.

---

## ADR-009: Stitch UI Design System as Single Visual Source of Truth

### Status: Accepted
### Date: 2026-09-16
### Context:
126 application screens have been designed in Stitch (`stitch_secretvault_devsecops_platfor/`). Redesigning or using generic templates degrades user experience.

### Decision:
The Stitch designs are the definitive visual source of truth. All frontend pages are constructed by composing reusable components adhering to the Stitch design system.

---

## ADR-010: Cryptographic AAD Context Binding & Immutable Secret Versioning

### Status: Accepted
### Date: 2026-09-17
### Context:
In multi-tenant secret management, ciphertext swapping attacks (where an attacker copies valid ciphertext from one environment or secret row to another) and unauthorized overwrites represent critical threat vectors. Furthermore, compliance requires a strict, monotonic audit trail of all secret mutations.

### Decision:
1. **AAD Context Binding:** Every AES-256-GCM encryption operation cryptographically binds Authenticated Additional Data formatted as `secretId:environmentId:versionNumber`. Any attempt to decrypt ciphertext in a different secret, environment, or version fails AEAD authentication immediately (`SECRET_DECRYPTION_FAILED`).
2. **Immutable Version Ledger:** Secret values are never mutated in place. Every value update generates a fresh random 256-bit DEK and 96-bit IV, creates an immutable row in `secret_versions`, and atomically advances the `current_version_number` pointer on the parent `secrets` record.
3. **Zero-Plaintext Default & Explicit Reveal:** All standard REST endpoints return only sanitized metadata (`SecretMetadataResponse`). Decryption occurs strictly on-demand via `POST /reveal` with HTTP `Cache-Control: no-store, no-cache` headers and append-only audit logging.

### Consequences:
- **Positive:** Complete immunity against cross-tenant or cross-environment ciphertext replay/transplantation; total audit traceability; zero risk of stale HTTP cache leaks.
- **Negative:** Storage grows with each secret version; requires version pruning/archival policies for high-frequency automated secret churn.

---

## ADR-011: 3-Way Branch Merging, Rollback-as-New-Version, and Cross-Environment Secret Promotion Engine

### Status: Accepted
### Date: 2026-09-17
### Context:
Modern DevSecOps workflows require secret experimentation in isolated feature branches, instant zero-downtime rollback upon misconfiguration, and verified cross-environment promotion (`Development -> Staging -> Production`). However, rolling back by mutating existing history breaks cryptographic immutability, naive promotions risk leaking development keys to production, and concurrent branch merges risk silent overwrite regressions.

### Decision:
1. **3-Way In-Memory Merge Engine:** Feature branches maintain their own version head without altering the main trunk current version pointer. During merge (`POST /branches/{id}/merge`), the server decrypts `BASE`, `OURS (main)`, and `THEIRS (branch)` in memory. If both branches have diverged with different edits since base, a `409 Conflict` is returned with full conflict details. Clean merges generate a new version on main with `versionType = MERGE`.
2. **Rollback-as-New-Version ($v_N \to v_{N+1}$):** Rollbacks never delete or rewrite existing version records. The target version's value is decrypted in memory and immediately encrypted into a brand new version $v_{N+1}$ with fresh 256-bit DEK, 96-bit random IV, and new AAD context binding `secretId:environmentId:N+1`.
3. **Cross-Environment Promotion Engine:** Promotion provides a dry-run preview (`POST /promote/preview`) classifying each secret as `ADDED`, `MODIFIED`, `UNCHANGED`, or `BLOCKED_DISABLED`. Execution (`POST /promote`) provisions destination secrets, re-encrypts with destination environment AAD, preserves lineage (`sourceEnvironmentId`, `sourceSecretId`, `sourceVersionId`), and skips duplicate unchanged writes.
4. **DoS-Protected In-Memory Diffing:** Comparisons return metadata and entropy scores by default. Explicit line diffing enforces a strict 64KB size limit, never logs plaintexts, and zeroes memory buffers immediately after diff evaluation.

### Consequences:
- **Positive:** Complete cryptographic immutability preserved across rollbacks; full branch isolation; conflict-free 3-way merges; dry-run visibility before promoting to protected production enclaves.
- **Negative:** Ephemeral in-memory decryption required during 3-way diffing and promotion preview. Mitigated by zeroization and strict memory isolation.

---

## ADR-012: Environment Branch Policy Hardening (Feature Branches in Development Only)

### Status: Accepted
### Date: 2026-09-18
### Context:
In DevSecOps governance, feature branches are a development experimentation and staging tool. Allowing feature branch creation, branch commits, or branch merges directly within `STAGING` or `PRODUCTION` environments creates severe compliance and security risks, including unauthorized configuration drift, untracked emergency bypasses, and unverified production changes.

### Decision:
1. **Server-Side Enforcement by Environment Type:** The backend strictly authorizes branch mutations (`createBranch`, `createBranchVersion`, `archiveBranch`, `mergeBranch`, `compareBranchWithMain`, `getBranchById`) only when `environment.envType == EnvType.DEVELOPMENT`.
2. **Deterministic Rejection:** Any attempt to perform branch creation, commit, or merge in `STAGING` or `PRODUCTION` environments is rejected with HTTP 400 and error code `BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT`.
3. **Canonical Trunk Only on Non-Dev:** Staging and Production environments only possess the single canonical `main` trunk. Queries to list branches on non-development environments return solely the canonical `main` branch descriptor without evaluating custom feature branches.
4. **Promotion as the Sole Progression Pathway:** Production mutations must proceed strictly through verified `Promotion` (`Development -> Staging -> Production`). Promotion creates independent destination `SecretVersion` records with fresh encryption material, zero destination `branch_id`, and full source lineage preservation.
5. **UI Policy Transparency:** Frontend components explicitly display environment branch status (`Branches: Enabled` for Development, `Branches: Disabled` for Staging/Production) and omit branch management controls on non-development environments.

### Consequences:
- **Positive:** Zero risk of production branch drift or rogue feature branches in production; guaranteed linear version progression in high-integrity enclaves; promotion pipeline remains the sole path to production.
- **Negative:** Hotfixes must be applied either directly to `main` in Development and promoted through Staging, or via standard authorized single-secret update workflows in the designated environment.

---

## ADR-013: Centralized Authorization Foundation, Effective Permission Pipeline & "Why Access" Lineage

### Status: Accepted
### Date: 2026-09-18
### Context:
As SecretVault expands with granular resource access grants (Phase 5.2), Just-In-Time temporary elevations (Phase 5.3), and periodic access review campaigns (Phase 5.4), scattered authorization checks across individual controllers risk security drift, bypasses of protected environment rules, and lack of lineage transparency.

### Decision:
1. **Canonical Permission Registry (`AccessPermission`):** Standardized machine-readable permission codes (`secret.read`, `secret.create`, `secret.update`, `secret.delete`, `secret.reveal`, `secret.rollback`, `secret.branch`, `environment.promote`, `environment.manage`, `access.manage`, `jit.request`, `jit.approve`, `access_review.manage`).
2. **Deterministic Evaluation Pipeline (`EffectiveAccessService`):** All access decisions evaluate sequentially:
   `Authentication -> Tenant/Workspace Boundary -> Project Boundary -> Environment Boundary -> Hard Invariants (e.g. Development-only branches) -> Standing Scoped RBAC -> [Future Granular Grants & JIT Hooks] -> Default Deny`.
3. **No Automatic Root Bypass:** `OWNER` and `ADMIN` roles do not receive indiscriminate root bypasses that undermine granular governance, audit trails, or protected environment rules.
4. **"Why Do I Have Access?" Transparency (`explainAccess`):** Every authorization decision captures composite source attribution (`WORKSPACE_ROLE`, `PROJECT_ACCESS`, `ENVIRONMENT_ACCESS`, `GRANULAR_GRANT`, `JIT_GRANT`) and human-readable operational rationale, establishing the foundation for access certification reviews.
5. **Zero Plaintext Invariant:** Plaintext secret values, cryptographic keys, and sensitive tokens are strictly prohibited from authorization decision records, error messages, exceptions, and audit logs.

### Consequences:
- **Positive:** Centralized, fail-closed authorization engine; zero duplicate authorization logic; seamless extension points for Phase 5.2 granular grants and Phase 5.3 JIT elevations; full audit lineage.
- **Negative:** Evaluating deep hierarchies incurs minor object traversal overhead, mitigated by JPA indexed lookups.

---

## ADR-014: Granular Access Control, JIT Temporary Access & Access Reviews (Phase 5)

### Status: Accepted
### Date: 2026-09-19
### Context:
Modern enterprise compliance frameworks (SOC 2, ISO 27001, HIPAA) require eliminating static permanent administrative credentials (Zero Standing Privilege), enforcing strict dual-custody approval for high-risk actions, and conducting regular periodic access reviews with automated remediation.

### Decision:
1. **Granular Resource-Level Grants (`access_grants`):**
   - Supports explicit permission overrides at `WORKSPACE`, `PROJECT`, `ENVIRONMENT`, or `SECRET` levels.
   - Enforces hierarchical check constraints preventing orphaned or cross-tenant assignments.
2. **Just-In-Time (JIT) Temporary Elevation (`jit_access_requests`):**
   - Ephemeral elevation with strict TTL (5 to 240 minutes) and automated expiry evaluation.
   - **Anti-Self-Approval Enforcement:** Users are cryptographically and server-side barred from approving their own elevation requests, enforcing true dual-custody separation of duties.
   - **Pessimistic Concurrency Locking:** `findByIdAndWorkspaceIdForUpdate` prevents race conditions during concurrent approval or revocation calls.
3. **Access Review Certification Campaigns (`access_review_campaigns` & `access_review_items`):**
   - Automated point-in-time snapshotting of all standing workspace roles, scoped project/environment grants, granular grants, and active JIT elevations.
   - Lineage attribution ("Why Access?") presented directly to auditors and certifiers.
   - **Targeted Revocation:** Deciding `REVOKE` on any review item immediately executes domain-specific revocation against the referenced standing grant or JIT record.
   - **Cryptographic Attestation Ledger:** Finalizing a completed campaign seals an immutable compliance record with certifier identity, timestamp, item count breakdown, and SHA-256 integrity reference.

### Consequences:
- **Positive:** Complete Zero Standing Privilege (ZSP) architecture; automated compliance certification; zero-trust ephemeral elevations with live countdown timers; full auditability.
- **Negative:** Additional database tables and index maintenance; periodic review campaigns require administrative attention.

---

## ADR-015: Just-In-Time (JIT) Temporary Access and Ephemeral Privilege Elevation (Phase 5.3)

### Status: Accepted
### Date: 2026-09-19
### Context:
Permanent administrative and reveal privileges on production secrets increase the attack surface and violate the principle of least privilege. Engineers require temporary access to investigate incidents and perform critical operations without accumulating standing privileges.

### Decision:
1. **Time-Bound Ephemeral Elevation (`jit_access_requests`):** JIT grants are strictly time-bound (5 to 240 minutes), evaluated on-the-fly against real-time UTC clock boundaries (`clock.instant() < expiresAt`). Expired grants immediately cease authorizing operations without relying solely on background schedulers.
2. **Mandatory Separation of Duties (Anti-Self-Approval):** Requesters are strictly prohibited from approving their own JIT requests, regardless of their workspace role (including `OWNER` and `ADMIN`).
3. **Scoped Approval Authority Containment:** An approver may only approve requests within their authorized management boundary (Workspace > Project > Environment > Secret). Lower-scoped approvers cannot approve broader-scoped requests.
4. **Pessimistic Concurrency Locking:** Concurrent approval, rejection, cancellation, and revocation state transitions utilize pessimistic database locks (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) to eliminate race conditions.
5. **Audited Lifecycle State Machine:** Explicit transitions (`PENDING -> APPROVED / REJECTED / CANCELLED`, `APPROVED -> REVOKED / EXPIRED`) emit structured audit records with operational justification and zero secret plaintext.

### Consequences:
- **Positive:** Enforces Zero Standing Privilege (ZSP); eliminates self-approval risks; provides immediate revocation capabilities; guarantees strict audit trails for SOC 2 and ISO 27001 compliance.
- **Negative:** Requires approver intervention for elevated privileges; short TTLs require timely execution of incident tasks.



