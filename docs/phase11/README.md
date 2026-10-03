# SecretVault Phase 11 — Runtime SDK & Consumption Platform

## 1. Overview

SecretVault Phase 11 delivers the enterprise-grade runtime secret consumption platform, providing the official **Java 21 SDK Core** (`io.secretvault:secretvault-sdk-core:1.0.0`) and **Spring Boot Starter** (`io.secretvault:secretvault-spring-boot-starter:1.0.0`).

Phase 11 eliminates the need for:
- Static `.env` files committed to disk
- Hardcoded credentials in source code or `application.yml`
- Bypassing SecretVault's authoritative `EffectiveAccessService` backend authorization pipeline
- Leaking secrets in application logs, Actuator endpoints, or stack traces

---

## 2. Architecture & Modules

```
sdk/
├── secretvault-sdk-core/
│   ├── auth/          (Static, Machine, OIDC, Environment, Chain)
│   ├── client/        (Java 21 HttpClient, retry, circuit breaker, single-flight coalescing)
│   ├── cache/         (In-memory bounded LRU cache, multi-tenant isolation, TTL)
│   ├── resilience/    (Fail-closed vs fail-open with bounded stale cache)
│   ├── model/         (SecretValue with redacted toString(), metadata, versions)
│   ├── api/           (SecretVaultClient, SecretsApi fluent interface)
│   └── observability/ (SLF4J redaction, metrics instrumentation)
│
└── secretvault-spring-boot-starter/
    ├── properties/    (SecretVaultProperties @ConfigurationProperties)
    ├── env/           (SecretVaultEnvironmentPostProcessor & PropertySource)
    ├── annotation/    (@SecretVaultValue bean post processor)
    ├── health/        (Actuator HealthIndicator without secret leakage)
    └── config/        (SecretVaultAutoConfiguration)
```

---

## 3. Quickstart

### Plain Java SDK

```java
SdkConfig config = SdkConfig.builder()
    .endpoint("http://localhost:8080")
    .credentials(new EnvironmentTokenProvider())
    .defaultScope("default", "payment-gateway", "production")
    .cacheEnabled(true)
    .cacheTtl(Duration.ofSeconds(60))
    .build();

try (SecretVaultClient client = SecretVaultClient.create(config)) {
    SecretValue dbPass = client.secrets().get("DB_PASSWORD");
    // dbPass.toString() outputs: SecretValue{name='DB_PASSWORD', version=1, value=[REDACTED]}
    String rawPass = dbPass.value();
}
```

### Spring Boot Starter (`application.yml`)

```yaml
secretvault:
  endpoint: https://vault.enterprise.internal
  workspace: default
  project: payment-gateway
  environment: production
  authentication:
    mode: oidc
    provider-id: 3c0782f3-0025-4e1e-a8b5-547686f3e9b6
    machine-id: 10f6ab12-6eb6-4c7a-9db2-6d2c4ebdae77
  cache:
    enabled: true
    ttl: 60s
  resilience:
    mode: FAIL_CLOSED

spring:
  datasource:
    password: ${secretvault:DB_PASSWORD}
```

---

## 4. Documentation Index

- [Architecture & Design](ARCHITECTURE.md)
- [Java SDK Reference](JAVA_SDK.md)
- [Spring Boot Starter Guide](SPRING_BOOT.md)
- [Authentication Strategies](AUTHENTICATION.md)
- [OIDC Workload Identity](OIDC_RUNTIME.md)
- [In-Memory Cache & Isolation](SECRET_CACHE.md)
- [Dynamic Secret Rotation](ROTATION.md)
- [Resilience & Failover](RESILIENCE.md)
- [Container & Docker Integration](DOCKER.md)
- [Kubernetes Architecture](KUBERNETES.md)
- [Serverless Readiness](SERVERLESS.md)
- [Security & Redaction](SECURITY.md)
- [Observability & Metrics](OBSERVABILITY.md)
- [Troubleshooting & Diagnostics](TROUBLESHOOTING.md)
- [Operations Guide](OPERATIONS.md)
- [Threat Model](THREAT_MODEL.md)
