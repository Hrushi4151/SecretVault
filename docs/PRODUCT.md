# SecretVault — Product Specification & Requirements

## 1. Product Identity & Vision

**SecretVault** is a next-generation **DevSecOps Secret Management and Security Control Plane** designed to unify, protect, and automate application secrets across their complete software lifecycle.

> **Core Product Statement:**  
> *"One secure control center for application secrets, regardless of where the application is hosted or where the secret is consumed."*

SecretVault is **NOT** a consumer password manager (like 1Password or Bitwarden for end-user logins). It is a developer- and infrastructure-focused platform connecting:
- Human Developers & DevOps Engineers
- Applications & Runtime Workloads
- CI/CD Pipelines (GitHub Actions, GitLab CI)
- Cloud Infrastructure (AWS, Azure, GCP, Cloudflare)
- Container Platforms (Kubernetes, ECS)
- Source Code Repositories
- Non-Human Machine Identities & Service Accounts
- Security Intelligence & AI Systems

---

## 2. Product Positioning & Six Control Plane Layers

```text
                    SECRET VAULT

                       WEB
                Control Plane
                       │
        ┌──────────────┼──────────────┐
        │              │              │
     Developer     Automation      Security
      Platform       Layer          Platform
        │              │              │
       CLI           CI/CD         Risk/Leaks
       SDK           OIDC          Drift
    VS Code       Kubernetes      Audit
   Local Dev      Terraform       Incidents
        │              │              │
        └──────────────┼──────────────┘
                       │
                 SECRET ENGINE
                       │
             Provider Integrations
                       │
                    CLOUD
```

### Core Product Principles:
1. **Security First**: Cryptographic envelope encryption by default. No plaintexts in database or telemetry.
2. **Least Privilege**: Granular RBAC and temporary Just-In-Time (JIT) access.
3. **Zero Unnecessary Secret Exposure**: Masked by default (`••••••••`), no secrets in URLs, logs, or analytics.
4. **Strong Tenant Isolation**: Strict Organization -> Workspace -> Project -> Environment scoping.
5. **Developer-First UX**: In-memory child process secret injection (`secretvault run`) eliminates `.env` file clutter.
6. **Infrastructure Awareness**: Real-time drift detection across external cloud hosting providers.
7. **Reliable Synchronization**: Asynchronous, idempotent sync queue with exponential backoff.
8. **Complete Auditing**: Immutable, non-repudiable audit logs for all sensitive transitions.
9. **AI as an Advisory Intelligence Layer**: Co-pilot for root cause analysis and security posture.
10. **Core Operability Without AI**: The secret engine and control plane function 100% reliably if AI is offline.

---

## 3. Product Pillars & Capabilities

### Pillar 1: Secure Secret Engine [PLANNED]
- **Lifecycle:** `CREATE` → `STORE` → `USE` → `SYNC` → `UPDATE` → `ROTATE` → `REVOKE` → `DELETE`
- **Capabilities:** AES-256-GCM envelope encryption, immutable versioning, rollback, Git-like secret branching, metadata classification, secret diffing, rotation policies, ownership, and usage tracking.

### Pillar 2: Multi-Platform Provider System [PLANNED]
- Decoupled adapter architecture (`SecretProvider` SPI).
- Connectors: AWS (Secrets Manager / Parameter Store), Vercel, Railway, Render, Netlify, GitHub Actions, GitLab CI, Cloudflare, Kubernetes, Fly.io, Terraform Cloud, Slack.

### Pillar 3: Asynchronous Synchronization Engine [PLANNED]
- Desired state vs. provider state management.
- States: `PENDING`, `RUNNING`, `SYNCED`, `DRIFTED`, `FAILED`, `MISSING`, `UNKNOWN`.
- Resilience: Idempotent execution, exponential backoff retries, provider health probes, failure taxonomy.

### Pillar 4: Developer Execution Layer [PLANNED]
- `secretvault` CLI injecting secrets into child process memory without writing plaintext `.env` files.
- VS Code extension (secret autocomplete, reference links, leak warnings).
- Lightweight runtime client SDKs (Java, Node/TypeScript, Python, Go).
- Local development profiles, environment cloning, template manager.

### Pillar 5: Security Intelligence [PLANNED]
- Central Risk Center, secret leak scanner (pre-commit, pre-push, PR gates), blast radius visualizer, dependency graph, JIT access, periodic access certification campaigns, IP allowlists.

### Pillar 6: AI Intelligence Layer [PLANNED]
- Python/FastAPI advisory service for deployment root cause analysis (RCA), anomaly interpretation, and policy recommendations. Strict zero-plaintext prompt policy.

---

## 4. Multi-Tenant Hierarchy

```text
Organization (Tenant Root / Enterprise Account)
  └── Workspace (Team / Departmental Scope)
        └── Project (Application / Microservice / API)
              └── Environment (Development, Staging, Production)
                    └── Secret (Encrypted value + Version history + Metadata)
```

---

## 5. Implementation Status Distinction

- **CURRENT PRODUCT FOUNDATION (IMPLEMENTED):**
  - Modular Monolith Java 21 / Spring Boot 3.3.4 backend baseline.
  - Maven configuration, Docker Compose (PostgreSQL 16, Redis 7, Backend).
  - Flyway migration baseline (`V1__init_baseline.sql`).
  - Spring Security baseline (stateless, public health/OpenAPI whitelist, secure API default).
  - Standard RFC-7807 error handling (`GlobalExceptionHandler`, `ErrorResponse`).
  - Request tracing & Correlation ID filter (`CorrelationIdFilter`).
  - Actuator & custom health endpoints (`/actuator/health`, `/api/v1/health`).
  - Complete architecture, security, and developer specifications.
- **PLANNED CAPABILITIES (ROADMAP):**
  - Business domain endpoints (Auth, Organization, Secrets, Providers, Sync, Security).
  - React/TypeScript/Tailwind CSS Frontend matching the 126 Stitch screens.
  - CLI, SDKs, Kubernetes Operator, and AI Intelligence Service.
