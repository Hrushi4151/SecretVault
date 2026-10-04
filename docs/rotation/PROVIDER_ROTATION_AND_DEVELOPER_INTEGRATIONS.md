# SecretVault — Phase 14 Member 2 Integration Documentation
## Provider Credential Rotation, Developer Platform & SDK Heartbeat

### Overview
This document details the Member 2 (Integrations & Developer Platform) deliverables for SecretVault Phase 14, closing the ecosystem integration gaps surrounding the 21-state secret rotation lifecycle engine.

---

### 1. Provider Credential Rotation Architecture & Push Lifecycle

During the `ACTIVATING` phase of automated secret rollover, the rotation engine invokes `ProviderCredentialRotator` to synchronize newly generated credentials to mapped cloud platforms.

```
       Rotation Activation (RotationService)
                         │
                         ▼
        RotationProviderPushEvent (Contract)
      [secretId, version, mappingId, jobId, wsId]
           (ZERO plaintext / keys / tokens)
                         │
                         ▼
             ProviderCredentialRotator
       [Resolves mapping, decrypts in-memory]
                         │
                         ▼
                  ProviderAdapter
        ┌────────────────┴────────────────┐
        ▼                                 ▼
   VercelAdapter                     RenderAdapter
(PATCH/POST /v10/env)             (PUT /env-vars/{key})
        │                                 │
        ▼                                 ▼
   External Vercel                  External Render
        │                                 │
        └────────────────┬────────────────┘
                         ▼
          Verification & Bounded Retry
             (Up to 3x with backoff)
                         │
                         ▼
         Audit & Security Event Emission
     (PROVIDER_SECRET_PUSHED / FAILED)
```

#### Provider Credential Security Boundary & Event Invariant
- **`RotationProviderPushEvent` Contract:**
  - Carries strictly non-sensitive metadata: `secretId`, `versionNumber`, `providerMappingId`, `rotationJobId`, `workspaceId`, `timestamp`.
  - **CRITICAL SECURITY INVARIANT:** Contains NO plaintext secret, generated password, DEK, KEK, provider access token, or credential material.
  - Server-side asynchronous listeners decrypt secret versions and provider credentials strictly in memory and immediately wipe secret buffers (`Arrays.fill(secretBytes, (byte) 0)`).

#### Authoritative Execution Flow & Durable Idempotency Model
1. **Authoritative Execution Path**:
   - `RotationService` invokes `ProviderCredentialRotator.activate(job, targetVersion)` synchronously during the `ACTIVATING` lifecycle phase.
   - Upon successful provider mutation, `ProviderCredentialRotator` durably persists an `EventProcessingLog` record in the database before publishing `RotationProviderPushEvent`.
   - Asynchronous outbox worker listeners (`@EventListener handleRotationProviderPush`) query the durable log (`existsByEventIdAndConsumerName`) to avoid double execution while guaranteeing decoupled delivery fallback if synchronous activation was deferred.
2. **Durable Idempotency Key**:
   - Primary DB uniqueness constraint: `event_id` = `rotationJobId` (or deterministic SHA-256/UUID of `secretId:mappingId:version`), `consumer_name` = `"PROVIDER_PUSH:" + providerMappingId + ":v" + targetVersion`.
   - Enforced by DB-level uniqueness constraint `uq_event_consumer` on `event_processing_log(event_id, consumer_name)`.
3. **Delivery & Execution Semantics**:
   - **At-Least-Once Delivery + Idempotent Execution**: Events may be delivered multiple times (e.g. outbox redelivery, worker failover, JVM restart), but delivery is guaranteed to execute the underlying cloud mutation at most once per version.
4. **Retry & Failure Semantics**:
   - **Transient Failures (5xx, rate limits, timeouts)**: Retried with bounded exponential backoff (max 3 attempts). No durable log entry is recorded until external mutation succeeds.
   - **Permanent Failures (401, 403, 400)**: Fail fast without retry, emit failure audit log, and fail the rotation job.
   - **Process Crash / Restart**: Upon worker recovery or outbox redelivery, checking the durable DB log prevents duplicate mutations.
5. **Provider-Level Idempotency**:
   - Secondary safety layer via provider-specific HTTP semantics (e.g., Vercel PATCH/POST upsert by environment key, Render PUT /env-vars/{key} idempotency).
