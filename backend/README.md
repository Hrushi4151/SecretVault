<div align="center">

# ⚙️ SecretVault Backend Control Plane

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)

**The core REST API, cryptographic secrets engine, and multi-cloud orchestration control plane.**

</div>

---

## 🏛️ Architectural Overview

The **SecretVault Backend** is architected as a **Modular Monolith** in Java 21 on Spring Boot 3.3. It isolates domain boundaries into clean internal packages while operating within a single high-performance deployment unit.

### Core Domain Modules

```
com.secretvault
├── ai/             # AI Security Copilot: Zero-plaintext firewall, multi-step agent orchestrator, tools
├── audit/          # Tamper-evident SHA-256 hash-chained compliance audit ledger
├── auth/           # BCrypt, WebAuthn FIDO2, TOTP RFC-6238, JWT tokens, Step-Up challenges
├── automation/     # Event-driven policy automation rules engine and infinite loop guard
├── common/         # Rate limiting (Redis sliding window), global error handler, caching
├── encryption/     # NIST SP 800-57 AES-256-GCM envelope encryption & KMS Key Provider SPI
├── environment/    # Environment protection policies (is_protected) & scoping
├── incident/       # Security Incident Lifecycle (INC-####) & auto-containment
├── machine/        # Machine Identities, Service Accounts & Keyless OIDC Federation
├── project/        # Microservice project scoping & effective access evaluation
├── provider/       # Multi-Cloud Provider SPI (AWS Secrets Manager/SSM, Vercel, Cloudflare, K8s)
├── repository/     # Git repository scanning, secret leak detection & GitHub push ingress
├── secret/         # Secret Core: CRUD, version ledger, rollback, and masked reveal
├── security/       # Risk Center, Posture Score (0-100) & dynamic blast radius computation
├── sync/           # Asynchronous Redis sync worker queue, retry backoff, and drift detection
├── webhook/        # Outbound HMAC-SHA256 signed webhooks with strict anti-SSRF defenses
└── workspace/      # Multi-tenant tenant boundaries, memberships, and invitations
```

---

## 🔒 Security Invariants & Guarantees

1. **Zero-Plaintext Storage:** Every secret version is encrypted at rest using AES-256-GCM with ephemeral 256-bit DEKs and 96-bit IVs.
2. **KMS / HSM Key Wrapping:** Master Key Encryption Keys (KEKs) wrap DEKs via AWS KMS or PBKDF2 Master Derivation.
3. **Authenticated Additional Data (AAD):** Cryptographically binds ciphertext to `secretId:envId:versionNumber` to eliminate replay and transposition attacks.
4. **Fail-Closed Rate Limiting:** All authentication and scanning endpoints enforce sliding-window Redis rate limiters that fail closed on network partition.
5. **Zero-Plaintext AI Boundary:** Inbound queries, tool parameters, tool outputs, and LLM responses pass through `AiSecretFirewall` for SHA-256 credential redaction.

---

## 🛠️ Configuration & Environment

The application is configured through `application.yml` and environment variables:

| Variable | Description | Default / Example |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC Connection URL | `jdbc:postgresql://localhost:5432/secretvault` |
| `DB_USERNAME` | PostgreSQL User | `secretvault` |
| `DB_PASSWORD` | PostgreSQL Password | `secretvault_dev_password` |
| `REDIS_URL` | Redis Connection URI | `redis://localhost:6379` |
| `JWT_SECRET` | 256-bit signing key for JWTs | *(Required in production)* |
| `ENCRYPTION_PROVIDER` | Master Key Provider (`PBKDF2` or `AWS_KMS`) | `PBKDF2` |
| `ENCRYPTION_MASTER_PASSWORD` | Master password for PBKDF2 KEK derivation | *(Required for PBKDF2)* |
| `GEMINI_API_KEY` | Google Gemini API Key for AI Copilot | *(Optional, enables cloud AI)* |

---

## 🧪 Testing & Verification

The test suite contains hundreds of unit, integration, and security tests:

```bash
# Run all tests
./mvnw test

# Run security & attack scenario tests only
./mvnw test -Dtest=*SecurityTest,*AttackTest

# Run multi-cloud sync integration tests
./mvnw test -Dtest=*Sync*Test

# Run AI Copilot integration tests
./mvnw test -Dtest=*Ai*Test
```
