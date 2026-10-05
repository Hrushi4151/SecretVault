# Phase 15: AI Intelligence Co-Pilot & DevSecOps Security Automation Platform

## 1. Executive Summary & Architectural Overview

Phase 15 delivers a production-grade, zero-plaintext, general-purpose **AI Intelligence Co-Pilot and DevSecOps Security Automation Platform** natively embedded into SecretVault's modular monolith architecture.

Designed for mission-critical enterprise environments, SecretVault AI operates under non-negotiable security invariants:
1. **Strictly Advisory by Default**: The AI generates reviewable remediation plans, telemetry evidence chains, and diff previews. It has zero autonomous write capability to system secrets.
2. **Zero-Plaintext Security Boundary**: The AI *never* receives plaintext secrets, ciphertexts, Data Encryption Keys (DEKs), Key Encryption Keys (KEKs), passwords, API tokens, provider credentials, or database connection strings.
3. **Absolute Secret Value Firewall (`AiSecretFirewall`)**: Pre-flight and post-flight boundary scanners detect and redact all tokens, keys, credentials, and connection URIs across inquiries, tool arguments, tool outputs, and LLM responses into safe cryptographic SHA-256 fingerprint prefixes (`[SHA256:prefix]`).
4. **Dynamic Server-Side RBAC Tool Calling (`AiToolRegistry`, `AiToolExecutor`)**: The AI autonomously selects and invokes authorized domain tools. Every tool execution is strictly validated server-side against the caller's workspace membership and effective permissions (`EffectiveAccessService`).
5. **Multi-Step Agentic Reasoning Loop (`AiContextOrchestrator`)**: Performs dynamic multi-turn tool planning, execution, evidence collection, and response synthesis with cycle detection and bounded iterations (max 5 steps, max 10 tool calls).
6. **Pluggable Multi-Provider Architecture (`LlmProviderRegistry`)**:
   - **Local/Private LLMs**: `OllamaLlmProvider` for private, air-gapped on-premise execution (e.g., Llama 3, Mistral, DeepSeek).
   - **OpenAI-Compatible APIs**: `OpenAiCompatibleLlmProvider` supporting universal OpenAI-compatible endpoints with dynamic runtime credentials (zero disk/log persistence).
   - **Deterministic Offline Provider**: `DeterministicOfflineLlmProvider` for zero-network CI/CD test environments and instant fallback.
7. **Persistent Conversational Memory (`AiConversationService`)**: Multi-turn conversation sessions (`ai_conversations`), message histories (`ai_messages`), and tool audit logs (`ai_tool_executions`) with composite indexes.
8. **Four-Eyes Separation of Duties Gate**: Critical and high-risk remediation plans enforce mandatory dual-authorization (`Approver A != Approver B`) before execution can proceed.
9. **Step-Up MFA Verification**: Authoritative execution of high-risk remediation plans requires valid Step-Up authentication proof.
10. **Cryptographic Approval Binding**: Remediation plans are sealed with SHA-256 integrity fingerprints upon approval. Any subsequent modification increments the version and invalidates prior approvals.
11. **Streaming Real-Time Responses**: Server-Sent Events (SSE) streaming endpoint (`/api/v1/workspaces/{id}/ai/chat/stream`) for fluid user experience.

---

## 2. Architecture & Data Flow

```
                                 [ User / CLI / SDK / Web UI ]
                                               │
                                               ▼
                                ┌──────────────────────────────┐
                                │ Tenant & Workspace Filter    │
                                └──────────────┬───────────────┘
                                               │
                                               ▼
                                ┌──────────────────────────────┐
                                │   AiSecretFirewall (Inbound) │
                                │   (Zero-Plaintext Invariant) │
                                └──────────────┬───────────────┘
                                               │
                                               ▼
                                ┌──────────────────────────────┐
                                │   AiContextOrchestrator      │◄────────────────┐
                                │   (Multi-Step Agent Loop)    │                 │
                                └──────┬───────────────┬───────┘                 │
                                       │               │                         │
                     ┌─────────────────┘               └─────────────────┐       │
                     ▼                                                   ▼       │
        ┌───────────────────────────┐                       ┌────────────────────┴──────┐
        │    LlmProviderRegistry    │                       │     AiToolExecutor        │
        │ ┌───────────────────────┐ │                       │ (Server-Side RBAC Check)  │
        │ │ OllamaLlmProvider     │ │                       │ ┌───────────────────────┐ │
        │ ├───────────────────────┤ │                       │ │ Workspace & Project   │ │
        │ │ OpenAiCompatible      │ │                       │ ├───────────────────────┤ │
        │ ├───────────────────────┤ │                       │ │ Secrets & Rotation    │ │
        │ │ Deterministic Offline │ │                       │ ├───────────────────────┤ │
        │ └───────────────────────┘ │                       │ │ Audit & Security      │ │
        └─────────────┬─────────────┘                       │ ├───────────────────────┤ │
                      │                                     │ │ Knowledge Base        │ │
                      │                                     │ └───────────────────────┘ │
                      │                                     └───────────────────────────┘
                      ▼
        ┌───────────────────────────┐
        │ AiSecretFirewall & Safety │
        │ (Sanitize & Suppress)     │
        └─────────────┬─────────────┘
                      │
                      ▼
        ┌───────────────────────────┐
        │ Advisory Response / Plans │
        │ (Four-Eyes & Step-Up MFA) │
        └─────────────┬─────────────┘
                      │
                      ▼
        ┌───────────────────────────┐
        │ RemediationGateway        │──► Audited Core Platform APIs
        └───────────────────────────┘
```

