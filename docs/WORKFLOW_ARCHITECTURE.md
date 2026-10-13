# SecretVault — End-to-End System & Workflow Architecture

> **SecretVault** is an enterprise-grade DevSecOps platform and Secrets Management control plane built as a **Spring Boot 3 (Java 21) Modular Monolith** with zero-trust envelope encryption, immutable audit chains, and automated multi-cloud synchronization.

---

## 1. High-Level System Architecture Topology

The diagram below visualizes the three-tier architectural layout: **Client & Developer Surface**, the **Spring Boot Control Plane (Modular Monolith)**, and the **Persistence & Cloud Provider Fabric**.

![SecretVault Platform End-to-End Architecture](images/secretvault_arch_flow.jpg)

### Core Architectural Layers

```mermaid
graph TB
    subgraph ClientLayer["1. Client & Integration Surface"]
        CLI["SecretVault CLI (secretvault run / inject)"]
        Dashboard["Next.js / React Web Dashboard"]
        VSCode["VS Code Developer Extension"]
        SDK["Multi-Language SDKs (Java, Node, Python, Go)"]
        CICD["CI/CD Workloads (GitHub Actions, GitLab CI)"]
    end

    subgraph ControlPlane["2. Spring Boot 3 Control Plane (Modular Monolith)"]
        direction TB
        subgraph CoreSecurity["Security & Core Governance"]
            Auth["Auth & MFA (WebAuthn / TOTP)"]
            Org["Tenant & Workspace Isolation"]
            RBAC["Granular RBAC & JIT Scoping"]
            Audit["Cryptographic Audit Ledger"]
        end
        subgraph SecretCore["Cryptographic Secrets Engine"]
            SecMgr["Secret Lifecycle Engine"]
            Envelope["AES-256-GCM Envelope Encryption"]
            KMS["KMS Key Provider SPI (AWS KMS / HSM)"]
        end
        subgraph PlatformSync["Multi-Cloud Sync & Platform"]
            SyncWorker["Redis Queue Sync Worker"]
            ProviderSPI["Provider SPI (AWS, Vercel, CF, K8s)"]
            Drift["Drift Detection Scanner"]
            AIBridge["AI Advisory Gateway (FastAPI)"]
        end
    end

    subgraph PersistenceLayer["3. Data & Persistence Fabric"]
        Postgres[(PostgreSQL 16: Encrypted Secrets & Ledger)]
        Redis[(Redis 7: Locks, Tokens & Sync Queues)]
    end

    subgraph CloudTargets["4. External Infrastructure Providers"]
        AWS["AWS Secrets Manager / SSM"]
        Vercel["Vercel Environment API"]
        CF["Cloudflare Workers Secrets"]
        K8s["Kubernetes External Secrets / CRD"]
    end

    ClientLayer -->|TLS 1.3 / mTLS REST| ControlPlane
    ControlPlane --> Postgres
    ControlPlane --> Redis
    SyncWorker --> ProviderSPI
    ProviderSPI --> AWS
    ProviderSPI --> Vercel
    ProviderSPI --> CF
    ProviderSPI --> K8s
    AIBridge -.->|Sanitized Metadata Only| FastAPI["Python FastAPI AI Copilot"]
```

---

## 2. Cryptographic Envelope Encryption & Secret Reveal Workflow

SecretVault implements zero-plaintext guarantees using authenticated hardware-grade envelope encryption (**AES-256-GCM**) with **Authenticated Additional Data (AAD)** context binding and pluggable Key Management Services (**AWS KMS / HSM**).

![Cryptographic Envelope Encryption & Secret Reveal Lifecycle](images/secretvault_crypto_flow.jpg)

### Sequence Flow: Secret Ingestion vs. Decryption Reveal

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer / Workload
    participant API as SecretController
    participant Core as SecretService
    participant Crypto as EnvelopeEncryptionService
    participant KMS as KmsKeyProvider (AWS / Vault)
    participant DB as PostgreSQL 16 (secret_versions)
    participant Ledger as AuditLedgerService

    Note over Dev, DB: 🔐 Secret Provisioning & Envelope Encryption Flow
    Dev->>API: POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets
    API->>Core: createSecret(name, plaintextPayload)
    Core->>Core: Validate Workspace Role & Scoped Permissions
    Core->>Crypto: encrypt(plaintextBytes, "secretId:envId:vNum")
    Crypto->>Crypto: SecureRandom: Ephemeral 256-bit DEK + 96-bit IV
    Crypto->>Crypto: AES-256-GCM Encrypt with AAD Context Binding
    Crypto->>KMS: wrapKey(plaintextDek, KeyRef)
    KMS-->>Crypto: wrappedDekBytes
    Crypto-->>Core: EncryptedPayload (ciphertext, wrappedDek, iv, authTag)
    Core->>DB: INSERT secrets + INSERT secret_versions (v1)
    Core->>Ledger: recordEvent(SECRET_CREATED, callerPrincipal, hashChain)
    Core-->>API: SecretMetadataResponse (ZERO Plaintext)
    API-->>Dev: 201 Created (ID, Version, Metadata)

    Note over Dev, DB: 👁️ Zero-Trust Secret Reveal (Step-Up Challenge)
    Dev->>API: POST /api/v1/.../secrets/{id}/reveal
    API->>Core: revealSecret(secretId, authContext)
    Core->>Core: Require Recent Step-Up MFA Challenge (< 5m)
    Core->>DB: SELECT current encrypted version
    Core->>Crypto: decrypt(payload, AAD)
    Crypto->>KMS: unwrapKey(wrappedDek, KeyRef)
    KMS-->>Crypto: plaintextDek
    Crypto->>Crypto: AES-256-GCM Decrypt & Validate 128-bit Auth Tag
    Crypto-->>Core: plaintextBytes
    Core->>Ledger: recordEvent(SECRET_REVEALED, callerPrincipal, IP, Device)
    Core-->>API: SecretRevealResponse
    API-->>Dev: 200 OK (Cache-Control: no-store, no-cache, private)
