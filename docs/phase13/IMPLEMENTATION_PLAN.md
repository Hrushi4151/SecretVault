# SecretVault Phase 13 — Event-Driven Secret Intelligence, Security Automation & Policy Operations Platform
## Implementation Plan & Architectural Blueprint

---

## 1. Executive Overview & Objectives

SecretVault Phase 13 transforms SecretVault from a cryptographic secret-management and rotation platform into an **Event-Driven Secret Intelligence, Security Automation & Policy Operations Platform**.

Phase 13 establishes:
1. **Authoritative Domain Event Architecture:** Immutable, strictly redacted, schema-versioned domain events tracking every security, secret lifecycle, rotation, lease, consumer, machine, access grant, and provider synchronization event.
2. **Transactional Outbox & Reliable Dispatcher:** Atomic domain event persistence alongside database transactions (`event_outbox`), at-least-once delivery with distributed locking, exponential backoff, jitter, dead-lettering, and consumer idempotency deduplication (`event_processing_log`).
3. **Sandboxed Automation Policy Engine:** Deterministic, user-programmable AST/DSL condition engine (AND, OR, NOT, nested, comparisons) with safe automated actions (`NOTIFY`, `TRIGGER_ROTATION`, `REVOKE_LEASE`, `DISABLE_CONSUMER`, `SUSPEND_MACHINE`, `REVOKE_MACHINE`, `CREATE_SECURITY_FINDING`, `CREATE_SECURITY_INCIDENT`, `CREATE_WEBHOOK_DELIVERY`, `ADD_AUDIT_EVENT`).
4. **Execution Safety, Loop Protection & Approvals:** Causation tree depth tracking, execution budgets, anti-infinite-loop tripwires, dry-run simulation mode (`/api/v1/automation-policies/simulate`), and four-eyes approval workflows for high-blast-radius actions.
5. **Unified Multi-Channel Notification Engine:** In-app, webhook, email abstraction, and chat adapters with configurable workspace/user preferences, severity routing (`INFO` to `CRITICAL`), deduplication windows, and alert storm suppression.
6. **Hardened Webhook Platform with SSRF Defense:** HMAC-SHA256 request signing (`X-SecretVault-*` headers), post-DNS IP resolution revalidation (blocking loopback, RFC1918, link-local, cloud metadata `169.254.169.254`), timeouts, and retry/dead-letter delivery engine.
7. **Security Incident Engine & Automated Remediation:** First-class incident lifecycle management (`OPEN` -> `INVESTIGATING` -> `CONTAINED` -> `REMEDIATION` -> `RESOLVED` -> `CLOSED`), multi-signal event correlation, and end-to-end automated compromise remediation (`SECRET_COMPROMISED` -> impact analysis -> lease revocation -> emergency rotation -> consumer migration -> incident resolution).
8. **Explainable Secret Health & Risk Intelligence:** Multi-factor scoring (rotation freshness, active leases, reveal frequency, consumer health, drift) with transparent justification factors and deterministic anomaly detection rules.
9. **Full-Stack Extension:** New Frontend views (`EventCenterView.jsx`, `AutomationCenterView.jsx`, `WebhookCenterView.jsx`, `SecurityOperationsView.jsx`, Notification Center), expanded CLI subcommands (`events`, `automation`, `webhook`, `notification`, `incident`), and Java SDK / Spring Boot Starter event listeners (`@SecretVaultEventListener`).

---

## 2. Existing Architecture Audit & Non-Duplication Baseline

