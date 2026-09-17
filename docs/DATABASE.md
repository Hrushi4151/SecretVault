# SecretVault — Database Architecture & Schema Design

## 1. Storage Overview

- **Primary Relational Store:** PostgreSQL 16
- **Schema Management:** Flyway Migrations (`backend/src/main/resources/db/migration/`)
- **Cache & Ephemeral Queue:** Redis 7

---

## 2. Core Relational Schema Principles

1. **Zero Plaintext Secrets:** Plaintext secret values MUST NEVER be persisted in PostgreSQL. Secret columns store encrypted ciphertext bytes, encrypted DEK tokens, initialization vectors (IV), and auth tags.
2. **Flyway Migration Standards:**
   - Every schema mutation requires an incremental migration: `V<Version>__<Description>.sql`.
   - Never alter, remove, or rename an applied Flyway migration.
   - Migrations must be strictly backward-compatible.
3. **Multi-Tenancy Indexing:**
   - Every tenant-scoped entity (`projects`, `environments`, `secrets`, `audit_logs`) MUST contain `organization_id` and `workspace_id`.
   - Composite indexes must be applied: `(organization_id, id)` and `(organization_id, created_at)`.
### Applied Flyway Migrations:
- `V1__init_baseline.sql` — Baseline initialization
- `V2__auth_and_workspaces_schema.sql` — Users, organizations, workspaces, memberships, and refresh tokens
- `V3__projects_and_environments_schema.sql` — Projects and environments tables with UUID primary keys, foreign keys, unique slug constraints, and indexes

---

## 3. Conceptual Entity-Relationship Model [PLANNED]

```mermaid
erDiagram
    ORGANIZATION ||--o{ WORKSPACE : contains
    ORGANIZATION ||--o{ USER_MEMBERSHIP : employs
    WORKSPACE ||--o{ PROJECT : contains
    PROJECT ||--o{ ENVIRONMENT : defines
    ENVIRONMENT ||--o{ SECRET : owns
    SECRET ||--|{ SECRET_VERSION : tracks
    ENVIRONMENT ||--o{ PROVIDER_MAPPING : maps
    ORGANIZATION ||--o{ PROVIDER_CONNECTION : configures
    ENVIRONMENT ||--o{ SYNC_JOB : executes
    ORGANIZATION ||--o{ AUDIT_LOG : records
    PROJECT ||--o{ SERVICE_ACCOUNT : grants
    ORGANIZATION ||--o{ SECURITY_POLICY : enforces
    PROJECT ||--o{ SECURITY_FINDING : detects

    ORGANIZATION {
        uuid id PK
        string name
        string slug
        string status
        timestamp created_at
    }

    WORKSPACE {
        uuid id PK
        uuid organization_id FK
        string name
        string slug
        timestamp created_at
    }

    PROJECT {
        uuid id PK
        uuid workspace_id FK
        string name
        string slug
        string description
        string status "ACTIVE | ARCHIVED"
        uuid created_by FK
        timestamp created_at
        timestamp updated_at
    }

    ENVIRONMENT {
        uuid id PK
        uuid project_id FK
        string name
        string slug
        string env_type "DEVELOPMENT | STAGING | PRODUCTION"
        string description
        boolean is_protected
        string status "ACTIVE | ARCHIVED"
        uuid created_by FK
        timestamp created_at
        timestamp updated_at
    }

    SECRET {
        uuid id PK
        uuid organization_id FK
        uuid environment_id FK
        string name
        string description
        string secret_type
        integer current_version
        timestamp created_at
        timestamp updated_at
    }

    SECRET_VERSION {
        uuid id PK
        uuid secret_id FK
        integer version_number
        bytea encrypted_payload
        bytea encrypted_dek
        bytea iv_nonce
        bytea auth_tag
        string kms_key_id
        uuid created_by_actor_id
        string change_reason
        timestamp created_at
    }

    AUDIT_LOG {
        uuid id PK
        uuid organization_id FK
        uuid workspace_id FK
        uuid actor_id
        string actor_type "USER | SERVICE_ACCOUNT | SYSTEM"
        string action
        string resource_type
        string resource_id
        string request_id
        string ip_address
        string outcome "SUCCESS | FAILURE | DENIED"
        timestamp created_at
    }
```

---

## 4. Encryption Metadata Columns

Every secret version table record stores cryptographic envelope components:

| Column Name | Type | Purpose |
|---|---|---|
| `encrypted_payload` | `BYTEA` | Ciphertext produced by AES-256-GCM |
| `encrypted_dek` | `BYTEA` | Unique Data Encryption Key encrypted with Master KEK |
| `iv_nonce` | `BYTEA` | 96-bit unique Initialization Vector (IV) |
| `auth_tag` | `BYTEA` | 128-bit authentication tag validating ciphertext integrity |
| `kms_key_id` | `VARCHAR(255)` | Identifier or ARN of the Key Encryption Key (KEK) |

---

## 5. Audit Log Retention & Immutability

- `audit_logs` records are **append-only**.
- PostgreSQL permissions for application users must omit `UPDATE` and `DELETE` privileges on `audit_logs`.
- Minimum retention: 365 days for standard tiers; 7 years for enterprise compliance tiers.

---

## 6. Redis Usage & TTL Policy

| Key Pattern | Purpose | TTL |
|---|---|---|
| `ratelimit:{tenant}:{ip}` | API Rate limiting (sliding window) | 60 seconds |
| `sync:lock:{env_id}:{provider_id}` | Distributed mutex for external provider synchronization | 5 minutes |
| `jit:grant:{grant_id}` | Active ephemeral Just-In-Time access grant token | 1–8 hours |
| `token:blacklist:{jti}` | Revoked JWT tokens | Remaining token lifespan |
