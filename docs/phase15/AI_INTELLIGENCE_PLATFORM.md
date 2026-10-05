# Phase 15: AI Intelligence Co-Pilot & DevSecOps Security Automation Platform

## 1. Executive Summary & Architectural Overview

Phase 15 integrates a production-grade, zero-plaintext **AI Intelligence Co-Pilot and DevSecOps Security Automation Platform** natively into SecretVault's modular monolith architecture.

Designed for mission-critical enterprise environments, SecretVault AI operates under non-negotiable security invariants:
1. **Strictly Advisory by Default**: The AI generates reviewable remediation plans, telemetry evidence chains, and diff previews. It has zero autonomous write capability to system secrets.
2. **Zero-Plaintext Security Boundary**: The AI *never* receives plaintext secrets, ciphertexts, Data Encryption Keys (DEKs), Key Encryption Keys (KEKs), passwords, API tokens, provider credentials, or database connection strings.
3. **Mandatory Sanitization Pre-Processing**: `AiContextSanitizer` and `AiContextBuilder` sit as an impenetrable boundary between data stores, user queries, external inputs, and the LLM engine.
4. **Typed Bounded Safe Context**: Metadata is gathered via strongly typed models (`AiSafeContext`), bounded to at most 1,000 characters per operational hint, and capped at the top 5 high-priority findings.
5. **Tenant & Workspace Database-Level Isolation**: All context collection occurs strictly within authenticated workspace and tenant boundaries before any AI analysis or model invocation.
6. **Deterministic Offline Engine**: `DeterministicOfflineLlmProvider` operates without external network connectivity for air-gapped, CI, and local offline environments across 13 standard DevSecOps intents.
7. **Four-Eyes Separation of Duties Gate**: Critical and high-risk remediation plans enforce mandatory dual-authorization (`Approver A != Approver B`) before execution can proceed.
8. **Step-Up MFA Verification**: Authoritative execution of high-risk remediation plans requires valid Step-Up authentication proof.
9. **Cryptographic Approval Binding**: Remediation plans are sealed with SHA-256 integrity fingerprints upon approval. Any subsequent modification increments the version and invalidates prior approvals.
10. **Audited Core Execution Gateway**: Execution routes strictly through `AiRemediationExecutionGateway` into audited SecretVault core APIs (`EffectiveAccessService`, `RotationService`, `SyncService`).

---

## 2. Architecture & Data Flow

```
                                 [ User / CLI / SDK / Web UI ]
                                               │
                                               ▼
                               ┌──────────────────────────────────┐
                               │  Tenant & Workspace Auth Filter  │
                               └────────────────┬─────────────────┘
                                               │
                                               ▼
                               ┌──────────────────────────────────┐
                               │     AiContextBuilder &           │
                               │     AiContextSanitizer           │
                               │  (Zero-Plaintext & Bounded Size) │
                               └────────────────┬─────────────────┘
                                               │
                      ┌─────────────────────────┴─────────────────────────┐
                      ▼                                                   ▼
        ┌───────────────────────────┐                       ┌───────────────────────────┐
        │   Deterministic Offline   │                       │   Pluggable Cloud LLM     │
        │     Inference Engine      │                       │     (Anthropic/OpenAI)    │
        │   (Air-Gapped / CI)       │                       │    (Sanitized Prompts)    │
        └─────────────┬─────────────┘                       └─────────────┬─────────────┘
                      └─────────────────────────┬─────────────────────────┘
                                                │
                                                ▼
                               ┌──────────────────────────────────┐
                               │    AiSafetyGuardrailValidator    │
                               │   (Suppresses unsafe commands)   │
                               └────────────────┬─────────────────┘
                                                │
                                                ▼
                               ┌──────────────────────────────────┐
                               │   Advisory Remediation Plan      │
                               │ (Diff Preview, Blast Radius,     │
                               │  Four-Eyes & Step-Up Flags)      │
                               └────────────────┬─────────────────┘
                                                │ (Four-Eyes & Step-Up Sign-Off)
                                                ▼
                               ┌──────────────────────────────────┐
                               │ AiRemediationExecutionGateway    │
                               │ (Dry-Run, Idempotency, MFA Check)│
                               │ ──► Audited Core Platform APIs   │
                               └──────────────────────────────────┘
```

