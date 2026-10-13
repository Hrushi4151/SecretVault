# SecretVault — Phase 13: Security Incident Operations & Automated Remediation
## Comprehensive Architecture, Security, Implementation Audit, Gap Analysis & Local Verification Report

**Author**: Senior Application Security Engineer & Incident Response Architect  
**Target Repository**: `SecretVault` (`d:\CodePlayground\JAVA SpringBoot\SecureVault`)  
**Audit Date**: October 12, 2026  
**Environment**: Local Spring Boot 3.3.4 (Java 21), PostgreSQL 16 (`secretvault_dev`), Redis 7 (`secretvault-redis-dev`), Vite React Frontend  
**Scope**: Security Incident Operations, Multi-Signal Event Correlation, Incident Lifecycle & Triage State Machine, Automated Response Playbooks, Four-Eyes Governance, RBAC, and Infrastructure Integration

---

## A. Executive Verdict

### **Verdict**: Implemented with Specific Gaps (Hardened & Locally Verified)

#### Verdict Rationale
Prior to this audit, the **Security Incident Operations** module in SecretVault had fundamental persistence entities (`security_incidents`, `security_incident_events`), a database migration (`V18__event_outbox_automation_webhooks_incidents.sql`), an outbox event handler (`IncidentDomainEventHandler`), a service (`SecurityIncidentService`), a basic controller (`SecurityIncidentController`), and a frontend view (`SecurityOperationsView.jsx`).

However, an exhaustive audit revealed **five critical functional and security gaps**:
1. **Broken Access Control on Incident Mutations**: `SecurityIncidentService` enforced `SECURITY_VIEW` on read endpoints (`getIncident`, `listIncidents`, `getIncidentEvents`), but **omitted authorization checks on `createIncident` and `updateIncidentStatus`**, allowing any authenticated user to create or resolve/close incidents regardless of role.
2. **Missing Controller Validation (500 Server Error)**: `UpdateIncidentStatusRequest` used `@NotBlank` on the enum field `IncidentStatus status`. In Jakarta Bean Validation / Hibernate Validator, `@NotBlank` is invalid on non-string types, resulting in an unhandled `UnexpectedTypeException: HV000030` whenever status update requests were dispatched.
3. **Flawed Greedy Correlation & Self-Referential Loops**: Incoming domain events in a workspace were greedily correlated to the *first active incident found* without matching resource/aggregate identifiers or checking correlation keys. Furthermore, domain events published by the incident service itself (`SECURITY_INCIDENT_CREATED`, `SECURITY_INCIDENT_UPDATED`, `SECURITY_INCIDENT_RESOLVED`) were swallowed back into active incidents as correlated signals. Subsequent critical security events for different secrets were suppressed because the loop marked `correlated = true` and exited without opening a new ticket.
4. **Unenforced State Machine**: Status transitions could jump between any states (e.g., from `CLOSED` to `RESOLVED` directly), and incidents could be resolved or closed with null or empty resolution summaries.
5. **Missing Playbook Remediation Invocation on Incidents**: While `AutomationEngine` and `FindingRemediationService` supported background automated actions, operators had no capability to trigger or dry-run remediation playbooks bound directly to a security incident (`POST /remediate`). Furthermore, the schema-defined `relationship_type = 'REMEDIATION'` was never populated.

**Remediation & Verification**:
The engineering gaps were remediated with minimal, non-breaking interventions:
- Granular `SECURITY_MANAGE` access checks were added to all incident mutation paths.
- Proper `@NotNull` validation was applied to the controller request DTO.
- The state machine was hardened with validation against illegal jumps and mandatory resolution summaries.
- The correlation engine was updated to ignore incident lifecycle feedback loops and match resources by aggregate ID or causation/correlation tracing.
- Direct playbook remediation (`CONTAIN_INCIDENT`, `ROTATE_SECRET`, `REVOKE_SECRET_LEASES`, `DISABLE_MACHINE_IDENTITY`, `NOTIFY_RESPONDERS`) with dry-run preview and `REMEDIATION` relationship event linking was implemented.
- The unit test suite (`SecurityIncidentServiceTest`) and a comprehensive integration test suite (`SecurityIncidentIntegrationTest`) were authored and executed, achieving **14/14 passing tests** on the backend and **98/98 passing tests** on the frontend.

