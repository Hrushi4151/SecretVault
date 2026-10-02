# SecretVault — Provider Integration Framework Architecture & SPI Specification

> **Phase:** Phase 7 — Provider Integration Framework  
> **Role:** Member 2 — Backend / Provider Integration Owner  
> **Package:** `com.secretvault.infrastructure.provider` & `com.secretvault.application.provider`

---

## 1. Architectural Principles & Isolation Boundaries

The SecretVault Provider Integration Framework is designed around three non-negotiable architectural invariants:

1. **Provider Isolation:** Provider-specific API schemas, URLs, headers, and response formats are strictly encapsulated inside dedicated provider adapters. No provider-specific logic (e.g. Vercel project IDs or Render service shapes) is leaked into the core application layer or database schema.
2. **Normalized Abstractions:** All external operations are expressed via the generic `ProviderAdapter` Service Provider Interface (SPI).
3. **Defense-in-Depth Credential Security:** Credentials and secret values are never logged, never stored plaintext, and never held longer than necessary for individual HTTP dispatch.

```
                  ┌─────────────────────────────────────┐
                  │      ProviderIntegrationController   │
                  └──────────────────┬──────────────────┘
                                     │
              ┌──────────────────────┴──────────────────────┐
              ▼                                             ▼
┌───────────────────────────┐                 ┌───────────────────────────┐
│ ProviderIntegrationService│                 │ ProviderSecretSyncService │
└─────────────┬─────────────┘                 └─────────────┬─────────────┘
              │                                             │
              └──────────────────────┬──────────────────────┘
                                     │
                                     ▼
                       ┌───────────────────────────┐
                       │  ProviderAdapterRegistry  │
                       └─────────────┬─────────────┘
                                     │
                    ┌────────────────┴────────────────┐
                    ▼                                 ▼
       ┌────────────────────────┐        ┌────────────────────────┐
       │  VercelProviderAdapter │        │  RenderProviderAdapter │
       │      (Spring SPI)      │        │      (Spring SPI)      │
       └────────────┬───────────┘        └────────────┬───────────┘
                    │                                 │
                    ▼                                 ▼
       [ Vercel REST Client ]            [ Render REST Client ]
                    │                                 │
                    ▼                                 ▼
          https://api.vercel.com            https://api.render.com/v1
```

---

## 2. Core Service Provider Interface (SPI)

Every platform adapter implements the `ProviderAdapter` interface:

```java
public interface ProviderAdapter {
    ProviderType getProviderType();
    Set<ProviderCapability> getSupportedCapabilities();

    ProviderValidationResult validateConnection(ProviderContext context);
    List<ProviderDiscoveredResource> listProjects(ProviderContext context);
    ProviderDiscoveredResource getProject(ProviderContext context, String providerResourceId);
    List<ProviderDiscoveredEnvironment> listEnvironments(ProviderContext context, String providerResourceId);
    
    List<ProviderSecretMetadata> listSecrets(ProviderContext context, String providerResourceId, String providerEnvironment);
    ProviderSecretOperationResult writeSecret(ProviderContext context, ProviderSecretWriteRequest request);
    ProviderSecretOperationResult deleteSecret(ProviderContext context, ProviderSecretDeleteRequest request);
}
```

### 2.1 Provider Capabilities
Capabilities are explicitly declared by each adapter:

```java
public enum ProviderCapability {
    READ_SECRETS,
    WRITE_SECRETS,
    DELETE_SECRETS,
    LIST_PROJECTS,
    LIST_ENVIRONMENTS,
    DEPLOYMENT_TRIGGER,
    DRIFT_DETECTION,
    HEALTH_CHECK
}
```

If a caller invokes an unsupported capability, the framework throws a normalized `ProviderUnsupportedCapabilityException` rather than executing undefined API calls.

---

## 3. Normalized Error Handling

Third-party HTTP status codes and error responses are translated into standardized SecretVault domain exceptions:

| HTTP Status | Normalized Domain Exception | Normalized Error Code |
|---|---|---|
| `401 Unauthorized` | `ProviderAuthenticationException` | `PROVIDER_AUTHENTICATION_FAILED` |
| `403 Forbidden` | `ProviderAuthorizationException` | `PROVIDER_AUTHORIZATION_FAILED` |
| `404 Not Found` | `ProviderResourceNotFoundException` | `PROVIDER_RESOURCE_NOT_FOUND` |
| `409 Conflict` | `ProviderInvalidRequestException` | `PROVIDER_INVALID_REQUEST` |
| `429 Too Many Requests` | `ProviderRateLimitedException` | `PROVIDER_RATE_LIMITED` |
| `5xx Server Error` | `ProviderUnavailableException` | `PROVIDER_UNAVAILABLE` |
| Network Timeout / I/O | `ProviderTimeoutException` | `PROVIDER_TIMEOUT` |

Raw third-party response bodies, tokens, and authorization headers are intercepted and filtered to prevent internal infrastructure disclosure.

---

## 4. HTTP Client, Timeouts, and Rate Limits

Provider adapters utilize Spring 6 `RestClient` configured with strict, bounded timeouts:

- **Connect Timeout:** 5,000 ms (5 seconds)
- **Read Timeout:** 15,000 ms (15 seconds)
- **Retry Policy:** Bounded exponential backoff applied exclusively to idempotent operations (`GET`, `validateConnection`). Non-idempotent secret write requests are never retried blindly to prevent race conditions or duplicate variables.
- **Rate Limit Resilience:** When receiving `429 Too Many Requests`, adapters parse standard `Retry-After` / `X-RateLimit-Reset` headers and expose normalized `ProviderRateLimitedException`.

---

## 5. Adding a Future Provider Adapter (Extension Guide)

To integrate a new provider (e.g., `AWS`, `CLOUDFLARE`, `GITHUB`, `RAILWAY`):

1. **Update Enum:** Add the new provider identifier to `com.secretvault.domain.provider.ProviderType`.
2. **Implement SPI:** Create a new `@Component` class implementing `ProviderAdapter`:
   ```java
   @Component
   public class GitHubProviderAdapter implements ProviderAdapter {
       @Override
       public ProviderType getProviderType() {
           return ProviderType.GITHUB;
       }
       // Implement capabilities...
   }
   ```
3. **Register Adapter:** `ProviderAdapterRegistry` automatically auto-wires and registers all Spring components implementing `ProviderAdapter` on application startup.
4. **Add Unit & Mock Contract Tests:** Verify connection validation, discovery, secret writes, and error normalization using WireMock / Mockito without connecting to external networks.
5. **Add Flyway Updates (If needed):** Standard schema requires no alterations for new providers as the schema is 100% provider-agnostic.

---

## 6. Phase Boundaries

### 6.1 Phase 8 Boundary (Synchronization & Drift Engine)
- **Phase 7 Owns:** On-demand synchronous push/delete primitives, credential encryption, resource mappings, and discovery APIs.
- **Phase 8 Owns:** Asynchronous Redis sync queue, background worker reconcilers, periodic drift comparison engine, and drift remediation policies.

### 6.2 Phase 14 Boundary (Automated Secret Rotation)
- **Phase 7 Owns:** Atomic credential replacement API and safe single-variable updates.
- **Phase 14 Owns:** Automated rotation schedules, dual-key shadow deployment, and rotation rollback policies.
