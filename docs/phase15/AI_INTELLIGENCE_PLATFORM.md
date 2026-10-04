# Phase 15: AI Intelligence Co-Pilot & DevSecOps Security Automation Platform

## 1. Executive Summary & Architectural Overview

Phase 15 integrates a production-grade, zero-plaintext **AI Intelligence Co-Pilot and DevSecOps Security Automation Platform** natively into SecretVault's modular monolith architecture.

Designed for mission-critical enterprise environments, SecretVault AI operates under ten non-negotiable security invariants:
1. **Strictly Advisory by Default**: The AI generates reviewable remediation plans, telemetry evidence chains, and diff previews. It has zero autonomous write capability to system secrets.
2. **Zero-Plaintext Security Boundary**: The AI *never* receives plaintext secrets, ciphertexts, Data Encryption Keys (DEKs), Key Encryption Keys (KEKs), passwords, API tokens, provider credentials, or database connection strings.
3. **Mandatory Sanitization Pre-Processing**: `AiContextSanitizer` sits as an impenetrable boundary between data stores, user queries, external inputs, and the LLM engine.
4. **Tenant/Workspace Database-Level Isolation**: All context collection occurs strictly within authenticated workspace and tenant boundaries before any AI analysis or model invocation.
5. **Structural Metadata Only**: External providers receive only sanitized structural metadata, SHA-256 masked references, and non-sensitive schema keys.
6. **Deterministic Offline Engine**: `DeterministicOfflineLlmProvider` operates without external network connectivity for air-gapped, CI, and local offline environments.
7. **Human-in-the-Loop Remediation**: AI-generated remediation plans require explicit human approval and step-up authentication where sensitive blast radius thresholds are met.
8. **Audited Core Execution Gateway**: Execution of approved plans routes strictly through existing audited SecretVault core APIs (`EffectiveAccessService`, `RotationService`, `SyncService`).
9. **Real-world Provider Abstraction**: Pluggable provider SPI (`LlmProvider`) with circuit breaker, timeout, rate limiter, and fallback capabilities.
10. **Explainable AI Evidence Chains**: Every recommendation includes confidence scores, latency telemetry, risk classifications, and correlated audit pointers.

---

## 2. Core Subsystems

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
                              │     AiContextSanitizer           │
                              │  (Zero-Plaintext Guardrail)     │
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
                              │ (Diff Preview, Blast Radius)     │
                              └────────────────┬─────────────────┘
                                               │ (Human Approval Required)
                                               ▼
                              ┌──────────────────────────────────┐
                              │ AiRemediationExecutionGateway    │
                              │ ──► Audited Core Platform APIs   │
                              └──────────────────────────────────┘
```

### 2.1. Zero-Plaintext Sanitizer (`AiContextSanitizer`)
All context and prompt strings pass through `AiContextSanitizer.sanitizeContext(String)`:
- Live secret tokens (`sk_live_...`, `ghp_...`, `aws_secret_access_key`, `eyJ...`) are replaced with `[REDACTED_SECRET_TOKEN]`.
- Raw PEM private keys (`-----BEGIN PRIVATE KEY-----`) are scrubbed and replaced with `[REDACTED_PRIVATE_KEY]`.
- Database connection strings (`postgresql://user:pass@host...`) have credentials redacted.
- Password assignments (`password=...`, `secret=...`) are replaced with cryptographic non-reversible hashes: `password=[SHA256:<hash>]`.

### 2.2. Deterministic Offline Engine (`DeterministicOfflineLlmProvider`)
Enables 100% deterministic, offline execution with zero network requirements:
- **Deployment & Sync RCA**: Analyzes connection timeouts, handshake failures, and lease expirations to produce root-cause hypotheses, executive summaries, and recommended corrective actions.
- **Posture Forecasting**: Projects 7-day, 14-day, and 30-day security posture decay scores based on lease drift velocity, expiring credentials, and stale permissions.
- **Remediation Plans**: Synthesizes step-by-step migration diffs, dual-credential rotation schedules, and blast radius assessments.

### 2.3. Rate Limiter & Token Budgeting (`AiRateLimiterAndBudgetEnforcer`)
Enforces daily token quotas and rate limits per workspace:
- Daily token allowance tracking per workspace with automatic midnight UTC quota resets.
- Prevents runaway automation costs and protects against Denial-of-Service inquiries.

### 2.4. Advisory Remediation Workbench & Authoritative Execution Gateway
Remediation plans progress through a formalized state lifecycle:
`PROPOSED` ──► `APPROVED` ──► `EXECUTED` (or `REJECTED` / `EXPIRED`).
Execution does not occur within the AI engine; instead, `AiRemediationExecutionGateway` validates workspace tenancy, verifies human approval, and invokes authoritative core services (`RotationService`, `SecurityIncidentService`).