---

## B. Feature Matrix

| Capability | Status | Relevant Files / Classes / Migrations | Execution Path | Test Evidence | Defects & Hardening Applied |
| :--- | :---: | :--- | :--- | :--- | :--- |
| **Multi-Signal Ingestion** | **PASS** | `IncidentDomainEventHandler.java`<br>`SecurityIncidentService.java`<br>`EventType.java` | Outbox dispatcher -> `IncidentDomainEventHandler.handle` -> `processDomainEventCorrelation` | `SecurityIncidentIntegrationTest.testAutoIncidentCreationFromDomainEvent` | Expanded trigger events to cover `ROTATION_FAILED`, `PROVIDER_DRIFT_DETECTED`, `MACHINE_SESSION_REJECTED`, `LEASE_RENEWAL_REJECTED`, and `SECURITY_FINDING_CREATED`. |
| **Signal Correlation & Duplicate Suppression** | **PASS** | `SecurityIncidentService.java`<br>`SecurityIncidentEventRepository.java`<br>`V18__...sql` | `correlateEvent` -> `existsByIncidentIdAndEventId` -> `uq_incident_event` constraint | `SecurityIncidentIntegrationTest.testDuplicateEventSuppression` | Fixed greedy cross-aggregate correlation and self-referential event ingestion loops. |
| **Incident Creation & Numbering** | **PASS** | `SecurityIncidentService.java`<br>`SecurityIncident.java`<br>`uq_sec_incident_number` | `createIncident` -> `countByWorkspaceId` + collision probe -> save | `SecurityIncidentServiceTest.testCreateIncident`<br>`SecurityIncidentIntegrationTest.testIncidentLifecycleAndStateMachine` | Added collision avoidance loop to prevent concurrency crashes on unique workspace incident number constraint. |
| **Triage State Machine & Transitions** | **PASS** | `SecurityIncidentService.java`<br>`IncidentStatus.java` | `updateIncidentStatus` -> `validateStatusTransition` -> persist | `SecurityIncidentServiceTest.testUpdateIncidentStatus_InvalidTransition`<br>`SecurityIncidentIntegrationTest.testIncidentLifecycleAndStateMachine` | Server-side validation added. Prohibits invalid transitions (e.g. `CLOSED -> RESOLVED`) and enforces mandatory resolution summary. |
| **RBAC & Workspace Isolation** | **PASS** | `SecurityIncidentService.java`<br>`SecurityIncidentController.java`<br>`EffectiveAccessService.java` | `verifySecurityManageAccess` (`SECURITY_MANAGE`) & `verifySecurityViewAccess` (`SECURITY_VIEW`) | `SecurityIncidentServiceTest.testCreateIncident_Unauthorized`<br>`SecurityIncidentIntegrationTest.testCrossWorkspaceIsolation` | Previously lacked permission checks on `createIncident` and `updateStatus`. Enforced `SECURITY_MANAGE`. |
| **Playbook Dry-Run / Preview** | **PASS** | `IncidentDtos.java`<br>`SecurityIncidentService.java`<br>`SecurityIncidentController.java` | `POST /incidents/{id}/remediate` (dryRun=true) -> `remediateIncident` | `SecurityIncidentServiceTest.testRemediateIncident_DryRun`<br>`SecurityIncidentIntegrationTest.testPlaybookDryRunPreview` | New endpoint allowing preview of proposed remediation action without mutations or side effects. |
| **Playbook Live Execution** | **PASS** | `SecurityIncidentService.java`<br>`RotationService.java`<br>`SecretLeaseService.java`<br>`MachineIdentityService.java` | `remediateIncident` -> execute action -> correlate `REMEDIATION` event -> advance status | `SecurityIncidentServiceTest.testRemediateIncident_LiveRotateSecret`<br>`SecurityIncidentIntegrationTest.testPlaybookLiveExecutionContainment` | Executes real service actions: emergency rotation, dynamic lease revocation, machine suspension, containment, and notification alerts. |
| **Approval Gates & Separation of Duties** | **PASS** | `AutomationEngine.java`<br>`AutomationApprovalService.java`<br>`automation_approvals` | `hasSensitiveActions` -> `createApprovals` -> `decideApproval` (anti-self-approval) | `AutomationPolicyEngineIntegrationTest` (5 passed) | Sensitive background actions require dual-operator approval. Requesters and policy creators are prevented from approving their own actions. |
| **Audit Ledger & Traceability** | **PASS** | `SecurityIncidentService.java`<br>`AuditService.java`<br>`audit_logs` | `auditService.recordAudit` on created, updated, and remediation events | `SecurityIncidentIntegrationTest.testAuditTrailIntegrity` | Logs `SECURITY_INCIDENT_CREATED`, `SECURITY_INCIDENT_UPDATED`, and `SECURITY_INCIDENT_RESOLVED` with user/system attribution. |
| **Transactional Outbox Integration** | **PASS** | `EventPublisher.java`<br>`DomainEventDispatcher.java`<br>`transactional_events` | `eventPublisher.publish` -> outbox table -> polling dispatcher | `SecurityIncidentIntegrationTest.testIncidentLifecycleAndStateMachine` | Events published with `CRITICAL`/`HIGH` severity and dispatch causation linkage. |
| **Frontend Operations Console** | **PASS** | `SecurityOperationsView.jsx`<br>`incidents.js` | React 18 UI -> `incidentsApi` -> REST endpoints | `npm test -- --run` (18 test suites, 98 tests passed) | Integrated "Run Playbook" interactive modal, dry-run toggles, target UUID inputs, and colored badge for `REMEDIATION` events. |

