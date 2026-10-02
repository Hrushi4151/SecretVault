# Phase 9: Machine Identity & OIDC Workload Authentication

## Overview

SecretVault Phase 9 introduces **First-Class Machine Identities** and **Keyless OIDC Workload Authentication**, enabling secure, ephemeral, secretless authentication for CI/CD pipelines, containerized workloads, Kubernetes clusters, and automated microservices without long-lived static API keys.

Workloads authenticate by presenting cryptographic OpenID Connect (OIDC) JWT ID tokens issued by trusted identity providers (such as **GitHub Actions**, **GitLab CI/CD**, or **Generic RFC 7519 OIDC/OAuth2 Providers**). SecretVault cryptographically validates these tokens via cached JWKS keys, evaluates customizable **Trust Policies** and **Claim Rules**, and issues scoped, revocable, short-lived session tokens (`sv_machine_...`).

Machine authorization is seamlessly integrated into SecretVault's unified authorization engine (`EffectiveAccessService`), enforcing deterministic `DENY > ALLOW` precedence with zero implicit admin privileges and full governance auditing.

---

## Key Features

1. **First-Class Machine Identities**
   - Full lifecycle management (Create, List, Update, Disable, Enable, Expire, Revoke, Soft Delete).
   - Dedicated identity types (`CI_CD`, `KUBERNETES`, `SERVICE`, `CUSTOM`).
   - Granular RBAC and scoped access grants (`WORKSPACE`, `PROJECT`, `ENVIRONMENT`, `SECRET`).

2. **OIDC Workload Identity Federation**
   - Native support for **GitHub Actions** (`https://token.actions.githubusercontent.com`).
   - Native support for **GitLab CI** (`https://gitlab.com` or self-hosted).
   - Support for **Generic RFC 7519 OIDC Providers** (e.g., Vault, Okta, Keycloak, AWS IAM OIDC, Google Cloud Workload Identity).
   - SSRF-safe HTTP client for discovery and JWKS endpoints (blocking private IPs, localhost, AWS metadata `169.254.169.254`, GCP/Azure metadata, link-local, loopback).
   - Automatic OIDC Discovery (`/.well-known/openid-configuration`) and memory-cached JWKS rotation with circuit-breaker fail-safes.

3. **Visual Trust Policy Builder & Claim Engine**
   - Dynamic policy matching with claim extraction adapters (`sub`, `aud`, `iss`, `repository`, `ref`, `actor`, `workflow`, `project_path`, `environment`, etc.).
   - Support for multiple operators: `EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`, `PREFIX`, `SUFFIX`, `CONTAINS`, `REGEX`.
   - Real-time natural language summary generation for human-readable auditability.

4. **Short-Lived Ephemeral Sessions**
   - Token exchange endpoint: `POST /api/v1/auth/oidc/token`.
   - Generates high-entropy opaque bearer tokens prefixed with `sv_machine_`.
   - SHA-256 hashed storage in `machine_sessions` (zero plaintext tokens in database).
   - Enforced session TTL with active IP and User-Agent tracking, plus instant session revocation.

5. **Single Unified Authorization Engine**
   - Zero parallel authorization engines: Evaluated directly by `EffectiveAccessService` alongside human members.
   - Zero implicit `ADMIN` or `OWNER` permissions for machines.
   - Strict `DENY > ALLOW` deterministic hierarchy.
   - Security Center risk detection (`MachineIdentityRiskRule`, `OverlyBroadTrustPolicyRule`).
   - Access Review governance & WhyAccess lineage tracking.

6. **CLI & Web UI Integration**
   - Full CLI support: `secretvault auth oidc --provider-id ... --token ...`, `secretvault machine list`, `secretvault machine get`, `secretvault machine status`.
   - Modern, responsive React dashboard for Machine Identity management, Visual Trust Policy editor, Grant manager, Session inspector, and Provider configuration.

---

## Documentation Index

- [Architecture Guide](file:///Users/nimisha/Downloads/SecretVault-main/docs/phase9/ARCHITECTURE.md)
- [Machine Identities Lifecycle](file:///Users/nimisha/Downloads/SecretVault-main/docs/phase9/MACHINE_IDENTITIES.md)
- [OIDC Subsystem & JWKS Validation](file:///Users/nimisha/Downloads/SecretVault-main/docs/phase9/OIDC.md)
- [GitHub Actions Integration Guide](file:///Users/nimisha/Downloads/SecretVault-main/docs/phase9/GITHUB_ACTIONS.md)
- [GitLab CI Integration Guide](file:///Users/nimisha/Downloads/SecretVault-main/docs/phase9/GITLAB_CI.md)
- [Generic OIDC Providers](file:///Users/nimisha/Downloads/SecretVault-main/docs/phase9/GENERIC_OIDC.md)
- [Trust Policies & Claim Rules](file:///Users/nimisha/Downloads/SecretVault-main/docs/phase9/TRUST_POLICIES.md)
- [Security & Threat Mitigation](file:///Users/nimisha/Downloads/SecretVault-main/docs/phase9/SECURITY.md)
- [REST API Reference](file:///Users/nimisha/Downloads/SecretVault-main/docs/phase9/API.md)
- [Troubleshooting & Diagnostics](file:///Users/nimisha/Downloads/SecretVault-main/docs/phase9/TROUBLESHOOTING.md)
- [Threat Model & Security Invariants](file:///Users/nimisha/Downloads/SecretVault-main/docs/phase9/THREAT_MODEL.md)
- [Operations & Runbook](file:///Users/nimisha/Downloads/SecretVault-main/docs/phase9/OPERATIONS.md)
