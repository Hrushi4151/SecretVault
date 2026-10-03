# Phase 13 Architecture Overview — Event-Driven Secret Intelligence & Security Automation

## 1. Executive Summary

Phase 13 establishes SecretVault as an **Event-Driven Secret Intelligence, Security Automation, Webhook Governance & Incident Operations platform**. Prior phases established cryptographic secret isolation, zero-downtime dual-version rotation, distributed leasing, workload consumer registration, machine identity, and JIT access. Phase 13 introduces an authoritative domain event backbone and closed-loop security automation.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        SECRETVAULT DOMAIN CORE                         │
│   (Secrets, Versions, Leases, Consumers, Rotations, JIT, Providers)    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Atomic DB Commit
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   TRANSACTIONAL OUTBOX (event_outbox)                  │
│       • Schema-versioned domain events (JSON)                          │
│       • Zero-plaintext leakage invariant                               │
│       • Statuses: PENDING, PROCESSING, PROCESSED, FAILED, DEAD_LETTER  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Polling & Distributed Lock
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     EVENT DISPATCHER & DEDUPLICATION                   │
│       • At-least-once delivery with exponential backoff & jitter       │
│       • Consumer-level deduplication via event_processing_log          │
└───────────┬───────────────────┬───────────────────┬────────────────────┘
            │                   │                   │
            ▼                   ▼                   ▼
     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
     │ AUTOMATION  │     │   WEBHOOK   │     │ NOTIFICATION│
     │   ENGINE    │     │   ENGINE    │     │ & INCIDENTS │
     │  (AST/DSL)  │     │(SSRF Defense│     │ (Multi-Chan │
     │  Approvals  │     │HMAC-SHA256) │     │ Remediation)│
     └─────────────┘     └─────────────┘     └─────────────┘
```

## 2. Core Architectural Pillars

1. **Transactional Outbox Engine (`event_outbox`):**
   Every state modification (secret creation, reveal, rotation trigger, lease expiration, consumer stale, machine suspension) emits a strongly typed, immutable domain event inside the local database transaction. Events are guaranteed never to be lost even in server crash scenarios.
2. **Domain Event Deduplication (`event_processing_log`):**
   Ensures idempotent processing across clustered instances using a unique composite index `(event_id, consumer_name)`.
3. **Deterministic Condition DSL & Safe Automation:**
   AST-based rule engine supporting complex Boolean logic (`AND`, `OR`, `NOT`, `EQUALS`, `IN`, `CONTAINS`, `GREATER_THAN`) without arbitrary code execution risk.
4. **Execution Budgets & Anti-Loop Tripwires:**
   Recursion depth tracking via `causationId` and execution rate limiting prevents infinite automation loops.
5. **SSRF-Defended Webhook Platform:**
   Pre-flight DNS resolution and IP address validation blocking loopback, link-local, RFC1918 private subnets, and cloud metadata endpoints (`169.254.169.254`).
6. **Automated Incident Response & Compromise Playbooks:**
   Instant compromise containment: revoking active leases, disabling stale consumers, executing emergency rotation, and notifying responders.
7. **Explainable Secret Health Scoring:**
   Multi-factor health algorithm (freshness, active lease density, reveal anomalies, consumer heartbeats, drift status) generating normalized scores (0-100) with explainable risk breakdown.
