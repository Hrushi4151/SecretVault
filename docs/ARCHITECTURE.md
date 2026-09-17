# SecretVault — System Architecture Design

## 1. Architectural Strategy: Modular Monolith

SecretVault is implemented as a **Modular Monolith** using **Spring Boot 3 (Java 21)**. This ensures high velocity, atomic transactional boundaries, type safety, and zero internal network latency for a two-developer team, while enforcing strict domain decoupling.

```mermaid
graph TB
    subgraph ClientLayer["Client & Integration Layer"]
        ReactUI["React Web Dashboard (126 Stitch Screens)"]
        CLI["SecretVault CLI (secretvault run)"]
        VSCode["VS Code Extension"]
        SDK["Client SDKs (Java, Node, Python, Go)"]
        CI["CI/CD Workloads (GitHub Actions / GitLab CI)"]
    end

    subgraph ControlPlane["SecretVault Spring Boot Control Plane (Modular Monolith)"]
        subgraph CorePlatform["Core Platform & Security Domains (Member 1)"]
            Auth["auth (JWT / Sessions / MFA)"]
            Org["organization / workspace (Invitations / Lifecycle)"]
            Proj["project (Scoped Access / Policies)"]
            Env["environment (Scoped Access / Protection)"]
            SecEngine["secret (CRUD / Versions / Diff / Rollback)"]
            Crypto["encryption (AES-256-GCM / Envelope / KMS)"]
            Access["access (RBAC / Inheritance / Scoping)"]
            Audit["audit (Immutable Event Ledger)"]
            SecIntel["security (Risk / Leaks / Blast Radius)"]
        end

        subgraph IntegrationPlatform["Integrations & Dev Platform (Member 2)"]
            Integ["integration (Connections / Mappings)"]
            ProviderSPI["provider (SecretProvider SPI)"]
            SyncEng["sync (Async Queue / Drift / Retries)"]
            Runtime["runtime (In-Memory Process Injection)"]
            Ident["identity (Service Accounts / OIDC)"]
            CICD["cicd (Pipeline Gates)"]
            Tmpl["template (Config Schemas)"]
            Deploy["deployment / health"]
            AIClient["ai (FastAPI Client Boundary)"]
        end
    end

    subgraph DataStorage["Persistence & Cache Layer"]
        Postgres[(PostgreSQL 16\nEncrypted Metadata & Audit)]
        RedisCache[(Redis 7\nQueue, Locks & Rate Limits)]
    end

    subgraph AIServiceLayer["Independent AI Service"]
        FastAPI["Python / FastAPI AI Co-pilot\n(Advisory RCA & Risk Analysis)"]
    end

    subgraph ExternalProviders["Target Infrastructure Providers"]
        AWS["AWS Secrets Manager / SSM"]
        Vercel["Vercel Environment API"]
        Railway["Railway API"]
        GitHub["GitHub Actions Secrets"]
        K8s["Kubernetes Clusters (ESO / CRDs)"]
    end

    ClientLayer -->|REST / TLS 1.3| ControlPlane
    ControlPlane --> Postgres
    ControlPlane --> RedisCache
    AIClient -.->|Sanitized Metadata Only (No Plaintext)| FastAPI
    SyncEng --> ProviderSPI
    ProviderSPI --> AWS
    ProviderSPI --> Vercel
    ProviderSPI --> Railway
    ProviderSPI --> GitHub
    ProviderSPI --> K8s
```

---

## 2. Multi-Tier Access Scoping & Inheritance Model [IMPLEMENTED]

SecretVault enforces the fundamental security principle: **Role $\neq$ Scope**.

```text
User Principal
     ↓
Organization Membership
     ↓
Workspace Membership (Role: OWNER | ADMIN | DEVELOPER | VIEWER)
     ↓
Project Scope Grant (ProjectAccess: Scoped Role)
     ↓
Environment Scope Grant (EnvironmentAccess: PermissionLevel READ | WRITE | MANAGE)
     ↓
Effective Permission Evaluation
```

### Effective Permission Formula:
$$\text{Effective Permission} = \text{Workspace Role} \cap \text{Project Scope} \cap \text{Environment Scope} \cap \text{Security Policies}$$

