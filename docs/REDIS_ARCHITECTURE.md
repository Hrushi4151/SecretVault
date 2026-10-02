# SecretVault Redis Infrastructure, Rate Limiting & Caching Architecture

## 1. Overview & Purpose

SecretVault is a DevSecOps Secret Management & Security Control Plane. In the SecretVault platform architecture, **PostgreSQL is the single authoritative source of truth for all persistent records**, including users, organizations, workspaces, projects, environments, secrets, secret versions, granular grants, JIT requests, access reviews, and audit trails.

**Redis operates strictly as a distributed coordination, acceleration, rate limiting, and short-lived security state layer.** It is **NEVER** a database of record and is **NEVER** the authoritative source of truth for authorization decisions.

```
                    ┌────────────────────────────────────────┐
                    │            SecretVault API             │
                    └───────────────────┬────────────────────┘
                                        │
             ┌──────────────────────────┼─────────────────────────┐
             │                          │                         │
             ▼                          ▼                         ▼
   DistributedRateLimiter      SecurityStateStore         SafeCacheService
             │                          │                         │
             └──────────────────────────┼─────────────────────────┘
                                        ▼
                                      Redis
                                        │
             ┌──────────────────────────┼─────────────────────────┐
             │                          │                         │
             ▼                          ▼                         ▼
      Atomic Counters            Short-Lived State       Safe Metadata Cache
     (Rate Limiting)             (MFA Challenges)         (Cache-Aside DTOs)
```

---

## 2. Prohibited Redis Storage (Zero-Secret Policy)

Under **NO circumstances** shall Redis store sensitive secrets or credentials. The following items are **strictly prohibited** from Redis storage:
- Plaintext SecretVault secret values or payload contents
- Decrypted secret versions
- Data Encryption Keys (DEKs) or Key Encryption Keys (KEKs)
- TOTP secrets or master seed keys
- User passwords or password hashes
- MFA recovery codes
- Cloud or provider API keys and client secrets
- Plaintext access tokens or unhashed refresh tokens
- Private cryptographic keys

All secret payloads and encryption operations remain isolated in-memory and persisted exclusively in PostgreSQL using AES-256-GCM envelope encryption.

---

## 3. Centralized Key Naming & Environment Isolation

