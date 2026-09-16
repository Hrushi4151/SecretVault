# SecretVault — AI Intelligence Architecture & Privacy Safeguards

## 1. Architecture & Service Isolation

The SecretVault AI Intelligence service is implemented as an independent microservice using **Python and FastAPI**. It functions as an operational co-pilot for DevSecOps teams to interpret complex security signals, triage incidents, and diagnose deployment issues.

```mermaid
graph LR
    subgraph CorePlane["SecretVault Core Control Plane"]
        Backend["Spring Boot Backend"]
    end

    subgraph AIService["AI Intelligence Service (Python / FastAPI)"]
        FastAPI["FastAPI Orchestrator"]
        LLMAdapter["LLM Adapter (Anthropic / OpenAI / Gemini)"]
    end

    Backend -->|HTTPS / Sanitized Context (No Plaintext Secrets)| FastAPI
    FastAPI --> LLMAdapter
    LLMAdapter -->|Advisory Explanation & Root Cause| Backend
```

---

## 2. Inviolable Security & Privacy Boundaries

1. **Zero Plaintext Secrets:** Plaintext secret values MUST NEVER be included in LLM prompts, embeddings, or training datasets. Only sanitized metadata (e.g. secret names, version numbers, age, sync state, failure stacktraces without credentials) may be transmitted.
2. **Advisory Only:** AI output is strictly advisory. The AI service CANNOT execute destructive actions (deleting secrets, revoking access, rotating credentials) without explicit human confirmation.
3. **Graceful Degradation:** If the AI service is offline, unreachable, or disabled, the core SecretVault platform continues to function with zero disruption.

---

## 3. Core AI Use Cases [PLANNED]

### 3.1 Deployment Root Cause Analysis (RCA)
- Correlates failed application deployments with recent secret updates, configuration drift, or provider sync failures.
- Synthesizes deployment failure logs to pinpoint missing environment variables.

### 3.2 Secret Leak & Exposure Analysis
- Analyzes source code snippets flagged by pre-commit or repository scanners.
- Evaluates exposure severity, identifies the affected credential type, and generates immediate remediation instructions.

### 3.3 Blast Radius & Dependency Explanation
- Traverses the secret dependency graph to explain downstream impact if a credential is rotated, expired, or compromised.

### 3.4 Security Risk & Anomaly Interpretation
- Explains anomalous access patterns (e.g. sudden spikes in reveal frequency or unfamiliar IP access) and recommends appropriate security policy adjustments.

---

## 4. Hallucination Safeguards

- Grounding responses strictly in provided metadata and audit logs.
- Formatting suggestions with clickable UI action links rather than raw synthetic code blocks.
- Explicit uncertainty disclaimers when context is insufficient.
