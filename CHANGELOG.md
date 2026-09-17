# Changelog

All notable changes to the **SecretVault** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added - Phase 3: Core Secret Management & AES-256-GCM Envelope Encryption Engine
- **Cryptographic Envelope Encryption Engine (`com.secretvault.encryption`)**:
  - `EncryptedPayload` immutable record storing ciphertext, wrapped DEK, IV nonce, GCM auth tag, and key reference.
  - `KmsKeyProvider` SPI with `LocalDevKmsKeyProvider` implementation using RFC 3394 / AES-KW and AES-GCM wrapping with 256-bit Master Key Encryption Key (KEK).
  - `AesGcmEnvelopeEncryptionService` with cryptographically secure random 256-bit DEK and 96-bit IV generation per encryption operation.
  - Authenticated Additional Data (AAD) context binding: binds ciphertext to `secretId:environmentId:versionNumber`, rejecting cross-swapping attacks.
  - Comprehensive cryptographic test suite (`AesGcmEnvelopeEncryptionServiceTest`).
- **Core Secret Domain & Lifecycle (`com.secretvault.secret`)**:
  - `Secret`, `SecretVersion`, `SecretStatus` (`ACTIVE`, `DISABLED`, `DELETED`) entities and repositories.
  - Immutable version ledger with monotonic version increments ($v_{N+1}$) and atomic pointer advancement.
  - Zero-plaintext default guarantee: standard GET endpoints return `SecretMetadataResponse` without secret values.
  - Explicit Reveal API (`POST /api/v1/.../secrets/{secretId}/reveal`) returning plaintext with HTTP `Cache-Control: no-store, no-cache, must-revalidate, private` and `Pragma: no-cache` headers.
  - Soft deletion protecting audit history while blocking further reveal/mutation actions.
  - Bulk `.env` import API (`POST /api/v1/.../secrets/batch-import`) with key validation, comments parsing, and optional overwrite toggle.
- **Append-Only Tamper-Evident Audit Logging (`com.secretvault.audit`)**:
  - `AuditLog` entity, `AuditAction` (`SECRET_CREATED`, `SECRET_REVEALED`, `SECRET_VALUE_UPDATED`, `SECRET_DELETED`), and `AuditService`.
  - Records non-repudiable logs with actor ID, workspace ID, client IP, request ID, and timestamp without sensitive plaintext leakage.
- **Database Schema & Migrations**:
  - Flyway migration `V5__core_secret_management_schema.sql` creating `secrets`, `secret_versions`, and `audit_logs` tables with UUID PKs, composite indexes, and unique constraints.
- **Frontend Control Plane (React 18 / Vite / Tailwind CSS / JSX)**:
  - `frontend/src/api/secrets.js` API client module.
  - `SecretsView.jsx` (Stitch Screen 14): Project dropdown selector, environment tier filter chips, real-time search, metrics cards, and instant reveal timer.
  - `CreateSecretModal.jsx` (Stitch Screen 15): Dual-mode modal supporting single secret creation and bulk `.env` paste/upload import with syntax parser.
  - `SecretDetailsModal.jsx` (Stitch Screens 16 & 17): Masked view, on-demand explicit reveal with countdown auto-mask timer, one-click clipboard copy, immutable version history timeline, metadata and value mutation tabs, and soft delete confirmation.
- **Automated Test Suite**:
  - 99/99 passing backend tests across all unit and integration test suites.

### Added - Phase 2 Extension: Workspace Access, Invitations & Fine-Grained Scoping
- **Workspace Governance & Member Lifecycle**:
  - `UpdateMemberRoleRequest`, `UpdateWorkspaceSettingsRequest`, `WorkspaceSettingsResponse`.
  - Member role modification (`PATCH /api/v1/workspaces/{id}/members/{userId}`) and member removal / leave workspace (`DELETE /api/v1/workspaces/{id}/members/{userId}`).
  - Strict last `OWNER` protection preventing demoting or removing the final workspace owner.
  - Workspace configuration and governance policy API (`GET/PATCH /api/v1/workspaces/{id}/settings`).
- **Cryptographic Workspace Invitation System**:
  - `WorkspaceInvitation`, `InvitationStatus` (`PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED`, `REVOKED`).
  - High-entropy single-use invitation tokens with SHA-256 one-way hash storage (`token_hash`).
  - Expiration and revocation APIs (`POST /workspaces/{id}/invitations`, `GET /workspaces/{id}/invitations`, `POST /workspaces/{id}/invitations/{id}/revoke`, `POST /invitations/accept`).
  - One-time acceptance enforcement and token replay prevention.
