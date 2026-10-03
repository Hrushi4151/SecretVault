# Java SDK & Spring Boot Integration for Phase 13 Events

## 1. Overview

The SecretVault Java SDK (`secretvault-sdk-core`) and Spring Boot Starter (`secretvault-spring-boot-starter`) allow microservices to react to domain events and rotation notifications in real-time.

## 2. Event Consumption via SDK

Microservices can listen to secret rotations and compromise events:

```java
SecretVaultClient client = SecretVaultClient.builder()
        .baseUrl("https://vault.internal.net")
        .token("sv_token_...")
        .build();

// Register listener for secret rotation
client.registerSecretChangeListener("DATABASE_PASSWORD", (event) -> {
    log.info("Received rotation event for version {}", event.getVersion());
    // Invalidate local connection pool and re-authenticate
    dataSource.refreshCredentials(event.getNewValue());
});
```

## 3. Spring Boot Automatic Refresh

When using `secretvault-spring-boot-starter`, beans annotated with `@ConfigurationProperties` automatically reload when corresponding rotation events are received:

```yaml
secretvault:
  server-url: https://vault.internal.net
  workspace: my-workspace
  project: payment-service
  environment: production
  events:
    enabled: true
    heartbeat-interval-seconds: 60
```

Heartbeats are sent automatically from background threads, maintaining consumer status as `HEALTHY` in the Secret Consumer Registry.