---

## C. Defect Register

### Defect 1: Missing RBAC Authorization on Incident Creation & Status Mutations
- **Severity**: **CRITICAL** (Security / Authorization Bypass)
- **Impact**: Any authenticated tenant user with minimal permissions (such as `secret.read` or `workspace.view`) could create arbitrary security incidents or modify the triage status of existing incidents (including resolving or reopening tickets), bypassing security governance.
- **Root Cause**: `SecurityIncidentService.createIncident` and `SecurityIncidentService.updateIncidentStatus` lacked calls to `verifySecurityManageAccess(workspaceId, actorId)`. Only read methods checked `SECURITY_VIEW`.
- **Minimal Fix**: Introduced `verifySecurityManageAccess` checking `AccessPermission.SECURITY_MANAGE` before saving incidents or updating statuses. Allowed system actors (`actorId == null`) to execute automated triggers.
- **Retest Status**: Verified and passed via `SecurityIncidentServiceTest.testCreateIncident_Unauthorized` and `SecurityIncidentIntegrationTest.testCrossWorkspaceIsolation`.

### Defect 2: Controller Validation Exception on Status Update (`@NotBlank` on Enum)
- **Severity**: **HIGH** (Functional Crash / 500 Server Error)
- **Impact**: Any attempt to update incident status via `PUT /api/v1/workspaces/{workspaceId}/incidents/{incidentId}/status` failed with HTTP 500 Internal Server Error due to Hibernate Validator throwing `UnexpectedTypeException`.
- **Root Cause**: In `SecurityIncidentController.UpdateIncidentStatusRequest`, the record field was declared as `@NotBlank IncidentStatus status`. In Jakarta Bean Validation, `@NotBlank` can only be applied to `CharSequence`.
- **Minimal Fix**: Changed `@NotBlank` to `@NotNull` on `IncidentStatus status`.
- **Retest Status**: Verified and passed via `SecurityIncidentIntegrationTest.testIncidentLifecycleAndStateMachine`.

### Defect 3: Greedy Domain Event Swallowing & Self-Referential Feedback Loop
- **Severity**: **HIGH** (Data Integrity & Incident Suppression)
- **Impact**:
  1. Incident lifecycle events (`SECURITY_INCIDENT_CREATED`, `SECURITY_INCIDENT_UPDATED`, `SECURITY_INCIDENT_RESOLVED`) were correlated back to the incident itself.
  2. Any routine domain event in the workspace was greedily linked to the first active incident, suppressing the creation of new incidents for distinct compromised resources.
