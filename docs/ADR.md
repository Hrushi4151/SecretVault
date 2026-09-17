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

