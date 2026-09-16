# SecretVault — Client SDK Specification

## 1. SDK Architecture & Supported Languages

SecretVault client SDKs enable applications to securely retrieve, cache, and rotate secrets dynamically at runtime over encrypted HTTPS connections.

### Supported Language Roadmap [PLANNED]:
- **Java / Spring Boot** (`secretvault-java-sdk` / `secretvault-spring-boot-starter`)
- **TypeScript / Node.js** (`@secretvault/sdk`)
- **Python** (`secretvault-python`)
- **Go** (`github.com/secretvault/sdk-go`)

---

## 2. Machine Identity & Authentication

SDK clients authenticate exclusively via **Machine Identities**:
1. **Service Account Tokens:** Short-lived or scoped bearer tokens (`SECRETVault_SERVICE_TOKEN`).
2. **Workload Identity Federation (OIDC):** Ephemeral tokens exchanged dynamically from Kubernetes, AWS IAM, or GitHub Actions.

> **Rule:** SDKs must never use long-lived human developer credentials.

---

## 3. Java SDK Code Pattern Example [PLANNED]

```java
SecretVaultClient client = SecretVaultClient.builder()
    .serviceToken(System.getenv("SECRETVault_TOKEN"))
    .environment("production")
    .cacheTtl(Duration.ofMinutes(15))
    .build();

// Retrieve secret with automatic in-memory caching
String dbPassword = client.getSecret("DATABASE_PASSWORD");

// Subscribe to real-time secret rotation events
client.onSecretRotated("DATABASE_PASSWORD", newSecretValue -> {
    dataSource.updatePassword(newSecretValue);
});
```

---

## 4. Resilience & Graceful Degradation

- **In-Memory Cache:** Secret values are cached in RAM with a configurable TTL to prevent unnecessary network overhead.
- **Fail-Safe Cache:** If SecretVault is temporarily unreachable during a refresh cycle, the SDK serves the cached value and logs a non-fatal warning without crashing the host application.
- **Safe Memory Zeroing:** Implements memory wiping upon client shutdown where language runtimes allow.
