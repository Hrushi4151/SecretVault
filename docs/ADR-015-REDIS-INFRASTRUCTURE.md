# ADR-015: Redis Infrastructure, Distributed Rate Limiting & Safe Caching Architecture

## Status
Accepted

## Context
SecretVault operates as an enterprise DevSecOps Secret Management and Security Control Plane. As the platform scales across multiple backend application instances, it requires:
1. Distributed, atomic rate limiting to protect authentication, MFA verification, and sensitive secret mutation APIs from brute force and denial of service.
2. An atomic, short-lived security state store for ephemeral challenge tokens (MFA challenges, step-up nonces) with replay attack prevention.
3. Safe application metadata caching to accelerate read-heavy non-sensitive endpoints without risking stale authorization decisions or secret leakage.

## Decision Drivers
- **Zero-Secret Invariant**: Secret plaintext, master keys, DEKs, TOTP secrets, passwords, and tokens must never be written to Redis.
- **Authoritative Source of Truth**: PostgreSQL remains the sole source of truth for persistent records and authorization decisions.
- **Stale Authorization Prevention**: Dynamic authorization decisions (`EffectiveAccessService`) must not be cached in Redis.
- **Fail-Closed Security**: Outages in the coordination layer must never allow unauthenticated access or privilege escalation.
- **Replay Protection**: Ephemeral security challenges must be atomically consumed in a single transaction.

## Architectural Decisions

### 1. Unified Redis Role
Redis is designated strictly as an **ephemeral coordination, distributed rate limiting, and acceleration layer**. PostgreSQL remains the system of record.

### 2. Centralized Key Builder with Environment Isolation
All keys are constructed via `RedisKeyBuilder` using the syntax:
`secretvault:{environment}:{domain}:{category}:{identifier}`

### 3. Atomic Rate Limiting via Lua Script
Rate limiting uses an atomic Redis Lua script (`INCR` + `EXPIRE` + `TTL`) returning HTTP 429 `RATE_LIMIT_EXCEEDED` and a standard `Retry-After` header when thresholds are exceeded.

### 4. Atomic Single-Use Security State Store
Ephemeral security tokens for MFA and step-up auth are managed via `RedisSecurityStateStore` using an atomic `GET-and-DEL` Lua script (`consumeAtomic`).

### 5. Safe Cache-Aside for Non-Sensitive Metadata Only
Application metadata caching (`RedisSafeCacheService`) is restricted to explicit summary DTOs (`WorkspaceSummaryCacheDto`, `ProjectSummaryCacheDto`, `EnvironmentSummaryCacheDto`). Real-time authorization decisions from `EffectiveAccessService` are explicitly excluded from caching.

### 6. Deserialization Safety
Java native serialization (`ObjectInputStream`) is forbidden. All values are serialized as JSON via `GenericJackson2JsonRedisSerializer` with strict type validation.

## Consequences

### Positive
- Cross-instance distributed throttling protects critical login and secret mutation endpoints.
- Single-use challenge tokens prevent replay attacks against MFA and step-up authentication.
- PostgreSQL read load is reduced for static metadata while maintaining 100% dynamic, up-to-date authorization enforcement.
- Sanitized health indicators and metrics provide observability without credential leakage.

### Negative / Trade-offs
- Slight operational dependency on Redis health (mitigated by graceful DB fallback for metadata and configurable fail-open/closed policies for rate limiting).
- Cache invalidation logic required across workspace, project, and environment write operations.