---

## 3. Core Subsystems

### 3.1. Zero-Plaintext Sanitizer (`AiContextSanitizer`)
All context, prompt strings, and model outputs pass through `AiContextSanitizer`:
- **Live secret tokens**: `sk_live_...`, `ghp_...`, `github_pat_...`, `xox...`, `AKIA...`, `ASIA...`, and JWT envelopes are replaced with `[REDACTED_SECRET_TOKEN]`.
- **Raw PEM private keys**: `-----BEGIN PRIVATE KEY-----` blocks are scrubbed and replaced with `[REDACTED_ASYMMETRIC_PRIVATE_KEY]`.
- **Database & Service URLs**: `postgresql://user:pass@host...` and AMQP/Redis URIs have credentials redacted to `postgresql://user:[REDACTED_CREDENTIAL]@host...`.
- **Key-Value credential assignments**: `password=...`, `secret=...` are replaced with one-way deterministic 8-character hash digests: `password=[SHA256:<hash>]`.
- **Strict Invariant Verification**: `assertZeroPlaintext(String)` throws `SecurityException` if any unredacted pattern is encountered in serialized contexts.

### 3.2. Strongly Typed Safe Context Assembly (`AiContextBuilder` & `AiSafeContext`)
- Gathers non-sensitive aggregated finding counts (`openCriticalFindingsCount`, `openHighFindingsCount`, `totalOpenFindingsCount`).
- Restricts contextual finding descriptions to the top 5 most severe findings.
- Truncates operational context hints to a maximum of 1,000 characters with explicit `... [truncated]` markers.
- Completely isolates plaintext encrypted envelopes, KMS key material, and raw payloads from context serialization.

### 3.3. Deterministic Offline Reasoning Provider (`DeterministicOfflineLlmProvider`)
Supports 13 standardized DevSecOps intents with 100% deterministic local reasoning:
1. `COPILOT_GENERAL`: General DevSecOps inquiries and system metadata analysis.
2. `SECURITY_POSTURE`: Mathematical posture index scoring and 14-day drift decay modeling.
3. `SECURITY_FINDING`: Triage and risk analysis for specific vulnerability findings.
4. `DEPLOYMENT_RCA`: Root-cause failure analysis with structured telemetry evidence (`[EV_HASH_MISMATCH]`, `[EV_HTTP_401]`, `[EV_LEASE_EXPIRED]`).
5. `SYNC_FAILURE`: Provider synchronization and edge drift diagnostics (`[EV_HTTP_429]`, `[EV_HASH_DRIFT]`).
6. `ROTATION_ANALYSIS`: Stale credential lifetime auditing and shadow validation recommendations.
7. `SECRET_HEALTH`: Cryptographic envelope state and synchronization checks.
8. `BLAST_RADIUS`: Dependency tree analysis and multi-service impact scoring.
9. `REMEDIATION_RECOMMENDATION`: Prioritized non-destructive corrective actions.
10. `REMEDIATION_PLAN`: Structured multi-phase remediation workflows.
11. `SYSTEM_HEALTH`: KMS envelope, database, and platform operational health.
12. `HELP`: Copilot capabilities, usage syntax, and slash command reference.
13. `UNKNOWN`: Safe fallback advisory analysis.

### 3.4. Four-Eyes Separation of Duties & Step-Up Security
- **Four-Eyes Gate**: High-risk and critical remediation plans require two distinct reviewers (`reviewed_by_user_id != second_reviewed_by_user_id`). Duplicate approval attempts by the same reviewer are rejected with `IllegalStateException`.
- **Plan Versioning**: Any post-generation modification increments the plan `version`, resets approval states to `PENDING_APPROVAL`, and invalidates the previous cryptographic seal.
- **Cryptographic Fingerprint**: A deterministic SHA-256 fingerprint over the plan type, version, target resource, steps, and payload diff is sealed into `plan_fingerprint`. Pre-execution verification rejects tampered plans.
- **Step-Up MFA Verification**: Critical plans enforce Step-Up verification proof (`stepUpProof`), rejecting unauthorized execution with `SecurityException`.
- **Idempotency Guard**: Replay execution of already executed plans safely returns the existing execution state without repeating destructive mutations.

