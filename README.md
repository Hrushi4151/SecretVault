# SecretVault

> A secure DevSecOps control plane for managing application secrets once and synchronizing them across the platforms where software runs.

SecretVault gives engineering teams one place to manage secrets, environments, access, synchronization, security posture, and operational evidence. It is designed for application secrets—not as a consumer password manager—and treats projects, environments, providers, deployments, and auditability as first-class concepts.

> **Status:** Product specification and implementation foundation. Sections marked **Roadmap** describe intended future capabilities, not functionality that is already available.

## Architecture at a glance

```text
People, CI/CD, and future CLI
             │  authenticated, authorized requests
             ▼
┌─────────────────────────────────────────────────────────────┐
│                         SecretVault                          │
│  React web app → Spring Boot services → PostgreSQL / Redis   │
│       │                 │                   │                │
│       │                 ├─ encrypted secret versions          │
│       │                 ├─ audit & security events            │
│       │                 └─ asynchronous sync jobs             │
│       │                                     │                 │
│       │                         provider adapter layer        │
│       └─────────────────────────────────────┼─────────────────┘
└─────────────────────────────────────────────┼─────────────────┘
                                              ▼
                         Vercel · AWS · Railway · Render
                         Netlify · GitHub · future providers

Python/FastAPI services support future security and AI analysis. AI receives only the minimum authorized operational context; it is never the system of record for secret values.
```

## The problem

Modern software commonly spreads the same credential between a local `.env` file, staging hosting, production cloud infrastructure, CI/CD, background workers, and developer machines. That leads to duplicate secrets, out-of-date values, slow and error-prone rotations, unclear ownership, and little evidence of who accessed or changed something.

It also creates configuration drift: for example, the central `DATABASE_URL` may be at version 5 while a deployment provider still uses version 4. Conventional stores may protect the value itself, but often leave teams without a unified view of drift, aging credentials, risky access patterns, integration health, or potential exposure.

## The solution

SecretVault is the central control plane between a team and its infrastructure providers:

```text
Developer / CI
      │
      ▼
SecretVault: encrypted store + versions + RBAC + audit + security
      │
      ▼
Async synchronization engine
      │
      ├── Vercel
      ├── AWS
      ├── Railway
      ├── Render
      ├── Netlify
      └── GitHub
```

The system stores the authoritative secret version, records sensitive actions, and uses explicit project/environment mappings to synchronize safely. It never assumes a provider can reveal plaintext values in order to assess drift.

## Product pillars

1. **Secure secret management** — encrypted values, metadata, versions, controlled reveal, rotation, rollback, import, and deletion.
2. **Multi-platform integrations** — a provider-adapter model so platforms can be connected without coupling the core domain to one vendor.
3. **Reliable synchronization** — queued jobs, status tracking, verification, safe retries, and drift detection.
4. **Security intelligence** — visibility into exposure, age, access, configuration health, and risk.
5. **AI-assisted operations** — evidence-based explanations and recommendations, with human authorization for actions.

## Core product model

```text
Organization
├── Members and organization policies
├── Projects
│   └── Environments: Development, Staging, Production
│       ├── Secrets and version history
│       ├── Explicit provider mappings
│       ├── Synchronization state
│       └── Security findings
├── Integrations
└── Audit history
```

- An **organization** represents a company or team and owns members, policies, projects, integrations, and audit history.
- A **project** represents an application, such as an API, web app, or mobile backend.
- An **environment** scopes secrets and provider mappings. Initial environments are Development, Staging, and Production; custom environments are **Roadmap**.
- A **secret** has encrypted value material plus metadata: ID, name, description, tags, environment, current version, timestamps, creator, last-access information, risk, and synchronization status.

Secret search and global search operate on authorized metadata—not plaintext secret values—and must respect organization, project, and environment permissions.

## Core features

### Secret management

