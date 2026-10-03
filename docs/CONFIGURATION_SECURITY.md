# SecretVault — Configuration Security & Environment Architecture

## Overview
This document specifies the security policies, configuration profiles, environment variables, and fail-fast guarantees implemented across SecretVault.

---

## 1. Zero-Trust Configuration Principles

1. **No Insecure Production Fallbacks:** Production deployments must NEVER rely on compiled-in or default secrets. If required security secrets or database credentials are missing from the environment, the application must immediately fail fast on startup with a descriptive error.
2. **Strict Profile Isolation:** Development and test defaults are strictly quarantined to the `local` and `test` Spring profiles and cannot leak into base or production runtime configurations.
3. **256-Bit Cryptographic Integrity:** All Key Encryption Keys (KEK / `VAULT_MASTER_KEY`) and JWT signing secrets (`JWT_SECRET`) must satisfy a minimum 256-bit (32 bytes) length requirement.
4. **Zero Secrets in Source Control:** No database passwords, production master keys, or external provider API tokens may be committed to Git.

---

## 2. Environment Variables & Production Requirements

In production environments, the following environment variables are mandatory:

| Environment Variable | Target Configuration Property | Minimum Requirements | Description |
| :--- | :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` or `DB_URL` | `spring.datasource.url` | Valid PostgreSQL JDBC URL | PostgreSQL cluster connection endpoint. |
| `SPRING_DATASOURCE_USERNAME` or `DB_USER` | `spring.datasource.username` | Non-empty string | Database user with scoped vault privileges. |
| `SPRING_DATASOURCE_PASSWORD` or `DB_PASSWORD` | `spring.datasource.password` | Strong password | Database user password. |
| `VAULT_MASTER_KEY` | `secretvault.security.master-key` | Exactly 256-bit (32 bytes) Base64 string | Master Key Encryption Key (KEK) used to wrap Data Encryption Keys (DEKs). |
| `JWT_SECRET` | `secretvault.security.jwt.secret` | Minimum 256 bits (32 bytes) UTF-8 string | HMAC-SHA256 signing key for authentication tokens. |
| `REDIS_HOST` | `spring.data.redis.host` | Valid hostname/IP | Redis host for rate-limiting and challenge state. |
| `REDIS_PORT` | `spring.data.redis.port` | Valid port (default 6379) | Redis port. |
| `REDIS_PASSWORD` | `spring.data.redis.password` | Optional/Required | Redis authentication password. |

---

## 3. Configuration Profiles

### 3.1 Base Configuration (`application.yml`)
- Serves as the production-first baseline.
- Declares environment variable bindings without hardcoded database passwords, master keys, or JWT secrets.
- Unset required variables trigger fail-fast validation in `LocalDevKmsKeyProvider` and `JwtTokenProvider`.

### 3.2 Local Development Profile (`application-local.yml`)
- Activated with `SPRING_PROFILES_ACTIVE=local` (the default dev profile).
- Configures connections to local Docker Compose services:
  - Database: `jdbc:postgresql://localhost:5432/secretvault_dev` (`vault_user` / `vault_secure_password_dev_only`)
  - Redis: `localhost:6379`
- Provides pre-configured 256-bit development keys strictly isolated to local environments.

### 3.3 Automated Test Profile (`application-test.yml`)
- Activated with `SPRING_PROFILES_ACTIVE=test`.
- Uses in-memory H2 database (`jdbc:h2:mem:testdb;MODE=PostgreSQL`) with Flyway disabled for lightning-fast test isolation.
- Supplies deterministic 256-bit test-only keys (`secretvault.security.master-key` and `jwt.secret`).

---

## 4. Fail-Fast Validation Subsystems

### 4.1 Master Key Encryption Key Validation (`LocalDevKmsKeyProvider.java`)
```java
@PostConstruct
public void init() {
    if (!StringUtils.hasText(masterKeyConfig)) {
        throw new IllegalStateException("CRITICAL: Master Key Encryption Key (VAULT_MASTER_KEY) is not configured");
    }
    byte[] keyBytes = Base64.getDecoder().decode(masterKeyConfig.trim());
    if (keyBytes.length != 32) {
        throw new IllegalStateException("CRITICAL: Master KEK must be exactly 256 bits (32 bytes). Found: " + keyBytes.length + " bytes");
    }
    this.kekSpec = new SecretKeySpec(keyBytes, "AES");
}
```

### 4.2 JWT Secret Key Validation (`JwtTokenProvider.java`)
```java
public JwtTokenProvider(
        @Value("${secretvault.security.jwt.secret:}") String jwtSecret,
        @Value("${secretvault.security.jwt.expiration-seconds:86400}") long expirationSeconds) {
    if (!StringUtils.hasText(jwtSecret)) {
        throw new IllegalStateException("CRITICAL: JWT signing secret (JWT_SECRET / secretvault.security.jwt.secret) is not configured");
    }
    byte[] keyBytes = jwtSecret.trim().getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
        throw new IllegalStateException("CRITICAL: JWT signing secret must be at least 256 bits (32 bytes). Found: " + keyBytes.length + " bytes");
    }
    this.key = Keys.hmacShaKeyFor(keyBytes);
    this.expirationSeconds = expirationSeconds;
}
```

---

## 5. Secret Rotation & Key Lifecycle
- **KEK / Master Key Rotation:** To rotate the Master KEK, configure the new KMS provider and execute re-wrap operations across versioned DEKs in `secret_versions`.
- **JWT Key Rotation:** Updating `JWT_SECRET` invalidates active access tokens; users seamlessly refresh access via hashed, rotated refresh tokens in `refresh_tokens`.
- **Zero-Plaintext Guarantee:** Plaintext secret values are never persisted in PostgreSQL, Redis, or log outputs.