| Component | Existing Class / Service | Phase 13 Usage & Boundary Rule |
| :--- | :--- | :--- |
| **Authorization Engine** | [`EffectiveAccessService.java`](file:///Users/nimisha/Downloads/SecretVault-main/backend/src/main/java/com/secretvault/access/service/EffectiveAccessService.java) | **STRICT NON-DUPLICATION.** All automation policies, webhook configs, event replays, incident operations, and automated actions validate through `EffectiveAccessService`. |
| **Rotation Engine** | [`RotationService.java`](file:///Users/nimisha/Downloads/SecretVault-main/backend/src/main/java/com/secretvault/rotation/service/RotationService.java) | **STRICT NON-DUPLICATION.** Automated rotation calls `RotationService.triggerRotation(...)` preserving the 21-state state machine, providers, validation, and zero-downtime leases. |
| **Audit Ledger** | [`AuditService.java`](file:///Users/nimisha/Downloads/SecretVault-main/backend/src/main/java/com/secretvault/audit/service/AuditService.java) | **STRICT NON-DUPLICATION.** Immutable audit events recorded for all policy mutations, simulations, executions, approvals, webhook deliveries, incident updates, and remediations. |
| **Security Center** | [`SecurityPostureService.java`](file:///Users/nimisha/Downloads/SecretVault-main/backend/src/main/java/com/secretvault/security/posture/service/SecurityPostureService.java), [`SecurityDetectionRules.java`](file:///Users/nimisha/Downloads/SecretVault-main/backend/src/main/java/com/secretvault/security/engine/SecurityDetectionRules.java) | Integrated to surface outbox backlogs, dead letters, automation failures, webhook SSRF attempts, policy loops, and secret health findings. |
| **Distributed State** | [`RedisDistributedLockManager.java`](file:///Users/nimisha/Downloads/SecretVault-main/backend/src/main/java/com/secretvault/common/lock/RedisDistributedLockManager.java), [`RedisSecurityStateStore.java`](file:///Users/nimisha/Downloads/SecretVault-main/backend/src/main/java/com/secretvault/common/security/state/RedisSecurityStateStore.java) | Used for outbox worker leader election / batch claiming and idempotency key locks with thread-safe in-memory fallbacks for test runners. |
| **Scheduler** | Spring `@Scheduled` / `ThreadPoolTaskScheduler` | Transactional outbox polling, dead-letter recovery, webhook retry runner, and incident correlation scanner. |

---

## 3. Detailed Phase 13 Component Architecture

```
                    ┌─────────────────────────────────────────────────────────────┐
                    │                   DOMAINS & MUTATIONS                       │
                    │  (Secret, Rotation, Lease, Consumer, Machine, Access, JIT)  │
                    └──────────────────────────────┬──────────────────────────────┘
                                                   │ 1. Atomic DB Write
                                                   ▼
                    ┌─────────────────────────────────────────────────────────────┐
                    │            TRANSACTIONAL OUTBOX (event_outbox)              │
                    │  Statuses: PENDING, PROCESSING, PROCESSED, FAILED, DEAD_LTR │
                    └──────────────────────────────┬──────────────────────────────┘
                                                   │ 2. Claim & Distributed Lock
                                                   ▼
                    ┌─────────────────────────────────────────────────────────────┐
                    │              EVENT DISPATCHER & DEDUPLICATION               │
                    │       (event_processing_log deduplicates per consumer)      │
                    └──────┬───────────────┬───────────────┬───────────────┬──────┘
                           │               │               │               │
                           ▼               ▼               ▼               ▼
                    ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐
                    │   AUDIT    │  │ AUTOMATION │  │  WEBHOOK   │  │NOTIFY & OPS│
                    │  CONSUMER  │  │   ENGINE   │  │   ENGINE   │  │ INCIDENTS  │
                    └────────────┘  └──────┬─────┘  └──────┬─────┘  └──────┬─────┘
                                           │               │               │
                                           ▼               ▼               ▼
                                    ┌────────────┐  ┌────────────┐  ┌────────────┐
                                    │ Policy DSL │  │SSRF Defense│  │In-App/Push │
                                    │ Simulator  │  │HMAC-SHA256 │  │Incident    │
                                    │ Approvals  │  │Retries/Dead│  │Correlation │
                                    └────────────┘  └────────────┘  └────────────┘
```

---

## 4. Domain Event Taxonomy & Redaction Guarantees

Every domain event contains:
- `eventId`: UUID
- `eventType`: Canonical type string
- `eventVersion`: Schema version (e.g. 1)
- `occurredAt`, `recordedAt`: ISO-8601 timestamps
- `workspaceId`: UUID (Mandatory tenant isolation)
- `projectId`, `environmentId`, `secretId`: Optional hierarchical resource pointers
- `actorType`, `actorId`: Triggering caller identity
- `correlationId`, `causationId`, `requestId`: Distributed tracing and causation tree
- `source`: Subsystem name
- `severity`: `INFO`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `metadata`: JSON structured attributes (strict redaction: NO plaintext secret values, credentials, tokens, or raw DEKs).

### Taxonomy Categories:
1. **Secret Events:** `SECRET_CREATED`, `SECRET_UPDATED`, `SECRET_DELETED`, `SECRET_REVEALED`, `SECRET_VERSION_CREATED`, `SECRET_ROLLBACK`, `SECRET_PROMOTED`, `SECRET_BRANCH_CREATED`, `SECRET_MERGED`, `SECRET_COMPROMISED`, `SECRET_REVOKED`, `SECRET_RESTORED`.
2. **Rotation Events:** `ROTATION_POLICY_CREATED`, `ROTATION_POLICY_UPDATED`, `ROTATION_POLICY_DISABLED`, `ROTATION_TRIGGERED`, `ROTATION_STARTED`, `ROTATION_GENERATING`, `ROTATION_GENERATED`, `ROTATION_VALIDATING`, `ROTATION_VALIDATED`, `ROTATION_STAGED`, `ROTATION_ACTIVATED`, `ROTATION_GRACE_STARTED`, `ROTATION_REVOKING`, `ROTATION_COMPLETED`, `ROTATION_FAILED`, `ROTATION_ROLLED_BACK`, `ROTATION_PAUSED`, `ROTATION_RESUMED`.
3. **Lease Events:** `LEASE_CREATED`, `LEASE_RENEWED`, `LEASE_EXPIRED`, `LEASE_REVOKED`, `LEASE_RENEWAL_REJECTED`.
4. **Consumer Events:** `CONSUMER_REGISTERED`, `CONSUMER_HEARTBEAT`, `CONSUMER_STALE`, `CONSUMER_RECOVERED`, `CONSUMER_DISABLED`, `CONSUMER_REFRESHED`, `CONSUMER_REFRESH_FAILED`.
5. **Machine Events:** `MACHINE_CREATED`, `MACHINE_DISABLED`, `MACHINE_SUSPENDED`, `MACHINE_REVOKED`, `MACHINE_SESSION_CREATED`, `MACHINE_SESSION_REJECTED`.
6. **Access Events:** `ACCESS_GRANTED`, `ACCESS_REVOKED`, `ACCESS_EXPIRED`, `JIT_REQUESTED`, `JIT_APPROVED`, `JIT_REJECTED`, `JIT_EXPIRED`, `JIT_REVOKED`, `ACCESS_REVIEW_CREATED`, `ACCESS_REVIEW_COMPLETED`, `ACCESS_REVIEW_REMEDIATION`.
7. **Provider Events:** `PROVIDER_CONNECTED`, `PROVIDER_CONNECTION_FAILED`, `PROVIDER_SYNC_STARTED`, `PROVIDER_SYNC_COMPLETED`, `PROVIDER_SYNC_FAILED`, `PROVIDER_DRIFT_DETECTED`, `PROVIDER_DRIFT_RESOLVED`, `PROVIDER_RESOURCE_DISCOVERED`.
8. **Security Events:** `SECURITY_FINDING_CREATED`, `SECURITY_FINDING_RESOLVED`, `SECURITY_INCIDENT_CREATED`, `SECURITY_INCIDENT_UPDATED`, `SECURITY_INCIDENT_RESOLVED`, `SECURITY_POLICY_VIOLATION`, `ANOMALY_DETECTED`.
9. **System Events:** `CONFIG_CHANGED`, `SCHEDULER_FAILURE`, `EVENT_PROCESSING_FAILURE`, `WEBHOOK_DELIVERY_FAILURE`, `AUTOMATION_EXECUTION_FAILURE`.

---

## 5. Transactional Outbox, Event Dispatcher & Idempotency

- **Table `event_outbox`:**
  - Fields: `id`, `event_id`, `event_type`, `schema_version`, `workspace_id`, `aggregate_type`, `aggregate_id`, `payload` (TEXT/JSON), `occurred_at`, `available_at`, `status`, `attempt_count`, `locked_at`, `locked_by`, `last_error`, `processed_at`, `correlation_id`, `causation_id`, `created_at`, `updated_at`.
  - Indexes: `idx_outbox_status_available` (`status`, `available_at`), `idx_outbox_workspace_created` (`workspace_id`, `created_at`), `idx_outbox_event_id` (`event_id`).
- **Table `event_processing_log`:**
  - Fields: `id`, `event_id`, `consumer_name`, `processed_at`, `status`, `attempt_count`, `error`, `correlation_id`, `created_at`.
  - Unique Constraint: `(event_id, consumer_name)` preventing duplicate consumer side-effects.
- **Event Dispatcher (`EventDispatcher.java`):**
  - Claims batches of `PENDING` events whose `available_at <= NOW()`.
  - Locks using row-level update or Redis lease to avoid duplicate worker execution.
  - Recovers stale processing locks (`locked_at < NOW() - 5 min`).
  - Supports pluggable `DomainEventHandler<T>` consumers (`AuditEventHandler`, `AutomationEventHandler`, `WebhookEventHandler`, `NotificationEventHandler`, `IncidentEventHandler`).
  - Implements exponential backoff ($2^{\text{attempt}} \times 1000\text{ms}$) + jitter up to max attempts (default 5), then marks `DEAD_LETTER`.

---

## 6. Automation Policy Engine, DSL & Simulator

- **Table `automation_policies`:**
  - Fields: `id`, `workspace_id`, `name`, `description`, `enabled`, `priority`, `trigger_event_types`, `conditions_json`, `actions_json`, `dry_run`, `approval_required`, `policy_version`, `created_by`, `updated_by`, `created_at`, `updated_at`.
- **Table `automation_executions`:**
  - Fields: `id`, `workspace_id`, `policy_id`, `policy_version`, `event_id`, `trigger_event_type`, `status`, `dry_run`, `evaluation_result_json`, `actions_executed_json`, `error_message`, `causation_id`, `correlation_id`, `duration_ms`, `created_at`.
- **Table `automation_approvals`:**
  - Fields: `id`, `workspace_id`, `execution_id`, `policy_id`, `action_type`, `action_payload_json`, `status` (`PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`), `requested_by`, `decided_by`, `rejection_reason`, `expires_at`, `decided_at`, `created_at`.
- **Safe Sandboxed Condition AST / DSL:**
  - Operators: `EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`, `CONTAINS`, `STARTS_WITH`, `GREATER_THAN`, `LESS_THAN`, `AND`, `OR`, `NOT`.
  - Allowed Target Fields: `eventType`, `severity`, `workspaceId`, `projectId`, `environmentId`, `environmentType`, `secretKey`, `rotationAgeDays`, `leaseAgeMinutes`, `consumerStatus`, `machineStatus`, `findingSeverity`.
  - **Zero Arbitrary Code Execution:** Strictly rejects SpEL, Groovy, JS eval, reflection, or shell calls.
- **Loop Protection:**
  - Maximum automation chain depth (default 5).
  - Traversal of `causationId` and `parentEventId` chains.
  - Per-policy rate limits (e.g. max 10 executions/min per policy).
  - Tripwire: Halts execution, raises `SECURITY_FINDING_CREATED`, records critical audit event.
- **Policy Simulator (`/api/v1/automation-policies/simulate`):**
  - Evaluates mock or historical event payloads against workspace policies.
  - Returns detailed evaluation breakdown, matching status per condition, actions that would fire, required permissions, and affected scope without executing any side-effects.

---

## 7. Unified Notification Engine

- **Table `notifications`:**
  - Fields: `id`, `workspace_id`, `recipient_id`, `severity`, `title`, `message`, `event_id`, `action_url`, `channel`, `status` (`UNREAD`, `READ`, `ACKNOWLEDGED`, `EXPIRED`), `created_at`, `read_at`, `acknowledged_at`, `expires_at`.
- **Table `notification_preferences`:**
  - Fields: `id`, `workspace_id`, `user_id`, `channel_in_app`, `channel_webhook`, `channel_email`, `min_severity`, `muted_event_types_json`, `quiet_hours_enabled`, `quiet_hours_start`, `quiet_hours_end`, `created_at`, `updated_at`.
- **Deduplication & Storm Suppression:**
  - Time-window aggregation (e.g. 5 minutes): collapses bursts of identical event alerts into single digest notifications (e.g. "15 consumers became stale across workspace").

---

## 8. Webhook Platform & Strict SSRF Defense

- **Table `webhook_endpoints`:**
  - Fields: `id`, `workspace_id`, `name`, `destination_url`, `enabled`, `subscribed_events_json`, `signing_secret_encrypted`, `secret_prefix`, `created_by`, `created_at`, `updated_at`, `last_success_at`, `last_failure_at`.
- **Table `webhook_deliveries`:**
  - Fields: `id`, `workspace_id`, `webhook_id`, `event_id`, `attempt`, `status` (`PENDING`, `SUCCESS`, `FAILED`, `DEAD_LETTER`), `http_status`, `duration_ms`, `error_message`, `payload_digest`, `next_retry_at`, `delivered_at`, `created_at`.
- **Cryptographic Request Signing:**
  - HMAC-SHA256 signature over `${timestamp}.${rawPayload}` with stored signing secret.
  - Headers: `X-SecretVault-Event-Id`, `X-SecretVault-Event-Type`, `X-SecretVault-Timestamp`, `X-SecretVault-Signature`, `X-SecretVault-Delivery-Id`.
- **SSRF & DNS Rebinding Protection:**
  - Resolves destination hostname to IP address prior to connect.
  - Blocks all IPv4/IPv6 private ranges: `127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16` (Cloud Metadata / Link-Local), `::1`, `fc00::/7`, `fe80::/10`.
  - Disables unsafe HTTP redirects to internal IP addresses.
  - Enforces 5s connection timeout, 10s total timeout, 1MB response size limit.

---

## 9. Security Incident Engine & Automated Remediation

- **Table `security_incidents`:**
  - Fields: `id`, `workspace_id`, `incident_number`, `severity` (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), `category`, `status` (`OPEN`, `INVESTIGATING`, `CONTAINED`, `REMEDIATION`, `RESOLVED`, `CLOSED`), `title`, `description`, `source_event_id`, `assignee_id`, `created_by`, `resolved_by`, `resolved_at`, `created_at`, `updated_at`.
- **Table `security_incident_events`:**
  - Fields: `id`, `incident_id`, `event_id`, `relationship_type` (`ROOT_CAUSE`, `CORRELATED`, `SIDE_EFFECT`), `created_at`.
- **Automated Incident Correlation:**
  - Temporal clustering (sliding window) grouping related events (e.g. rotation failure + consumer stale + lease expiration on the same secret) into a single incident.
- **Automated Compromise Playbook:**
  - `SECRET_COMPROMISED` event -> creates `CRITICAL` incident -> revokes active leases -> performs consumer dependency impact analysis -> triggers emergency rotation -> verifies and activates new version -> migrates consumers -> revokes compromised version -> sends incident escalation notifications -> marks incident `RESOLVED`.

---

## 10. Database Migration Plan (`V17__event_outbox_automation_webhooks_incidents.sql`)

1. `event_outbox`
2. `event_processing_log`
3. `automation_policies`
4. `automation_executions`
5. `automation_approvals`
6. `notifications`
7. `notification_preferences`
8. `webhook_endpoints`
9. `webhook_deliveries`
10. `security_incidents`
11. `security_incident_events`
12. `event_replay_requests`

All tables include:
- `workspace_id UUID NOT NULL` with foreign keys to `workspaces(id)`
- Audit timestamps `created_at`, `updated_at`
- Covering composite indexes for high-throughput queries
- H2 in-memory test & PostgreSQL production column definition compatibility (`TEXT` for JSON structures).

---

## 11. Testing Strategy

1. **Unit & Engine Tests:** Event serialization/sanitization, AST condition evaluation, loop detection, webhook HMAC calculation, SSRF address filter, notification storm deduplication.
2. **Transactional Outbox & Dispatcher Concurrency Tests:** Multi-threaded worker event claiming, retry exponential backoff, dead-letter threshold, consumer idempotency log.
3. **Automation & Approval Integration Tests:** Policy evaluation, approval requirement block, anti-self-approval enforcement, dry-run simulation, execution history tracking.
4. **Webhook Security & SSRF Tests:** Loopback/link-local/cloud metadata IP rejection, signature generation and validation, delivery retry on 5xx/429, no-retry on 400.
5. **Incident & Automated Remediation E2E Tests:** `SECRET_COMPROMISED` trigger -> emergency rotation -> lease cascade -> consumer refresh -> incident lifecycle transition.
6. **Multi-Tenant Security & IDOR Tests:** Cross-workspace event query block, cross-workspace policy execution block, cross-workspace replay rejection.
7. **Frontend Unit & Component Tests:** Vitest tests for `EventCenterView`, `AutomationCenterView`, `WebhookCenterView`, `SecurityOperationsView`, and Notification Center.
8. **CLI & SDK Tests:** Picocli commands for events, automation, webhooks, notifications, incidents; SDK event listeners and runtime refresh.

---

## 12. Deliverable Verification Checklist

- [ ] `V17` Flyway migration script written and verified.
- [ ] Domain event publisher, outbox entity, repository, and dispatcher implemented.
- [ ] Automation policy entity, AST condition evaluator, action executor, simulator, and approval workflow implemented.
- [ ] Webhook endpoint, delivery engine, SSRF filter, and HMAC signer implemented.
- [ ] Notification engine, preferences, and deduplication implemented.
- [ ] Security incident engine, correlation, and automated remediation implemented.
- [ ] Secret health engine and deterministic anomaly detection rules implemented.
- [ ] REST API controllers and OpenAPI annotations created.
- [ ] Frontend views and navigation integrated into `AppShell.jsx`.
- [ ] CLI commands extended and tested.
- [ ] SDK & Spring Boot Starter event listeners added and tested.
- [ ] Full test suite run (`mvn test` in backend, cli, sdk; `npm test` in frontend) achieving 100% pass rate.
- [ ] 25+ Phase 13 documentation files generated in `docs/phase13/`.
- [ ] Git commit and push to `origin/main`.