- **Permission Reduction / Restriction:** A child grant (ProjectAccess or EnvironmentAccess) may further restrict a user's permissions within that specific boundary.
- **Elevation Blocked:** A child grant cannot exceed the user's workspace-level role permissions (e.g., a `VIEWER` at the workspace level assigned `WRITE` in an environment still receives `READ` as effective permission).

---

## 3. Authoritative Multi-Tenant Hierarchy [IMPLEMENTED]

SecretVault strictly enforces a 4-tier tenant hierarchy:

```text
Organization (Global Tenant Container)
    │
    └── Workspace (Isolation Boundary, Invitations, & Member Governance)
            │
            ├── Project (Application / Microservice Scope)
            │      │
            │      ├── Environment (development)
            │      ├── Environment (staging)
            │      └── Environment (production [is_protected = true])
            │
            └── Project
```

### Hierarchy Validation & IDOR Prevention Rules:
1. **Never Trust Client-Supplied Composite IDs:** A client request containing `workspaceId`, `projectId`, and `environmentId` is NEVER trusted implicitly.
2. **Server-Side Ownership Verification:**
   - The backend checks `WorkspaceMembership(workspaceId, userId)` to establish caller authorization and RBAC role.
   - The backend verifies `Project.workspaceId == workspaceId`.
   - The backend verifies `Environment.projectId == projectId`.
3. **Mismatched Path ID Rejection:** If a valid `environmentId` from Project A is queried under `/projects/{projectB}/environments/{envA}`, the server immediately aborts with `404 RESOURCE_NOT_FOUND` (preventing IDOR enumeration).
4. **Cross-Tenant Request Rejection:** Any cross-workspace request is blocked with `403 FORBIDDEN`.

---

## 4. Domain Package Boundaries (`com.secretvault.*`)

| Domain Package | Primary Responsibility | Primary Owner | Status |
|---|---|---|---|
| `common` | Exceptions, RFC-7807 error handler, OpenAPI, Redis, Security filter chain, CorrelationIdFilter | Shared | 🟢 IMPLEMENTED |
| `auth` | User identity, JWT issuance, password reset, sessions, MFA | Member 1 | 🟢 IMPLEMENTED |
| `workspace` | Organization and workspace lifecycle, tenant membership, invitations, settings | Member 1 | 🟢 IMPLEMENTED |
| `project` | Project container lifecycle, scoped member access, environments auto-provisioning | Member 1 | 🟢 IMPLEMENTED |
| `environment` | Scoped access tiers, dev/staging/prod management, protection rules | Member 1 | 🟢 IMPLEMENTED |
| `secret` | Secret CRUD, immutable versioning, masking, rollback, batch import | Member 1 | 🟢 IMPLEMENTED |
| `encryption` | AES-256-GCM envelope encryption, DEK generation, AAD context binding, KMS/HSM integration | Member 1 | 🟢 IMPLEMENTED |
| `access` | Granular RBAC, JIT access requests, approval workflows, access review campaigns | Member 1 | 🟢 IMPLEMENTED (RBAC Scope) |
| `audit` | Append-only audit ledger, event emission, compliance logs | Member 1 | 🟢 IMPLEMENTED |
| `security` | Risk Center, secret leak scanner, blast radius graph, security policies | Member 1 | ⚪ PLANNED |
| `integration` | Provider connection credentials, platform mapping definitions | Member 2 | ⚪ PLANNED |
| `provider` | `SecretProvider` SPI and adapters (AWS, Vercel, Railway, GitHub, K8s) | Member 2 | ⚪ PLANNED |
| `sync` | Asynchronous sync queue worker, retry engine, drift detection | Member 2 | ⚪ PLANNED |
| `runtime` | SDK runtime secret endpoints, in-memory CLI hydration | Member 2 | ⚪ PLANNED |
| `identity` | Machine identities, Service Accounts, Workload Identity Federation (OIDC) | Member 2 | ⚪ PLANNED |
| `cicd` | CI/CD pipeline gates, PR scanning integration, headless auth | Member 2 | ⚪ PLANNED |
| `template` | Reusable secret templates, configuration generators | Member 2 | ⚪ PLANNED |
| `deployment` | Cloud and self-hosted deployment profiles, cluster connectivity | Member 2 | ⚪ PLANNED |
| `health` | Public health probes, Actuator observability indicators | Member 2 | 🟢 IMPLEMENTED |
| `ai` | Outbound communication boundary to Python/FastAPI AI co-pilot | Member 2 | ⚪ PLANNED |

