# ADR-016: Privileged Access Security — Dual Approval Quorum, Ephemeral Elevation & Break-Glass

## Status
**ACCEPTED / IMPLEMENTED** (Phase 5.8.4)

## Context
SecretVault protects sensitive secrets, cryptographic keys, and infrastructure configurations across multi-tenant workspaces. Standing administrative access presents significant insider threat and credential compromise risks. High-impact actions such as secret deletion, mass export, role escalation, and emergency incident remediation require strict governance, four-eyes quorum verification, real-time elevation evaluation, and an audited break-glass protocol.

## Decision
1. **Centralized Privileged Action Model**: Defined 17 high-risk privileged actions under the `PrivilegedAction` enum, scoped across Workspace, Project, Environment, and Secret levels.
2. **Policy-Driven Dual Approval (Four-Eyes Principle)**: Implemented configurable approval quorum (`approval_quorum >= 1`) with strict anti-self-approval enforcement (`approver != requester && approver != targetUser`).
3. **Real-Time Temporary Elevation Engine**: Created `PrivilegedAccessElevation` records directly evaluated by `EffectiveAccessService` during authorization checks, with instant revocation and automatic real-time expiration.
4. **Emergency Break-Glass Protocol**: Implemented strongly-authenticated, time-bounded, explicitly-scoped break-glass access requiring mandatory 20+ character justification, Step-Up/WebAuthn proof, and comprehensive audit trails. Break-glass never grants global root or unrestricted OWNER authority.
5. **REST API & Defense-in-Depth**: Exposed rate-limited endpoints with `@SecurityRequirement(name = "BearerAuth")`, `@RateLimited`, and mandatory `Cache-Control: no-store` headers.

## Consequences
- **Positive**:
  - Eliminates standing privileges for high-risk operations.
  - Ensures compliance with SOC2, FedRAMP, and ISO 27001 four-eyes access requirements.
  - Guarantees fail-safe emergency access during critical outages without granting unconstrained standing administrative authority.
  - Comprehensive, tamper-evident audit logging for all elevation requests, decisions, executions, and revocations.
- **Negative / Operational Considerations**:
  - Requires active participation from workspace approvers to satisfy quorum on non-emergency operations.
  - Requesters must complete Step-Up / WebAuthn challenges before executing privileged actions or emergency elevations.