---

## 3. Dynamic Tool Calling System

SecretVault AI features 14 server-side domain tools categorized across platform subsystems:

| Tool Category | Tool Name | Description | Required Permission |
|---|---|---|---|
| **Workspace** | `workspace.get` | Inspect current workspace configuration and settings | `WORKSPACE_READ` |
| **Workspace** | `workspace.members` | List workspace members and role assignments | `WORKSPACE_MEMBERS_READ` |
| **Project** | `project.list` | List all projects in active workspace | `PROJECT_READ` |
| **Project** | `project.get` | Inspect project details and environment summaries | `PROJECT_READ` |
| **Environment** | `environment.list` | List environments for a project | `ENVIRONMENT_READ` |
| **Environment** | `environment.get` | Get environment configuration | `ENVIRONMENT_READ` |
| **Secrets** | `secret.listMetadata` | List secret metadata (names, versions, rotation status — NEVER values) | `SECRET_READ_METADATA` |
| **Secrets** | `secret.getMetadata` | Inspect individual secret metadata and sync state | `SECRET_READ_METADATA` |
| **Secrets** | `secret.history` | Inspect version history and rotation lifecycle | `SECRET_READ_METADATA` |
| **Secrets** | `secret.blastRadius` | Calculate blast radius of secret modification/compromise | `SECRET_READ_METADATA` |
| **Security** | `security.findings` | Query open security findings, CVEs, and compliance drift | `SECURITY_FINDING_READ` |
| **Security** | `security.posture` | Compute security posture index and decay metrics | `SECURITY_POSTURE_READ` |
| **Security** | `security.events` | Review recent security audit events and anomalies | `SECURITY_EVENT_READ` |
| **Audit** | `audit.search` | Search audit log history with filters | `AUDIT_LOG_READ` |
| **Audit** | `audit.timeline` | Generate chronological audit timeline | `AUDIT_LOG_READ` |
| **Access** | `access.users` | Review workspace user access and memberships | `WORKSPACE_MEMBERS_READ` |
| **Access** | `access.roles` | Inspect predefined and custom RBAC permissions | `WORKSPACE_READ` |
| **Access** | `access.jit` | Check active and requested JIT access grants | `ACCESS_REQUEST_READ` |
| **Rotation** | `rotation.status` | Inspect rotation schedule and stale credentials | `SECRET_ROTATION_READ` |
| **Rotation** | `rotation.history` | Review previous credential rotation executions | `SECRET_ROTATION_READ` |
| **Sync** | `sync.status` | Check external provider sync status and health | `SYNC_JOB_READ` |
| **Sync** | `sync.drift` | Diagnose configuration drift across third-party targets | `SYNC_JOB_READ` |
| **Providers** | `provider.list` | List registered cloud and SaaS integrations | `PROVIDER_INTEGRATION_READ` |
| **Providers** | `provider.health` | Verify connectivity and credentials validity | `PROVIDER_INTEGRATION_READ` |
| **Deployment** | `deployment.list` | Inspect recent deployment events and logs | `AUDIT_LOG_READ` |
| **Machine** | `machine.list` | List machine identities, service principals, and SPIFFE IDs | `MACHINE_IDENTITY_READ` |
| **Incident** | `incident.timeline` | Reconstruct security incident timelines | `SECURITY_EVENT_READ` |
| **Knowledge** | `knowledge.search` | Search grounded platform documentation and architecture guides | Public |
| **Knowledge** | `knowledge.getTopic` | Retrieve deep technical reference on SecretVault internals | Public |

---

## 4. Grounded Technical Knowledge Base