---

## 5. Cryptographic Envelope Encryption & Secret Lifecycle Engine [IMPLEMENTED]

SecretVault implements hardware-grade envelope encryption with AEAD authenticated cipher suites and zero-plaintext guarantees:

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Client / Developer
    participant Ctrl as SecretController
    participant Svc as SecretService
    participant Enc as EnvelopeEncryptionService
    participant KMS as KmsKeyProvider
    participant DB as PostgreSQL (secrets, versions, audit)

    Note over Dev, DB: Secret Creation & Version Mutation Flow
    Dev->>Ctrl: POST .../secrets (Plaintext Value + Name)
    Ctrl->>Svc: createSecret(workspaceId, projId, envId, req)
    Svc->>Svc: Validate RBAC & Path Hierarchy
    Svc->>Enc: encrypt(plaintextBytes, "secretId:envId:versionNum")
    Enc->>Enc: Generate random 256-bit DEK & 96-bit IV
    Enc->>Enc: AES-256-GCM Encrypt with AAD binding
    Enc->>KMS: wrapKey(plaintextDek, keyReference)
    KMS-->>Enc: encryptedDekBytes
    Enc-->>Svc: EncryptedPayload (ciphertext, encryptedDek, iv, authTag)
    Svc->>DB: INSERT secrets + INSERT secret_versions (v1)
    Svc->>DB: INSERT audit_logs (SECRET_CREATED)
    Svc-->>Ctrl: SecretMetadataResponse (zero plaintext)
    Ctrl-->>Dev: 201 Created (Metadata Only)

    Note over Dev, DB: Explicit Secret Reveal Flow
    Dev->>Ctrl: POST .../secrets/{id}/reveal
    Ctrl->>Svc: revealSecret(workspaceId, projId, envId, secretId)
    Svc->>DB: SELECT current secret_version
    Svc->>Enc: decrypt(payload, "secretId:envId:versionNum")
    Enc->>KMS: unwrapKey(encryptedDek, keyReference)
    KMS-->>Enc: plaintextDek
    Enc->>Enc: AES-256-GCM Decrypt & Validate 128-bit Auth Tag
    Enc-->>Svc: plaintextBytes
    Svc->>DB: INSERT audit_logs (SECRET_REVEALED)
    Svc-->>Ctrl: SecretRevealResponse (Plaintext)
    Ctrl-->>Dev: 200 OK (Cache-Control: no-store, no-cache)
```

### Core Cryptographic Invariants:
1. **Fresh DEK and IV Per Version:** Each encryption generates an ephemeral 256-bit AES key and a 96-bit nonce via `java.security.SecureRandom`. DEKs are never reused across versions or secrets.
2. **Authenticated Additional Data (AAD) Context Binding:** Ciphertexts are cryptographically bound to the tuple `secretId:environmentId:versionNumber`. Swapping ciphertext into another environment or version immediately breaks AEAD tag validation.
3. **Pluggable Master Key Provider (`KmsKeyProvider`):** Abstracted key wrapping SPI supporting local AES-KW / AES-GCM wrapping in development (`LocalDevKmsKeyProvider`) and AWS KMS / HashiCorp Vault HSM in production.
4. **Immutable Version Ledger:** Secret values are write-once read-many (`secret_versions`). Updates increment `current_version_number` and insert a new version row. Historical versions remain intact for rollback and compliance.
5. **No-Store HTTP Delivery:** Secret reveal endpoints return explicit HTTP `Cache-Control: no-store, no-cache, must-revalidate, private` and `Pragma: no-cache` headers to prevent proxy, CDN, or browser disk caching.