---

## 4. Database Schema (`V20__ai_intelligence_copilot_schema.sql`)

- **`ai_inquiries`**: Tracks natural-language user queries, conversation threads (`conversation_id`), sanitized prompts, AI responses, model metadata, latency, and telemetry evidence.
- **`ai_rca_reports`**: Persists deployment and sync failure root-cause analysis reports, primary root causes, executive summaries, and recommended actions.
- **`ai_remediation_plans`**: Stores remediation plans with `version`, `requires_four_eyes`, `requires_step_up`, `reviewed_by_user_id`, `second_reviewed_by_user_id`, `plan_fingerprint`, blast radius metrics, and execution audit records.
- **`ai_token_budgets`**: Tracks token quotas, daily/monthly token consumption, and per-minute rate-limiting counters per workspace.

---

## 5. REST API Specification

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/workspaces/{id}/ai/chat` | Submit natural-language inquiry to AI Copilot |
| `GET` | `/api/v1/workspaces/{id}/ai/chat/history` | List historical inquiries (paginated) |
| `POST` | `/api/v1/workspaces/{id}/ai/rca` | Trigger automated deployment/sync root-cause analysis |
| `GET` | `/api/v1/workspaces/{id}/ai/rca/{reportId}` | Retrieve detailed RCA report |
| `GET` | `/api/v1/workspaces/{id}/ai/posture/forecast` | Retrieve posture score and 14-day decay forecast |
| `GET` | `/api/v1/workspaces/{id}/ai/plans` | List remediation plans |
| `POST` | `/api/v1/workspaces/{id}/ai/plans/generate` | Generate new remediation plan |
| `GET` | `/api/v1/workspaces/{id}/ai/plans/{planId}` | Retrieve remediation plan details |
| `POST` | `/api/v1/workspaces/{id}/ai/plans/{planId}/approve` | Approve plan (enforces Four-Eyes if required) |
| `POST` | `/api/v1/workspaces/{id}/ai/plans/{planId}/execute` | Authoritative or Dry-Run plan execution |
| `POST` | `/api/v1/workspaces/{id}/ai/plans/{planId}/reject` | Reject plan with human justification |
| `POST` | `/api/v1/workspaces/{id}/ai/plans/{planId}/feedback` | Record 1-5 star human feedback rating |
| `GET` | `/api/v1/workspaces/{id}/ai/token-budget` | Retrieve token budget and quota status |

---

## 6. Developer CLI & SDK Support

### CLI Commands (`secretvault ai`)
```bash
# Natural Language Copilot Chat
secretvault ai ask "Why did our payments-worker fail during deployment?"

# Trigger Deployment Root Cause Analysis
secretvault ai rca --target-type DEPLOYMENT --target-id dep-pay-worker-01 --logs "Pod exit code 1"

# Inspect Security Posture & 14-Day Trajectory
secretvault ai posture

# Manage Remediation Plans
secretvault ai plans list
secretvault ai plans generate --goal "Reconcile Payments Worker Secret Drift" --risk CRITICAL
secretvault ai plans approve <plan-id>
secretvault ai plans execute <plan-id> --dry-run
secretvault ai plans execute <plan-id> --step-up-token <mfa-proof>
secretvault ai plans feedback <plan-id> --rating 5 --comment "Zero downtime rollover"
```

### SDK Integration (`AiDiagnosticsApi`)
```java
try (SecretVaultClient client = SecretVaultClient.create(config)) {
    // Submit inquiry
    AiInquiryResponse response = client.ai().inquire("Diagnose provider drift on Vercel");

    // Generate and approve remediation plan
    AiRemediationPlanDto plan = client.ai().generatePlan(new AiPlanGenerateRequest(
        "Reconcile Vercel Sync Drift", null, rcaId, "SYNC_JOB", "job-42", AiRiskLevel.HIGH, true, true
    ));
    client.ai().approvePlan(plan.id());

    // Execute plan with Step-Up verification
    client.ai().executePlan(plan.id(), new ExecutePlanRequest(false, "Confirm execute", "mfa-stepup-token"));
}
```
