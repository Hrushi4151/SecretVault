# Changelog

All notable changes to the **SecretVault** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added - Phase 1: Authentication, Identity & Multi-Tenant Workspaces
- **Authentication & Identity Engine**:
  - `User`, `RefreshToken`, `UserStatus` domain entities and repositories.
  - BCrypt 12-round secure password hashing.
  - JJWT 0.12.6 implementation (`JwtTokenProvider`, `JwtAuthenticationFilter`, `JwtAuthenticationEntryPoint`, `UserPrincipal`).
  - Auth REST API (`/api/v1/auth/register`, `/login`, `/refresh`, `/me`, `/logout`).
  - Atomic auto-provisioning of personal Organization and default Workspace with `OWNER` role on registration.
- **Multi-Tenant Workspaces & RBAC**:
  - `Organization`, `Workspace`, `WorkspaceMembership`, `WorkspaceRole` entities and repositories.
  - ThreadLocal `TenantContext` for workspace isolation.
  - Workspace REST API (`/api/v1/workspaces`, creation, details, member management, listing).
  - RBAC permission checks for workspace modification and membership management.
- **Database Migrations & Test Suite**:
  - Flyway migration `V2__auth_and_workspaces_schema.sql` (PostgreSQL DDL with foreign keys and composite indexes).
  - 100% passing test suite across `JwtTokenProviderTest`, `AuthServiceTest`, `WorkspaceServiceTest`, `AuthControllerTest`, `WorkspaceControllerTest` (30/30 tests green).
- **Phase 1 Frontend Web Dashboard (React / Vite / Tailwind CSS / JSX)**:
  - Stitch dark glassmorphism design system tokens (`#05070A`, `#7C5CFF`, glass panels, borders).
  - Clean JSX component architecture (`LoginScreen.jsx`, `RegisterScreen.jsx`, `ForgotPasswordModal.jsx`, `MfaChallengeScreen.jsx`, `WorkspaceSwitcher.jsx`, `CreateWorkspaceModal.jsx`, `WorkspaceMembersDialog.jsx`, `WorkspaceOverview.jsx`, `AppShell.jsx`).
  - Reusable components (`Button.jsx`, `Input.jsx`, `Badge.jsx`, `Modal.jsx`, `Alert.jsx`).
  - Reusable API client (`client.js`, `auth.js`, `workspaces.js`) with Bearer token authentication, `X-Workspace-ID` tenant context, and silent refresh.
  - Global `AuthContext.jsx` with full authentication lifecycle, session persistence, and multi-tenant workspace switcher.

### Added - Phase 2: Projects, Environments & RBAC Expansion
- **Projects & Environments Domain**:
  - `Project`, `ProjectStatus`, `Environment`, `EnvType`, `EnvironmentStatus` entities and repositories.
  - Granular RBAC capabilities on `WorkspaceRole` (`canCreateProjects()`, `canManageProjects()`, `canManageEnvironments()`).
  - Automatic provisioning of 3 default deployment tiers (`development`, `staging`, and `production` with `is_protected = true`) on project creation.
  - Strict hierarchical authorization chain (`User → Workspace Membership → Workspace → Project → Environment`).
  - Cross-tenant IDOR defense returning `404 RESOURCE_NOT_FOUND` on mismatched parent-child route paths.
  - Project REST APIs (`/api/v1/workspaces/{workspaceId}/projects`, list, create, get, patch, delete).
  - Environment REST APIs (`/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments`, list, create, get, patch, delete).
- **Database Migrations & Test Suite**:
  - Flyway migration `V3__projects_and_environments_schema.sql` adding `projects` and `environments` tables with foreign keys and unique slug constraints.
  - Full automated test suite across `ProjectServiceTest`, `ProjectControllerTest`, `EnvironmentServiceTest`, `EnvironmentControllerTest` (50/50 tests passing).
- **Phase 2 Frontend Control Plane (React / Vite / Tailwind CSS / JSX)**:
  - High-Contrast Bold Dark Ruby-Garnet design system.
  - `ProjectsView.jsx` (Stitch Screen 10 / Screen 12) featuring live project listing, environment tier chips, search, and metric cards.
  - `CreateProjectModal.jsx` (Stitch Screen 11) with slug auto-generation and automated environment provisioning notice.
  - Integrated `projectApi` and `environmentApi` client modules.

### Planned (Upcoming Milestone: Phase 3)
- Secret Engine & Encryption (`com.secretvault.secret`, `com.secretvault.encryption`) with AES-256-GCM envelope encryption and masked secret reveal.

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