6. **In-Memory Cache Role**:
   - `recentlyPushedKeys` `ConcurrentHashMap` acts strictly as an in-memory latency optimization to short-circuit hot event loops. Authoritative correctness is always backed by persistent database transactions.

---

### 2. Supported vs. Unsupported Cloud Providers

| Provider | Status | Mechanism | Isolation & Security |
| :--- | :--- | :--- | :--- |
| **Vercel** | **Supported** | `VercelProviderAdapter.pushSecret()`: Lists existing env vars by target env (`production`/`preview`/`development`). Issues `PATCH /v10/projects/{id}/env/{envId}` or `POST /v10/projects/{id}/env` with `type: "encrypted"`. Team-scoped or user-scoped authentication via decrypted bearer token. | Workspace tenant isolation; project-scoped; TLS only; zero plaintext logging. |
| **Render** | **Supported** | `RenderProviderAdapter.pushSecret()`: Issues `PUT /services/{id}/env-vars/{key}` with fallback to batch `PUT /services/{id}/env-vars` on 404. Service-scoped authentication via decrypted API key. | Workspace tenant isolation; service-scoped; TLS only; zero plaintext logging. |
| **GitHub** | **NOT AVAILABLE** | `GITHUB PROVIDER ROTATION: NOT AVAILABLE — NO EXISTING PROVIDER ADAPTER`<br>While `ProviderType.GITHUB` is defined in the enum taxonomy, no underlying `GitHubProviderAdapter` foundation exists in the repository. As per architectural specifications, no stub adapter was fabricated. | N/A |

---

### 3. Provider Failure Semantics & Bounded Retry

Provider rotation strictly categorizes operation responses using `ProviderErrorCode`:

| Failure Category | Error Codes | Handling Strategy |
| :--- | :--- | :--- |
| **Permanent / Auth Failures** | `PROVIDER_AUTHENTICATION_FAILED`<br>`PROVIDER_AUTHORIZATION_FAILED`<br>`PROVIDER_INVALID_REQUEST`<br>`PROVIDER_UNSUPPORTED_CAPABILITY` | **Fail closed immediately without retry.** Records `AuditAction.PROVIDER_OPERATION_FAILED`. Throws `IllegalStateException` to fail the rotation job. Plaintext credentials are never included in error messages. |
| **Transient Failures** | `PROVIDER_RATE_LIMITED`<br>`PROVIDER_TIMEOUT`<br>`PROVIDER_UNAVAILABLE`<br>Network socket timeouts / 502 / 503 | **Bounded exponential backoff retry.** Up to `maxRetries` (bounded to 3 attempts). Backoff delay calculated as `100ms * 2^(attempt - 1)`. If all retries fail, records failure audit and fails the rotation job. |
| **Success Verification** | N/A | Requires HTTP 200/201 response containing valid provider secret ID. Emits `AuditAction.PROVIDER_SECRET_PUSHED` and `SecurityEventType.PROVIDER_SECRET_PUSHED`. |

---

### 4. SDK Background Consumer Heartbeat Daemon

Workloads and microservices consuming SecretVault secrets must periodically announce their runtime health and acknowledge their currently active secret version so the rotation engine can verify zero-downtime rollover before terminating previous credentials.

#### Architecture
- **Location:** `sdk/secretvault-sdk-core/src/main/java/io/secretvault/sdk/consumer/`
  - `ConsumerHeartbeatConfig`: Validates consumer identity, interval, and version parameters.
  - `ConsumerHeartbeatDaemon`: Manages the background heartbeat daemon loop.
  - `ConsumerHeartbeatResult`: Records the execution status of individual heartbeats.

#### Lifecycle & Concurrency
```
SDK Initialized (SecretVaultClient.create(config))
                      │
                      ▼
   ConsumerHeartbeatDaemon Instantiated
   - Daemon thread: 'secretvault-consumer-heartbeat'
   - Idempotent start()
                      │
                      ▼
   Periodic Heartbeat Scheduled Task
   - Interval: Configurable (default 30s)
   - Payload: { currentAcknowledgedVersion, sdkVersion, runtimeFramework }
   - Zero secret values / keys / tokens
                      │
                      ▼
   Secret Rotation / Version Update
   - daemon.updateAcknowledgedVersion(newVersion)
   - Subsequent heartbeat informs backend of cutover
                      │
                      ▼
   Graceful Shutdown (client.close())
   - daemon.stop()
   - Await termination (3s) -> shutdownNow()
   - No thread leak, no JVM blockage
```