- **Root Cause**: `processDomainEventCorrelation` looped through `activeIncidents` without filtering for event types or matching resource identifiers, and broke on the first active incident with `correlated = true`.
- **Minimal Fix**:
  1. Ignored `SECURITY_INCIDENT_*` lifecycle events in `processDomainEventCorrelation`.
  2. Correlated only when `aggregateId`, `secretId`, `causationId`, or `correlationId` matches the active incident.
  3. If no matching incident exists, allowed distinct critical triggers to auto-create dedicated incidents.
- **Retest Status**: Verified and passed via `SecurityIncidentIntegrationTest.testAutoIncidentCreationFromDomainEvent`.

### Defect 4: Unvalidated Status Transitions and Empty Resolution Summaries
- **Severity**: **MEDIUM** (Operational Integrity)
- **Impact**: Analysts could jump directly between arbitrary terminal states (such as `CLOSED -> RESOLVED`) without reopening, and incidents could be closed without any post-mortem or resolution documentation.
- **Root Cause**: `updateIncidentStatus` directly assigned `incident.setStatus(newStatus)` without verifying valid state machine paths or requiring non-blank resolution text.
- **Minimal Fix**: Implemented `validateStatusTransition` allowing legitimate operational transitions (`OPEN -> INVESTIGATING -> CONTAINED -> REMEDIATION -> RESOLVED -> CLOSED`), allowing reopenings, and rejecting illegal transitions. Enforced `resolutionSummary` on `RESOLVED` and `CLOSED`.
- **Retest Status**: Verified and passed via `SecurityIncidentServiceTest.testUpdateIncidentStatus_InvalidTransition` and `testUpdateIncidentStatus_RequiresResolutionSummary`.

### Defect 5: Missing Incident Playbook Remediation Invocation
- **Severity**: **MEDIUM** (Workflow Disconnect)
- **Impact**: Operators had no direct mechanism to invoke or dry-run remediation playbooks (such as emergency rotation or dynamic lease revocation) directly from an incident ticket. Remediation events were not recorded with `relationship_type = REMEDIATION`.
- **Root Cause**: `SecurityIncidentController` and `SecurityIncidentService` lacked a remediation action endpoint.
- **Minimal Fix**: Created `IncidentDtos` (`RemediationPlaybookAction`, `IncidentRemediationRequest`, `IncidentRemediationResult`), implemented `remediateIncident` with dry-run support, real service invocations, audit logging, and `relationship_type = REMEDIATION` recording. Added `POST /{incidentId}/remediate` to controller and frontend.
- **Retest Status**: Verified and passed via `SecurityIncidentIntegrationTest.testPlaybookDryRunPreview` and `testPlaybookLiveExecutionContainment`.

---

## D. Test Results

### 1. Backend Targeted Incident Test Suite
Command:
```powershell
mvn.cmd test -Dtest=SecurityIncident*
```
Results:
```
[INFO] Running com.secretvault.incident.SecurityIncidentIntegrationTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 29.41 s -- in com.secretvault.incident.SecurityIncidentIntegrationTest
[INFO] Running com.secretvault.incident.SecurityIncidentServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.300 s -- in com.secretvault.incident.SecurityIncidentServiceTest
[INFO] Results:
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS (Total time: 37.110 s)
```