- Create secrets with a name, value, description, environment, and tags.
- Store values encrypted; plaintext secret values must never be stored in PostgreSQL.
- Create a new version for every update. Do not silently overwrite history.
- Support current and prior versions for safe change tracking and rollback.
- Provide controlled rotation, revocation, and deletion workflows.
- Import `.env` files after parsing and user confirmation; avoid showing values more than necessary.
- Mask values by default. Reveal requires re-authentication and MFA where configured, is temporary, and is audited.
- Permit export only where explicitly authorized; broad production-secret exports are not a casual action.

### Secret lifecycle

```text
Create → Encrypt & store → Version → Sync → Use
                    ↑                         │
                    └── Update / Rotate ──────┘
                                      ↓
                              Revoke → Delete
```

Every sensitive transition—including create, update, reveal, rotate, sync, revoke, and delete—creates an audit event without recording the secret value.

### Roles and access control (RBAC)

Initial organization roles:

| Role | Intended responsibility |
|---|---|
| `OWNER` | Organization ownership, policies, and highest-risk administration |
| `ADMIN` | Team, integrations, and operational administration |
| `DEVELOPER` | Day-to-day approved project/environment secret work |
| `VIEWER` | Authorized visibility without secret-changing authority |

Permissions evolve toward granular scopes such as `secret.read`, `secret.create`, `secret.update`, `secret.delete`, `secret.reveal`, `secret.rotate`, `integration.manage`, `sync.execute`, `audit.read`, `security.read`, `team.manage`, and `organization.manage`.

Access is scoped further by project and environment. A developer may have read/write access to Development and Staging while having no Production access. Production reveal, deletion, export, and rotation should receive heightened protection.

### Integrations and mappings

Initial planned providers are Vercel, AWS, Railway, Render, Netlify, and GitHub. Providers are represented through a common adapter boundary with capabilities such as testing a connection, discovering projects/environments, synchronizing or deleting a secret, and reporting health.

Connection flow: select provider → authenticate → select account and provider project → map a SecretVault project/environment → confirm permissions. Mappings must be explicit; no provider project or environment should be inferred silently.

**Roadmap:** Azure, Google Cloud, Kubernetes, Cloudflare, Fly.io, and GitLab.

### Synchronization and drift detection

Sync is asynchronous so a browser does not wait on a provider operation:

```text
Requested → queued → worker loads value securely → provider update
         → verification → audit event → completed or failed
```

Job states: `PENDING`, `RUNNING`, `SYNCED`, `FAILED`, `RETRYING`, `SKIPPED`, and `CANCELLED`.

Transient errors should retry with exponential backoff. Authentication, permission, validation, rate-limit, network, outage, and unknown failures are classified so users can act on the real cause. Permanent failures must not retry forever.

Drift reports whether the intended central state differs from observable provider state: `SYNCED`, `DRIFTED`, `MISSING`, or `UNKNOWN`. Since providers may not expose plaintext, comparison should rely on safe evidence such as version metadata, timestamps, provider metadata, hashes where appropriate, and observable status—not by retrieving or logging provider values.

### Security intelligence

Security views are intended to surface secret hygiene, rotation age, access control, synchronization health, leak protection, and risk severity. Risk can incorporate age, environment, privilege, exposure indicators, access frequency, rotation status, drift, and provider configuration.

**Roadmap:**

- Detection of likely leaked credentials in connected repositories, commits, files, and safely-integrated CI signals using patterns, entropy, contextual analysis, and classification.
- Leak remediation workflow: investigate → rotate/revoke → remove exposure → verify → resolve.
- Access anomaly detection based on baselines and evidence, especially for production access.
- Actionable recommendations such as rotating old credentials, enabling MFA, reducing unnecessary production access, reconnecting stale integrations, or resolving drift.

Findings must never display the exposed credential itself.

### AI features

**Roadmap:** An AI assistant answers authorized operational questions such as “Which production secrets need rotation?”, “Why did sync fail?”, and “Which projects have drift?” Responses should present a summary, evidence, risk, recommended next action, and related resources.

AI analysis may help summarize posture, investigate sync failures, and perform deployment root-cause analysis from authorized events, changes, jobs, and provider signals. It must not:

- become the secret-value storage layer;
- receive secret plaintext as routine context;
- bypass RBAC or reveal resources a user cannot access; or
- execute remediation, particularly in production, without explicit authorization.

### Audit and notifications

Audit records cover actions such as secret create/update/reveal/rotate/delete, integration connect/disconnect, sync lifecycle, invitations and role changes, and API-key lifecycle. An event records actor, action, resource, project, environment, timestamp, request ID, and result—never a secret value.

**Roadmap:** configurable in-app, email, Slack, and webhook notifications for critical exposures, risks, drift, sync failure, integration health, and relevant team events.

### API keys and CLI

API keys are intended for automation, CI/CD, infrastructure tooling, and a future CLI. Keys have a name, scopes, creation date, expiration, last-used time, and status. Their full value is shown only once.

**CLI roadmap:** authenticated retrieval/injection for approved environments, scoped automation, safe sync triggers, and CI/CD-friendly status checks. CLI design must preserve least privilege and must not encourage printing values to logs.

## Security architecture and non-negotiable rules

SecretVault is a security product; its design must prioritize confidentiality, authorization, traceability, and safe failure.

- Encrypt secret values before persistence. PostgreSQL may retain encrypted material and metadata, never plaintext values.
- Treat encryption keys, provider tokens, session material, and API keys as highly sensitive secrets with a separate, reviewed key-management design.
- TLS protects data in transit. Sensitive operations use authenticated, authorized server-side paths.
- Apply least privilege at organization, project, environment, API-key, provider-token, and service boundaries.
- Require re-authentication and MFA where configured before reveal and other high-risk operations.
- Mask values in the UI; minimize plaintext handling and lifetime in every component.
- Never put secret values in logs, audit records, analytics, error reports, browser storage, screenshots, issue trackers, commits, or AI prompts.
- Never index plaintext values; search names, tags, and other permitted metadata only.
- Clearly display provider permissions during connection and store only what the integration needs.
- Design deletion, export, revoke, and production changes with confirmations and heightened authorization.
- Redact secrets from error handling and test fixtures. Use synthetic values only.
- Fail closed when authorization, scope, or integrity cannot be established.

## Technology stack

The established application stack is:

| Area | Technology |
|---|---|
| Web application | React.js |
| Core services | Spring Boot microservices |
| Operational/security & AI services | Python / FastAPI |
| Relational data | PostgreSQL |
| Caching and asynchronous-work support | Redis |
| Local and deployable containers | Docker / Docker Compose |

Specific library choices, service names, identity provider, cloud provider, ORM, queue implementation, and build tooling are intentionally not prescribed here until selected by the team.

## Repository structure

The repository should make domain ownership and service boundaries visible. The following is the target structure, to be finalized as implementation begins:

```text
secretvault/
├── README.md
├── docs/
│   ├── PRODUCT.md
│   ├── ARCHITECTURE.md
│   ├── SECURITY.md
│   ├── DATABASE.md
│   ├── API.md
│   ├── INTEGRATIONS.md
│   ├── AI.md
│   ├── DEPLOYMENT.md
│   ├── TESTING.md
│   └── DECISIONS.md
├── apps/
│   └── web/                 # React application
├── services/
│   ├── ...spring-services/  # Spring Boot microservices, named when bounded contexts are chosen
│   └── intelligence/        # FastAPI service(s), when implemented
├── infrastructure/
│   └── docker/              # Compose and container configuration
├── tests/
│   ├── integration/
│   └── e2e/
└── .github/
    └── workflows/
```

Keep frontend responsibility concentrated in `apps/web/` (components and feature areas such as auth, dashboard, projects, environments, secrets, integrations, sync, security, audit, AI, team, and settings). Keep backend logic organized by independently owned bounded contexts rather than by UI page. Provider adapters must remain behind a stable interface so a new integration does not require rewriting core synchronization behavior.

## Local development

### Prerequisites

