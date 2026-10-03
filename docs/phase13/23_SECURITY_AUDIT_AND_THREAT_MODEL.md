# Security Audit & Threat Model — Phase 13

## 1. Threat Modeling (STRIDE)

| Threat | Risk | Mitigation in Phase 13 |
| :--- | :--- | :--- |
| **Spoofing** | Forging webhook notifications | HMAC-SHA256 request signatures with timestamp freshness checks. |
| **Tampering** | Altering domain events in outbox | Outbox table entries are strictly append-only; payload hash digest validated. |
| **Repudiation** | Denying an automated action | Four-eyes approvals ledger (`automation_approvals`) records `decided_by` and audit logs. |
| **Information Disclosure** | Leaking secret values in events | Automated metadata redaction (`BaseDomainEvent.sanitizeMetadata`) drops sensitive keys. |
| **Denial of Service** | Infinite automation cascades | `AutomationLoopDetector` halts causation chains deeper than 5 and applies rate limits. |
| **Elevation of Privilege** | Replay of old administrative events | Idempotency log (`event_processing_log`) prevents unauthorized re-execution. |
| **SSRF** | Probing internal networks via webhooks | `SsrfProtectionValidator` blocks loopback, RFC1918, link-local, and cloud metadata. |

## 2. Invariable Security Rules

1. **Zero Secret Leakage:** Event outbox payloads must NEVER store raw plaintext secret values or cryptographic keys.
2. **Strict Multi-Tenancy:** All queries, policies, webhooks, and incidents are bounded by `workspace_id`.
3. **No Arbitrary Code Execution:** Automation DSL is purely declarative with a static AST evaluator.