**Test Breakdown**:
- `SecurityIncidentServiceTest.testCreateIncident`: Verifies sequential numbering and event publication.
- `SecurityIncidentServiceTest.testCreateIncident_Unauthorized`: Verifies rejection when `SECURITY_MANAGE` is absent.
- `SecurityIncidentServiceTest.testUpdateIncidentStatus`: Verifies transition from `OPEN` to `RESOLVED` with summary.
- `SecurityIncidentServiceTest.testUpdateIncidentStatus_RequiresResolutionSummary`: Rejects empty resolution summary.
- `SecurityIncidentServiceTest.testUpdateIncidentStatus_InvalidTransition`: Rejects illegal transition (`CLOSED -> RESOLVED`).
- `SecurityIncidentServiceTest.testRemediateIncident_DryRun`: Verifies dry-run preview without side effects.
- `SecurityIncidentServiceTest.testRemediateIncident_LiveExecution`: Verifies live emergency rotation and `REMEDIATION` event correlation.
- `SecurityIncidentIntegrationTest.testIncidentLifecycleAndStateMachine`: Full REST lifecycle against real PostgreSQL.
- `SecurityIncidentIntegrationTest.testAutoIncidentCreationFromDomainEvent`: Domain event auto-creation verification.
- `SecurityIncidentIntegrationTest.testDuplicateEventSuppression`: Database constraint duplicate event suppression.
- `SecurityIncidentIntegrationTest.testCrossWorkspaceIsolation`: Cross-tenant IDOR attack rejection.
- `SecurityIncidentIntegrationTest.testPlaybookDryRunPreview`: REST dry-run preview verification.
- `SecurityIncidentIntegrationTest.testPlaybookLiveExecutionContainment`: REST containment playbook execution.
- `SecurityIncidentIntegrationTest.testAuditTrailIntegrity`: Verification of audit trail persistence in `audit_logs`.

### 2. Phase 13 Regression & Related Module Verification
Command:
```powershell
mvn.cmd test "-Dtest=SecurityIncident*,AutomationPolicyEngineIntegrationTest,HardenedWebhookTransportTest"
```
Results:
```
[INFO] Running com.secretvault.automation.AutomationPolicyEngineIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 16.48 s
[INFO] Running com.secretvault.incident.SecurityIncidentIntegrationTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.922 s
[INFO] Running com.secretvault.incident.SecurityIncidentServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.929 s
[INFO] Running com.secretvault.webhook.HardenedWebhookTransportTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.907 s
[INFO] Results:
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS (Total time: 49.667 s)
```

### 3. Frontend Operations View Test Suite
Command:
```bash
npm test -- --run
```
Results:
```
Test Files  18 passed (18)
     Tests  98 passed (98)
  Duration  11.23s
```

---

## E. Real-World Readiness Assessment

### Production-Grade Strengths
1. **End-to-End Traceability**: Security incidents link back to root-cause domain events, record subsequent correlated events, and document remediation events with explicit causation attribution.
2. **Defensive Separation of Duties**: Critical remediation actions can be routed through four-eyes approvals where self-approval and creator bypass are strictly forbidden.
3. **Workspace-Scoped Multi-Tenancy**: All database queries, repository constraints, and API operations enforce strict workspace boundaries. Cross-workspace IDOR access attempts return 404 or 403.
4. **Idempotency & Replay Protection**: Both the unique constraint `uq_incident_event (incident_id, event_id)` and the guarded atomic update queries protect against duplicate event linkage and race conditions.
5. **Operator Ergonomics**: The frontend operations view provides incident timeline inspection, status triage modals, correlated event views, and an interactive playbook runner with dry-run safety previews.

### What Remains Unverified Locally & Recommended Next Steps
1. **Live Cloud Provider Revocation**: In the local development environment, external provider secret revoking (e.g., AWS IAM keys, Azure Key Vault, HashiCorp Vault) is verified via local integration mocks. Live cloud validation across actual hyperscaler APIs requires staging credentials.
2. **High-Volume Event Storm Benchmark**: While duplicate event suppression and rate budgeting are verified, a performance stress test with 10,000 concurrent events per second through the outbox dispatcher should be performed on dedicated performance environments.
3. **Automated Operator Paging**: The `NOTIFY_RESPONDERS` action currently produces in-app notifications and webhook dispatches. Integration with external incident paging systems (e.g. PagerDuty, Opsgenie) should be configured through the hardened webhook platform.

---

## F. Audit Summary

| Metric | Status / Value |
| :--- | :--- |
| **Existing Capabilities Confirmed** | Entities, V18 migrations, Outbox event dispatcher, REST endpoints, UI views |
| **Defects Identified & Remediated** | 5 confirmed defects (RBAC bypass, validation crash, greedy correlation, transition validation, missing playbook endpoint) |
| **Backend Tests Executed** | 24 tests passed (14 targeted incident tests, 5 automation engine tests, 5 webhook tests) |
| **Frontend Tests Executed** | 98 tests passed across 18 test suites |
| **Overall Posture** | **PASS — Implemented, Hardened & Locally Verified** |