```

---

## 3. Multi-Cloud Synchronization & Drift Detection Engine

SecretVault propagates secret mutations across cloud providers asynchronously via Redis queues and continuously reconciles discrepancies via autonomous drift scans.

![DevSecOps Multi-Cloud Secret Synchronization & Drift Detection](images/secretvault_sync_flow.jpg)

### Synchronization & Auto-Remediation Pipeline

```mermaid
flowchart LR
    subgraph Trigger["1. Mutation Trigger"]
        A["Developer Web / CLI / GitOps"] --> B["Secret Mutation Committed"]
    end

    subgraph AsyncQueue["2. Resilient Worker"]
        B --> C["Redis Pub/Sub Sync Queue"]
        C --> D["SyncWorkerDaemon\n(Exponential Backoff & Dead-Letter Queue)"]
    end

    subgraph Dispatch["3. Provider SPI Dispatch"]
        D --> E{"Provider SPI"}
        E -->|Adapter| F1["AWS Secrets Manager"]
        E -->|Adapter| F2["Vercel Environment API"]
        E -->|Adapter| F3["Cloudflare Workers Secrets"]
        E -->|Adapter| F4["Kubernetes External Secrets CRD"]
    end

    subgraph DriftLoop["4. Continuous Attestation & Drift Engine"]
        G["Scheduled Cron / Webhook"] --> H["Drift Detection Scanner"]
        H -->|Compare SHA-256 Fingerprint| I{"Drift Detected?"}
        I -->|No| J["Attestation Certified (SOC 2)"]
        I -->|Yes| K["AI Root Cause Analysis & Auto-Reconcile"]
        K --> C
    end
```

---

## 4. Multi-Tier Access Scoping & Zero-Trust Governance

SecretVault enforces strict tenant boundaries using an intersection formula to eliminate Privilege Escalation:

$$\text{Effective Permission} = \text{Workspace Role} \cap \text{Project Scope} \cap \text{Environment Scope} \cap \text{Security Guardrails}$$

| Access Tier | Container | Supported Roles / Grants | Security Invariants |
|---|---|---|---|
| **Organization** | Root Enterprise Tenant | Org Admin, Member | Billing, SSO, SCIM directories, global policy defaults |
| **Workspace** | Isolation Boundary | `OWNER`, `ADMIN`, `DEVELOPER`, `VIEWER` | Invitation tokens, member offboarding, baseline permissions |
| **Project** | Microservice Scope | `ProjectAccess` (Inherited or Constrained) | Restricts developer access to specific services only |
| **Environment** | Runtime Target | `READ`, `WRITE`, `MANAGE` | `is_protected = true` flags require JIT approval + MFA |

---

## 5. Authoritative Domain & Package Responsibilities

| Domain Package | Layer | Core Responsibilities | Key Classes & Services |
|---|---|---|---|
| `com.secretvault.auth` | Security | JWT authentication, WebAuthn FIDO2, TOTP MFA, step-up challenges | `AuthController`, `MfaService` |
| `com.secretvault.workspace` | Governance | Multi-tenant organization, workspace provisioning, SCIM membership | `WorkspaceService`, `MembershipRepository` |
| `com.secretvault.project` | Scoping | Application and microservice containers, scoped permission grants | `ProjectService`, `ProjectAccessRepository` |
| `com.secretvault.environment` | Runtime | Tier isolation (dev, staging, prod), protection policies | `EnvironmentService`, `EnvironmentProtectionRule` |
| `com.secretvault.encryption` | Cryptography | AES-256-GCM envelope encryption, DEK derivation, KMS/HSM integration | `EnvelopeEncryptionService`, `AwsKmsKeyProvider` |
| `com.secretvault.secret` | Secrets Core | CRUD, immutable version ledger, masking, diff engine, rollbacks | `SecretService`, `SecretVersionRepository` |
| `com.secretvault.provider` | Cloud SPI | Pluggable cloud adapters (AWS, Vercel, Cloudflare, Kubernetes) | `SecretProvider`, `AwsSecretsManagerProvider` |
| `com.secretvault.sync` | Pipelines | Redis-backed asynchronous synchronization workers, drift detection | `SyncQueueWorker`, `DriftDetectionService` |
| `com.secretvault.audit` | Compliance | SHA-256 tamper-evident hash-chained event ledger, SOC 2 exports | `AuditLedgerService`, `AuditLogRepository` |
| `com.secretvault.security` | Intel | Secret leak scanning, blast radius analysis, JIT approvals | `RiskCenterService`, `BlastRadiusGraph` |
| `com.secretvault.ai` | Intelligence | Outbound boundary to FastAPI AI Co-pilot (zero-plaintext) | `AiSecurityClient`, `AiCopilotService` |