All Redis keys are constructed using [RedisKeyBuilder](file:///d:/CodePlayground/JAVA%20SpringBoot/SecureVault/backend/src/main/java/com/secretvault/common/redis/RedisKeyBuilder.java). Raw key string concatenation is strictly prohibited across services.

### Uniform Key Syntax
```
secretvault:{environment}:{domain}:{category}:{identifier}
```

| Domain | Key Pattern | Example | TTL Policy |
| :--- | :--- | :--- | :--- |
| **Rate Limit** | `secretvault:{env}:ratelimit:{category}:{identifier}` | `secretvault:prod:ratelimit:auth_login:ip_192.168.1.1` | 60 seconds |
| **Security State** | `secretvault:{env}:security:{category}:{identifier}` | `secretvault:prod:security:mfa_challenge:chal-c1a2-3b4c` | 300 seconds (5 min) |
| **Safe Cache** | `secretvault:{env}:cache:{domain}:{identifier}` | `secretvault:prod:cache:workspace:ws-8888-9999` | 300 seconds (5 min) |
| **Idempotency** | `secretvault:{env}:idempotency:{operation}:{key}` | `secretvault:prod:idempotency:secret_create:idem-555` | 120 seconds (2 min) |

Environment isolation guarantees that staging, development, and production deployments sharing a Redis cluster cannot collide or read each other's keys.

---

## 4. Distributed Rate Limiting

Distributed rate limiting is enforced via [RedisRateLimiter](file:///d:/CodePlayground/JAVA%20SpringBoot/SecureVault/backend/src/main/java/com/secretvault/common/ratelimit/RedisRateLimiter.java) using an **atomic Lua script**:

```lua
local current = redis.call('INCR', KEYS[1])
if tonumber(current) == 1 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
end
local ttl = redis.call('TTL', KEYS[1])
if ttl < 0 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
    ttl = tonumber(ARGV[1])
end
return { current, ttl }
```

### Rate-Limited Endpoints
1. **Authentication**:
   - `POST /api/v1/auth/login`: 10 requests / 60s per client IP
   - `POST /api/v1/auth/refresh`: 30 requests / 60s per client IP
2. **Sensitive Secret Operations**:
   - `POST /api/v1/.../secrets/{secretId}/reveal`: 60 requests / 60s per IP + User
   - `POST /api/v1/.../secrets/{secretId}/rollback`: 20 requests / 60s per IP + User
   - `POST /api/v1/.../promote`: 20 requests / 60s per IP + User
3. **Governance & JIT**:
   - `POST /api/v1/.../jit/requests`: 20 requests / 60s per User
   - `POST /api/v1/.../jit/requests/{id}/approve`: 30 requests / 60s per User
4. **Invitations**:
   - `POST /api/v1/workspaces/{id}/invitations`: 20 requests / 60s per Workspace
   - `POST /api/v1/invitations/accept`: 15 requests / 60s per IP

### Exceeded Rate Limit Response
When a limit is exceeded, the server immediately returns **HTTP 429 Too Many Requests** with standard sanitized error JSON and a standard `Retry-After` header indicating seconds until reset:
```json
{
  "status": 429,
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests. Please try again later.",
  "requestId": "5a4f78de-..."
}
```

---

## 5. Short-Lived Security State Store

[RedisSecurityStateStore](file:///d:/CodePlayground/JAVA%20SpringBoot/SecureVault/backend/src/main/java/com/secretvault/common/security/state/RedisSecurityStateStore.java) provides atomic, short-lived security state coordination for upcoming MFA authentication challenges, step-up nonces, and email verification tokens:

- **Single-Use Atomic Consumption (`consumeAtomic`)**:
  Executes an atomic Lua script that reads and immediately deletes the token in a single Redis transaction. Two concurrent verification requests can never succeed on the same challenge token.
- **Attempt Tracking (`incrementAttempts`)**:
  Atomically tracks failed challenge verification attempts with TTL expiration to prevent brute force attacks against short-lived nonces.
- **Fail-Closed Principle**:
  If Redis is unresponsive, security state lookups return empty / failure. The platform **never fails open** to bypass authentication.

---

## 6. Safe Application Metadata Caching

[RedisSafeCacheService](file:///d:/CodePlayground/JAVA%20SpringBoot/SecureVault/backend/src/main/java/com/secretvault/common/cache/RedisSafeCacheService.java) implements a cache-aside pattern exclusively for non-sensitive, read-heavy summary metadata:
- Cached entities are strictly DTOs (`WorkspaceSummaryCacheDto`, `ProjectSummaryCacheDto`, `EnvironmentSummaryCacheDto`).
- Direct caching of JPA entities or Hibernate proxies is forbidden.
- On record update or deletion in PostgreSQL, cache entries are explicitly evicted (`safeCacheService.evict(...)`).
- On Redis outage, metadata calls seamlessly fall back to PostgreSQL.

### Authorization Caching Rule
**Dynamic authorization decisions generated by `EffectiveAccessService` are NEVER cached in Redis.** Real-time project access overrides, environment permission caps, granular access grants, and temporary JIT grants must evaluate directly against PostgreSQL to guarantee zero stale privilege escalation.

---

## 7. Redis Failure & Outage Matrix

| Subsystem | Failure Behavior | Rationale |
| :--- | :--- | :--- |
| **MFA Challenge / Security State** | **FAIL-CLOSED** (Deny/Error) | Under no circumstances should a Redis outage allow an unverified authentication bypass. |
| **Authorization (`EffectiveAccessService`)** | **FAIL-CLOSED / Live DB** | Evaluated live from PostgreSQL; unaffected by Redis availability. |
| **Secret Retrieval & Reveal** | **Live DB + KMS Decryption** | Direct PostgreSQL + envelope decryption; zero Redis dependency for secrets. |
| **Rate Limiting** | **Configurable Policy** | Configurable via `secretvault.redis.rate-limit.fail-open` (Default: `false` in production, `true` in test). |
| **Metadata Caching** | **Graceful DB Fallback** | Cache misses or Redis errors seamlessly query PostgreSQL. |

---

## 8. Serialization Security

- Key Serializer: `StringRedisSerializer` (UTF-8)
- Value Serializer: `GenericJackson2JsonRedisSerializer` configured with secure Jackson typing (`LaissezFaireSubTypeValidator` with `NON_FINAL` properties)
- Java Native Serialization (`ObjectInputStream` / `Serializable` byte streams) is **explicitly disabled** to prevent remote code execution (RCE) and deserialization vulnerabilities.

---

## 9. Observability & Health

- **Actuator Health Check**: [RedisHealthIndicator](file:///d:/CodePlayground/JAVA%20SpringBoot/SecureVault/backend/src/main/java/com/secretvault/common/observability/RedisHealthIndicator.java) executes `PING` and returns `AVAILABLE` or `UNAVAILABLE` without leaking connection URLs, hostnames, or credentials.
- **Sanitized Micrometer Metrics**:
  - `secretvault.redis.ratelimit.allowed` (tags: `category`)
  - `secretvault.redis.ratelimit.denied` (tags: `category`)
  - `secretvault.redis.cache.hit` (tags: `domain`)
  - `secretvault.redis.cache.miss` (tags: `domain`)
  - `secretvault.redis.security_state.put` (tags: `category`)
  - `secretvault.redis.security_state.consumed` (tags: `category`)
  - `secretvault.redis.failure` (tags: `operation`)
