# Changelog

All notable changes to the **SecretVault** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Planned (Upcoming Milestone: Phase 1)
- User registration, login, and secure JWT session issuance with refresh tokens.
- Multi-tenant Organization and Workspace entities with workspace switching API.
- Team member invitation flow and baseline RBAC roles (`OWNER`, `ADMIN`, `DEVELOPER`, `VIEWER`).

---

## [0.1.0-SNAPSHOT] - 2026-09-16

### Added
- **Project Specifications & Comprehensive Documentation**:
  - `README.md` (Product overview, architecture, quickstart)
  - `AI_RULES.md` (Mandatory AI collaboration and engineering rules)
  - `PROJECT_STATUS.md` (Detailed phase-by-phase implementation tracker)
  - `SECURITY_POLICY.md` (Envelope encryption, SLA, vulnerability disclosure)
  - `.env.example` & `.gitignore` (Secret-safe configuration templates)
  - 20-file `docs/` technical architecture and specification suite (`PRODUCT.md`, `ARCHITECTURE.md`, `DATABASE.md`, `API.md`, `SECURITY.md`, `INTEGRATIONS.md`, `AI.md`, `DEPLOYMENT.md`, `TESTING.md`, `OBSERVABILITY.md`, `CLI.md`, `SDK.md`, `KUBERNETES.md`, `CONTRIBUTING.md`, `GIT_WORKFLOW.md`, `DEVELOPMENT.md`, `ADR.md`, `ROADMAP.md`, `SCREEN_MAP.md`, `TEAM_OWNERSHIP.md`, `DESIGN_SYSTEM.md`).
- **Backend Modular Monolith Baseline (Spring Boot 3.3.4 / Java 21)**:
  - Maven multi-module descriptor with Spring Data JPA, PostgreSQL driver, Redis, Flyway, Spring Security, Validation, Actuator, and SpringDoc OpenAPI UI.
  - Domain package layout under `com.secretvault`: `auth`, `organization`, `project`, `environment`, `secret`, `encryption`, `access`, `audit`, `security`, `integration`, `provider`, `sync`, `runtime`, `identity`, `cicd`, `template`, `deployment`, `health`, `ai`.
  - Global exception handling framework (`GlobalExceptionHandler`, `ErrorResponse`, `ApiException` hierarchy).
  - Observability filter (`CorrelationIdFilter` with MDC logging) and public health probe (`SystemHealthController`).
  - Baseline Flyway migration (`V1__init_baseline.sql`).
  - Automated unit and slice test suite (`GlobalExceptionHandlerTest`, `CorrelationIdFilterTest`, `SystemHealthControllerTest`, `SecretVaultApplicationTests`).
- **Infrastructure & CI/CD**:
  - `docker-compose.yml` for local PostgreSQL 16, Redis 7, and Backend service.
  - Multi-stage `backend/Dockerfile` with non-root runtime container.
  - GitHub Actions CI workflow (`.github/workflows/ci.yml`) for automated builds and test verification.