The `AiPlatformKnowledgeService` provides the LLM with deep architectural facts about SecretVault:
- **KMS Envelope Encryption**: AES-256-GCM data encryption keys (DEKs), wrapped by root Key Encryption Keys (KEKs), HSM/AWS KMS/GCP Cloud KMS/HashiCorp Vault backends.
- **RBAC & Authorization**: Custom roles, granular permission bits, least-privilege scoping.
- **JIT & Break-Glass Access**: Time-bound temporary elevations, dual approvals, automatic expiration.
- **MFA & Passkeys**: TOTP RFC 6238, FIDO2/WebAuthn hardware keys, Step-Up MFA elevation.
- **Automated Rotation**: Zero-downtime dual-credential rotation, shadow validation, rollback safety.
- **Multi-Cloud Sync Engine**: End-to-end drift reconciliation across AWS Secrets Manager, GCP Secret Manager, Azure Key Vault, HashiCorp Vault, Kubernetes, Vercel, and GitHub.
- **Kubernetes Operator**: Custom Resource Definitions (`SecretVaultSecret`), dynamic sidecar injection.
- **Terraform Provider**: Declarative infrastructure as code for workspaces, environments, and secrets.
- **CLI & SDKs**: Java SDK, Python SDK, Go SDK, Node.js SDK, and unified Developer CLI.

---

## 5. Database Schema

### `V20__ai_intelligence_copilot_schema.sql`
- **`ai_inquiries`**: Natural-language inquiries, user intent, sanitized prompts, responses, model metadata, latency, and telemetry evidence.
- **`ai_rca_reports`**: Deployment and sync failure root-cause analysis reports, primary root causes, executive summaries, and recommended actions.
- **`ai_remediation_plans`**: Remediation plans with `version`, `requires_four_eyes`, `requires_step_up`, `reviewed_by_user_id`, `second_reviewed_by_user_id`, `plan_fingerprint`, blast radius metrics, and execution audit records.
- **`ai_token_budgets`**: Tracks token quotas, daily/monthly token consumption, and per-minute rate-limiting counters per workspace.

### `V21__ai_conversations_and_agentic_memory.sql`
- **`ai_conversations`**: Multi-turn conversation sessions bound to workspace and user, tracking title, provider name, model, token usage, and status.
- **`ai_messages`**: Ordered messages within a conversation (`USER`, `ASSISTANT`, `SYSTEM`, `TOOL`), including sanitized content and tool call metadata.
- **`ai_tool_executions`**: Comprehensive server-side audit records of dynamic tool invocations, duration, success/failure, and sanitized payload digests.

---

## 6. REST API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/workspaces/{id}/ai/chat` | Submit natural-language inquiry to AI Copilot |
| `GET` | `/api/v1/workspaces/{id}/ai/chat/stream` | Stream real-time AI response tokens via Server-Sent Events (SSE) |
| `GET` | `/api/v1/workspaces/{id}/ai/chat/history` | List historical inquiries (paginated) |
| `GET` | `/api/v1/workspaces/{id}/ai/inquiries` | List historical inquiries alias |
| `GET` | `/api/v1/workspaces/{id}/ai/providers` | List available LLM providers and operational status |
| `GET` | `/api/v1/workspaces/{id}/ai/conversations` | List conversation sessions for current user |
| `GET` | `/api/v1/workspaces/{id}/ai/conversations/{convId}` | Get full conversation transcript and messages |
| `DELETE` | `/api/v1/workspaces/{id}/ai/conversations/{convId}` | Archive/delete conversation session |
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

## 7. Configuration & Environment Variables

| Variable | Property | Default | Description |
|---|---|---|---|
| `AI_PROVIDER` | `secretvault.ai.provider` | `DETERMINISTIC_OFFLINE` | Active LLM provider (`DETERMINISTIC_OFFLINE`, `OLLAMA`, `OPENAI_COMPATIBLE`) |
| `AI_FALLBACK_PROVIDER` | `secretvault.ai.fallback-provider` | `DETERMINISTIC_OFFLINE` | Fallback provider on primary failure |
| `OLLAMA_BASE_URL` | `secretvault.ai.ollama.base-url` | `http://localhost:11434` | Ollama API endpoint |
| `OLLAMA_MODEL` | `secretvault.ai.ollama.model` | `llama3:8b` | Ollama chat model name |
| `OPENAI_BASE_URL` | `secretvault.ai.openai.base-url` | `https://api.openai.com` | OpenAI-compatible endpoint |
| `OPENAI_MODEL` | `secretvault.ai.openai.model` | `gpt-4o-mini` | OpenAI-compatible model name |
| `AI_API_KEY` | `secretvault.ai.openai.api-key` | `""` | Runtime API key (zero disk/log persistence) |
| `AI_TEMPERATURE` | `secretvault.ai.temperature` | `0.1` | Sampling temperature |
| `AI_MAX_TOKENS` | `secretvault.ai.max-tokens` | `2048` | Maximum token completion limit |
