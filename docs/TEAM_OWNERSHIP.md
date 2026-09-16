# SecretVault — Two-Developer Ownership Matrix & Collaboration Rules

To ensure velocity, accountability, and pristine architecture, responsibilities are cleanly distributed between two primary software engineers.

---

## 1. Primary Domain Ownership

### Member 1 — Platform, Core Security & Data Model
- **Authentication & Identity:** User lifecycle, sessions, JWT token issuance, password reset, MFA.
- **Workspaces & Organizations:** Multi-tenant hierarchy, workspace switching, tenant membership.
- **Access Control & RBAC:** Role definitions (`OWNER`, `ADMIN`, `DEVELOPER`, `VIEWER`), granular permission evaluator, JIT access workflows, access review certification campaigns.
- **Projects & Environments:** Project containers, Dev/Staging/Prod environment boundaries and isolation.
- **Core Secret Engine:** Cryptographic envelope encryption (AES-256-GCM + DEK/KEK), secret CRUD, immutable versioning, rollback, secret diffing, rotation wizard.
- **Audit & Governance:** Append-only audit ledger, compliance center, network policies, IP allowlists.
- **Security Center:** Unified security posture, risk center, secret leak investigation, blast radius visualizer, dependency graph, incident management.

### Member 2 — Integrations, Developer Platform & Operations
- **Provider Adapters:** `SecretProvider` SPI and concrete adapters (AWS, Vercel, Railway, Render, Netlify, GitHub, Kubernetes, Cloudflare).
- **Synchronization Engine:** Asynchronous sync queue worker, retry engine, exponential backoff, drift detection.
- **Developer CLI & Runtime:** `secretvault` CLI, in-memory process secret injection (`secretvault run`), environment profiles, configuration templates.
- **Client SDKs & Tools:** Java SDK, Node/TypeScript SDK, Python SDK, Go SDK, VS Code extension, secret watch.
- **Machine Identities & CI/CD:** Service accounts, Workload Identity Federation (OIDC) for GitHub Actions and GitLab CI.
- **Kubernetes & Cloud-Native:** Kubernetes Operator, CRDs, External Secrets Operator (ESO) provider.
- **AI Intelligence Integration:** Python/FastAPI co-pilot client, Deployment RCA, anomaly analysis.
- **Deployment & Infra:** Docker Compose, Helm charts, Terraform integrations, System Health.

---

## 2. Shared Domain Responsibilities

Both developers collaborate on, review, and co-own:
- **API Contracts & OpenAPI Specifications** (`docs/API.md`, OpenAPI annotations)
- **Flyway Database Migrations** (`backend/src/main/resources/db/migration/*`)
- **Maven Dependencies & Root Build Configurations** (`pom.xml`, `Dockerfile`)
- **Docker Compose & CI/CD Pipelines** (`docker-compose.yml`, `.github/workflows/*`)
- **Shared Utilities & Exceptions** (`com.secretvault.common.*`)
- **Architecture Decision Records (ADRs) & Core Documentation**

---

## 3. High-Conflict Shared Files & Edit Rules

High-conflict shared files:
1. `backend/pom.xml`
2. `docker-compose.yml`
3. `backend/src/main/resources/application.yml`
4. `backend/src/main/resources/db/migration/*`
5. `.github/workflows/*`

### Coordination Protocol:
1. Always `git fetch origin && git rebase origin/main` immediately before editing.
2. Communicate the planned change to your teammate.
3. Make minimal, surgical additions without reformatting unrelated lines.
4. Run full build and tests (`mvn test`).
5. Open PR and merge promptly to minimize branch divergence.