---

## 3. Database Schema (`V20__ai_intelligence_copilot_schema.sql`)

- **`ai_inquiries`**: Tracks natural-language user queries, sanitized prompts, AI responses, model metadata, latency, and sanitized telemetry evidence.
- **`ai_rca_reports`**: Persists deployment and sync failure root-cause analysis reports, primary root causes, executive summaries, and recommended actions.
- **`ai_remediation_plans`**: Stores step-by-step remediation plans, risk level, confidence scores, blast radius metrics, approval metadata, and execution states.
- **`ai_token_budgets`**: Tracks token quotas, daily token utilization, and rate-limiting counters partitioned by workspace.

---

## 4. REST API Specification

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/workspaces/{id}/ai/chat` | Submit natural-language inquiry to AI Copilot |
| `GET` | `/api/v1/workspaces/{id}/ai/chat/history` | List historical inquiries (paginated) |
| `POST` | `/api/v1/workspaces/{id}/ai/rca` | Trigger automated deployment/sync root-cause analysis |
| `GET` | `/api/v1/workspaces/{id}/ai/rca/{reportId}` | Retrieve detailed RCA report |
| `GET` | `/api/v1/workspaces/{id}/ai/posture/forecast` | Retrieve 7/14/30-day posture decay forecast |
| `GET` | `/api/v1/workspaces/{id}/ai/plans` | List remediation plans |
| `POST` | `/api/v1/workspaces/{id}/ai/plans/generate` | Generate new remediation plan |
| `POST` | `/api/v1/workspaces/{id}/ai/plans/{planId}/approve` | Approve plan (Human Officer Sign-Off) |
| `POST` | `/api/v1/workspaces/{id}/ai/plans/{planId}/execute` | Execute approved plan through core APIs |
| `POST` | `/api/v1/workspaces/{id}/ai/plans/{planId}/reject` | Reject plan |
| `POST` | `/api/v1/workspaces/{id}/ai/plans/{planId}/feedback` | Record human thumbs-up/down feedback |
| `GET` | `/api/v1/workspaces/{id}/ai/token-budget` | Retrieve token budget and daily quota status |

---

## 5. Developer CLI (`secretvault ai`)

```bash
# Inquire Copilot
secretvault ai ask "Why did our staging deployment fail?" --intent DEPLOYMENT_RCA

# Run Deployment RCA
secretvault ai rca --target-type DEPLOYMENT --target-id dep-prod-auth-01 --logs "FATAL: password authentication failed for user app"

# Inspect Security Posture Forecast
secretvault ai posture

# Remediation Plans
secretvault ai plans list
secretvault ai plans generate --goal "Rotate expiring database credentials"
secretvault ai plans approve <plan-id>
secretvault ai plans execute <plan-id>
secretvault ai plans reject <plan-id>

# Token Budget Status
secretvault ai budget
```

---

## 6. Java SDK Diagnostics Client

```java
try (SecretVaultClient client = SecretVaultClient.create(config)) {
    // Submit inquiry
    AiInquiryResponse response = client.ai().inquire("Check stale access tokens");
    System.out.println("AI Response: " + response.responseContent());

    // Run deployment RCA
    AiRcaResponse rca = client.ai().runRca("DEPLOYMENT", "dep-app-1", "Connection timeout");
    System.out.println("Root Cause: " + rca.primaryRootCause());

    // Inspect posture forecast
    AiPostureForecast forecast = client.ai().getPostureForecast();
    System.out.println("Current Posture: " + forecast.currentScore() + "/100");

    // List and execute remediation plans
    List<AiPlanInfo> plans = client.ai().listPlans();
    if (!plans.isEmpty() && "APPROVED".equals(plans.get(0).status())) {
        client.ai().executePlan(plans.get(0).id());
    }
}
```

---

## 7. Security Certification & Test Verification

All invariants are continuously verified through automated unit, integration, and security tests:
- **`AiZeroPlaintextSecurityTest`**: Validates scrubbing of raw credentials, API tokens, and destructive command suppression.
- **`AiContextSanitizerTest`**: Comprehensive boundary testing across URI credentials, PEM keys, and secret token variants.
- **`DeterministicOfflineLlmProviderTest`**: Validates offline deterministic generation across all intents without external network access.
- **`AiDeploymentRcaServiceTest` & `AiRecommendationEngineTest`**: Validates RCA evidence synthesis and blast-radius calculation.
- **`AiRemediationExecutionGatewayTest`**: Validates multi-tenant isolation, human approval enforcement, and core gateway execution.