#### Configuration Example
```java
SdkConfig config = SdkConfig.builder()
    .endpoint("https://vault.internal.net")
    .token(System.getenv("SECRETVAULT_TOKEN"))
    .consumerHeartbeat(
        UUID.fromString("11111111-2222-3333-4444-555555555555"), // consumerId
        Duration.ofSeconds(30)                                    // interval
    )
    .build();

try (SecretVaultClient client = SecretVaultClient.create(config)) {
    // Secret consumption...
    // Dynamic version update upon rotation:
    client.getHeartbeatDaemon().ifPresent(d -> d.updateAcknowledgedVersion(2));
} // Daemon shuts down cleanly upon close()
```

#### Security Invariants
- **No Secret Material:** Payload contains only `currentAcknowledgedVersion`, `sdkVersion`, and `runtimeFramework`.
- **Fault-Tolerant:** Network failures and 401/403 authorization failures are handled safely and logged without throwing unhandled exceptions that could crash host application threads.

---

### 5. CLI Rotation Test Baseline Synchronization
The CLI Kubernetes and rotation contract test baseline was verified and harmonized:
- `cli/pom.xml` configured with `<argLine>-XX:+EnableDynamicAgentLoading -Dnet.bytebuddy.experimental=true</argLine>` for JDK 21+ Mockito dynamic agent compatibility.
- `KubernetesHelmContractTest` and `KubernetesCrdContractTest` verify Helm v2 specifications, structural CRD validation, and zero plaintext secret leakage across CLI templates.
- **Results:** 97/97 tests passing (100%).

---

### 6. Frontend Rotation Center Test Coverage
Dedicated unit and integration tests were established in `frontend/src/__tests__/RotationCenterView.test.jsx`:
- **Overview Dashboard:** Verifies rendering of Active Rotations, Runtime Leases, and Registered Consumers metrics.
- **Tab Switching:** Policies, Execution Jobs, Secret Leases, Workloads & SDKs, and Rotation Wizard navigation.
- **Emergency Remediation:** Verifies compromise modal opening, incident description input, and invocation of `rotationApi.markCompromised()`.
- **Dependency Telemetry:** Verifies `RotationImpactModal` telemetry graph and affected workloads.
- **Error Handling:** Verifies non-blocking failure recovery when backend job listing fails.
- **Masking Security:** Verifies sensitive secret patterns (`sk_live_`, `postgres://`, `ghp_`) never appear in plaintext in the DOM.
- **Results:** 11/11 tests passing (100%).

---

### 7. Live vs. Mock vs. Contract Testing Classification

| Component | Test File | Classification | Details |
| :--- | :--- | :--- | :--- |
| `ProviderCredentialRotator` | `ProviderCredentialRotatorTest.java` | **CONTRACT / MOCK** | Mocks `ProviderAdapter` and external cloud credentials. Validates zero-leakage event publishing, retry behavior, tenant isolation, and error code handling. |
| `VercelProviderAdapter` | `VercelProviderAdapterTest.java` | **MOCK** | Mocks Vercel API responses; tests env var listing, updating, creation, rate limits, and 401 handling. |
| `RenderProviderAdapter` | `RenderProviderAdapterTest.java` | **MOCK** | Mocks Render API responses; tests env var PUT, batch upsert fallback, and 403 handling. |
| `ConsumerHeartbeatDaemon` | `ConsumerHeartbeatDaemonTest.java` | **CONTRACT / INTEGRATION** | Uses mock HTTP client to verify scheduler start, stop, restart, version acknowledgement, and payload invariants. |
| CLI Kubernetes Contracts | `KubernetesHelmContractTest.java` | **CONTRACT** | Validates Helm chart templates against Kubernetes schema without live cluster. |
| Frontend Rotation Center | `RotationCenterView.test.jsx` | **MOCK / UNIT** | Vitest and React Testing Library; verifies UI interactions and modal flows. |

*Note: LIVE provider validation was NOT executed because real external cloud credentials (Vercel/Render API tokens) are intentionally not stored in repository fixtures or CI environments.*