- **Fine-Grained Scoped Project & Environment Access**:
  - `ProjectAccess` entity and repository for project-level member role grants.
  - `EnvironmentAccess`, `PermissionLevel` (`READ`, `WRITE`, `MANAGE`) for environment-level access scoping.
  - Permission reduction rule: $\text{Effective Permission} = \text{Workspace Role} \cap \text{Project Scope} \cap \text{Environment Scope}$. Child grants can narrow privilege but cannot elevate beyond parent workspace permissions.
  - Project member scoping APIs (`GET/POST/PATCH/DELETE /api/v1/workspaces/{workspaceId}/projects/{projectId}/members`).
  - Environment access scoping APIs (`GET/POST/PATCH/DELETE /api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/access`).
- **Database Migrations & Test Suite**:
  - Flyway migration `V4__workspace_invitations_and_access_scoping.sql` with UUID PKs, FKs, unique constraints, and indexes.
  - Expanded test suite across `WorkspaceInvitationServiceTest`, `WorkspaceInvitationControllerTest`, `ProjectAccessServiceTest`, `ProjectAccessControllerTest`, `EnvironmentAccessServiceTest`, `EnvironmentAccessControllerTest`, and `WorkspaceServiceTest` (72/72 tests passing).
- **Frontend Dashboard Governance**:
  - Tabbed `WorkspaceMembersDialog.jsx` (Screen 38 / 39 / 40) supporting Active Members, Pending Invitations, and Token Generation with single-use clipboard copying.
  - Viewport-centered `Modal.jsx` and `CreateProjectModal.jsx` utilizing `createPortal(..., document.body)`.

### Added - Phase 2: Projects, Environments & RBAC Expansion
- **Projects & Environments Domain**:
  - `Project`, `ProjectStatus`, `Environment`, `EnvType`, `EnvironmentStatus` entities and repositories.
  - Granular RBAC capabilities on `WorkspaceRole` (`canCreateProjects()`, `canManageProjects()`, `canManageEnvironments()`).
  - Automatic provisioning of 3 default deployment tiers (`development`, `staging`, and `production` with `is_protected = true`) on project creation.
  - Strict hierarchical authorization chain (`User → Workspace Membership → Workspace → Project → Environment`).
  - Cross-tenant IDOR defense returning `404 RESOURCE_NOT_FOUND` on mismatched parent-child route paths.
  - Project REST APIs (`/api/v1/workspaces/{workspaceId}/projects`, list, create, get, patch, delete).
  - Environment REST APIs (`/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments`, list, create, get, patch, delete).
- **Database Migrations**:
  - Flyway migration `V3__projects_and_environments_schema.sql` adding `projects` and `environments` tables with foreign keys and unique slug constraints.
- **Phase 2 Frontend Control Plane (React / Vite / Tailwind CSS / JSX)**:
  - High-Contrast Bold Dark Ruby-Garnet design system.
  - `ProjectsView.jsx` (Stitch Screen 10 / Screen 12) featuring live project listing, environment tier chips, search, and metric cards.
  - `CreateProjectModal.jsx` (Stitch Screen 11) with slug auto-generation and automated environment provisioning notice.
  - Integrated `projectApi` and `environmentApi` client modules.

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
  - 100% passing test suite (30/30 tests green).
- **Phase 1 Frontend Web Dashboard (React / Vite / Tailwind CSS / JSX)**:
  - Stitch dark glassmorphism design system tokens.
  - Clean JSX component architecture (`LoginScreen.jsx`, `RegisterScreen.jsx`, `ForgotPasswordModal.jsx`, `MfaChallengeScreen.jsx`, `WorkspaceSwitcher.jsx`, `CreateWorkspaceModal.jsx`, `WorkspaceMembersDialog.jsx`, `WorkspaceOverview.jsx`, `AppShell.jsx`).
  - Reusable components (`Button.jsx`, `Input.jsx`, `Badge.jsx`, `Modal.jsx`, `Alert.jsx`).
  - Reusable API client (`client.js`, `auth.js`, `workspaces.js`) with Bearer token authentication, `X-Workspace-ID` tenant context, and silent refresh.
  - Global `AuthContext.jsx` with full authentication lifecycle, session persistence, and multi-tenant workspace switcher.

### Planned (Upcoming Milestone: Phase 4)
- Versioning & Audit: Rollback engine, point-in-time secret diffing, version tagging, and audit log compliance search.

---

## [0.1.0-SNAPSHOT] - 2026-09-16

### Added
- Project Specifications & Comprehensive Documentation.
- Backend Modular Monolith Baseline (Spring Boot 3.3.4 / Java 21).
- Infrastructure & CI/CD Docker and GitHub Actions workflows.