- Git
- Docker Desktop (including Docker Compose)
- A current Node.js runtime compatible with the selected React toolchain
- A JDK compatible with the selected Spring Boot version
- Python compatible with the selected FastAPI dependencies

### Start-up flow

1. Clone the repository and create a feature branch (see [Collaboration workflow](#collaboration-workflow)).
2. Copy the version-controlled environment template(s) to local, ignored configuration files.
3. Start local dependencies with the repository’s Docker Compose configuration once added.
4. Start the React app, Spring Boot services, and any FastAPI service using the commands documented beside each initialized application.
5. Run the relevant unit, integration, and end-to-end checks before opening a pull request.

The exact package-manager and build commands will be added with the initial application scaffolding. Avoid adding guessed commands to automation before those choices are committed.

### Environment configuration

Commit only templates such as `.env.example`; never commit populated `.env` files, credentials, encryption keys, provider tokens, session secrets, or real connection strings.

Environment templates should describe categories rather than ship credentials:

```dotenv
# Application/runtime identity and safe local settings
APP_ENV=development

# Local dependency endpoints (use non-production local values)
DATABASE_URL=
REDIS_URL=

# Security material — local development values only; never commit populated values
SECRET_ENCRYPTION_KEY=
AUTH_CONFIGURATION=

# Optional provider development credentials; leave blank unless required
PROVIDER_TOKEN=
```

Use a secret-sharing method approved by the team for real development credentials. Rotate any value that reaches a commit, log, chat, ticket, or public location; removing it from the latest file revision alone is not sufficient remediation.

### Docker Compose

Docker Compose is the local orchestration boundary for PostgreSQL, Redis, and initialized service containers. Its goals are repeatable onboarding, isolated local dependencies, health checks, non-production volumes, and configuration from ignored environment files.

The initial Compose file should define only the services that exist at that time. At minimum, it will provide PostgreSQL and Redis; web, Spring Boot, and FastAPI containers can be added as their applications become runnable. Do not embed live credentials in compose files, images, or build arguments.

## Collaboration workflow

This is a two-person team. Protect shared branches and make small, reviewable changes.

### Branching strategy

```text
main       production-ready, protected
  ↑
develop    shared integration and testing branch
  ↑
feature/*  focused work branches
```

- Never develop directly on `main`.
- Create `develop` from `main` once, then merge feature work into `develop` through pull requests.
- Promote tested `develop` changes to `main` through a separate pull request.
- Use focused names such as `feature/secrets`, `feature/sync-engine`, `feature/web-dashboard`, or `fix/audit-redaction`.
- Pull/rebase or merge the current integration branch before opening a PR, according to the team’s chosen Git policy; do not rewrite shared branch history.

### Ownership

To reduce conflicts, initially divide primary ownership by boundary:

- **Frontend owner:** React UI, reusable components, and feature screens.
- **Platform owner:** Spring Boot services, persistence, Redis-backed operational work, security controls, and integrations.

Both developers review security-sensitive changes and agree before changing shared contracts, schemas, permission models, encryption boundaries, provider adapters, or deployment configuration. Ownership is coordination, not an access-control boundary.

### Pull request and CI rules

- `main` requires pull requests, one approval, passing required checks, and no force pushes or direct pushes.
- `develop` should also use pull requests and passing checks.
- Keep PRs small, single-purpose, and linked to a clear issue or task.
- Require review by the other developer for security-sensitive or shared-boundary changes.
- CI should run formatting/linting, unit tests, relevant integration tests, build validation, and secret scanning once configured.
- Do not merge a failing, unreviewed, or unresolved-conflict PR just to unblock work.

## Testing and quality

Testing is part of the feature definition, particularly for authorization and safety behavior.

| Test layer | Focus |
|---|---|
| Unit | Domain rules, permissions, encryption-boundary behavior, redaction, validation, provider adapters |
| Integration | Database/Redis interactions, service contracts, queued sync behavior, audit events |
| End-to-end | Key user workflows: create/version/rotate, controlled reveal, mapping, sync, drift, and failure recovery |
| Security | Authorization regression tests, scope isolation, redaction checks, dependency and secret scanning |

Use synthetic fixtures. Tests must prove that unprivileged users cannot discover protected metadata or values, audit events contain no values, and failed synchronization cannot silently claim success.

## Observability

Observability should answer what changed, who initiated it, which request/job/provider was involved, and whether the action succeeded—without leaking secret material.

- Structured, redacted application logs with request and job correlation IDs.
- Metrics for sync volume, duration, queue health, retries, failures, drift counts, provider health, and authorization failures.
- Tracing across request → job → provider adapter where supported.
- Separate audit history for security-relevant user and system events.
- Alerts for sustained sync failures, provider connection loss, failed jobs, and critical security events.

## Deployment architecture

**Target architecture:** containerized React, Spring Boot, and FastAPI workloads operate alongside managed or isolated PostgreSQL and Redis. Background workers execute synchronization separately from the interactive request path. Provider calls leave through the adapter boundary, with credentials and encryption material supplied by an approved secret-management and key-management process.

Production deployment details—including cloud, hosting, identity, encryption-key management, networking, backups, disaster recovery, and monitoring vendors—remain decisions for the implementation team and must be documented before production use. Do not promote a local Compose setup as a production security architecture.

## MVP and roadmap

### MVP

1. Organization, project, and Development/Staging/Production model.
2. Authenticated, scoped RBAC foundations.
3. Encrypted secret creation, masked display, metadata, and version history.
4. Audit events for sensitive operations.
5. One provider adapter and explicit mapping flow.
6. Asynchronous synchronization, job status, classified failure, and safe retry.
7. Basic drift status based on safely observable provider state.
8. Focused dashboard and project/environment/secret views.

### After MVP

- Additional providers and provider-specific capability improvements.
- Advanced health, risk, leak, anomaly, and recommendation workflows.
- Notification channels, API-key management, and CLI.
- AI assistant, security summaries, and deployment RCA.
- Custom environments, enhanced imports, controlled exports, billing, and SaaS administration.

## Recommended development order

1. Write and agree on security, API, database, and architectural decisions.
2. Establish repository tooling, local dependency containers, CI, and safe configuration templates.
3. Implement organization/project/environment ownership and RBAC checks.
4. Implement encrypted secret versions and audit events before any provider synchronization.
5. Build one end-to-end provider adapter, mapping, job, verification, and failure path.
6. Deliver the core React workflows around those capabilities.
7. Add drift visibility, testing depth, observability, and operational hardening.
8. Extend to more providers and roadmap intelligence only after the core security boundary is proven.

## Contributing

1. Start from an up-to-date `develop` branch and create a focused `feature/*` or `fix/*` branch.
2. Read the relevant documentation in `docs/` before changing a security, domain, API, or integration boundary.
3. Keep values, credentials, and sensitive fixtures out of commits and discussion artifacts.
4. Add or update tests and documentation with behavior changes.
5. Run the applicable local checks and open a concise PR explaining intent, security impact, test evidence, and migration/deployment implications.
6. Address review feedback and merge only after the branch policy is satisfied.

Security issues should be reported privately to the project maintainers; do not publish active credentials or exploitable details in public issues.

## Project principles

- **Security before convenience.** A fast workflow is not acceptable if it weakens authorization, encryption, or auditability.
- **Centralized authority, explicit delivery.** SecretVault is authoritative; external mappings are intentional and observable.
- **Least privilege by default.** Access is scoped and production is treated as higher risk.
- **No silent state.** Changes, synchronization, drift, and failure should be visible and attributable.
- **Metadata over disclosure.** Make operations understandable without exposing values.
- **Human control over automation.** Intelligence recommends; authorized people approve consequential actions.
- **Small, testable increments.** Build the security-critical core before expanding integrations or AI capabilities.

---

SecretVault is intended to make the secure path the natural engineering workflow: one source of truth, explicit access, verified synchronization, and evidence for every important action.
