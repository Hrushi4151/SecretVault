# SecretVault Phase 11 — Runtime Architecture

## 1. Core Principles

The SecretVault SDK is strictly a consumption client. It delegates 100% of identity verification and authorization decisions to the backend SecretVault platform.

```
Application Layer
       │
       ▼
SecretVault SDK (SecretVaultClient)
       │
       ├─► 1. In-Memory Cache (TTL / Multi-Tenant Isolation)
       ├─► 2. Request Coalescer (Single-Flight deduplication)
       ├─► 3. Circuit Breaker (Fast-fail protection)
       └─► 4. HTTP Client (Java 21 HttpClient + TLS Verification)
              │
              ▼
SecretVault Backend API
       │
       ├─► OIDC Workload Validation & Machine Identity Lookup
       ├─► Authorization via EffectiveAccessService
       ├─► Granular Grant, Environment Protection & JIT Policy Check
       ├─► Envelope Decryption (KMS / AES-256-GCM)
       ├─► Audit Logging (Sanitized Outcome)
       └─► Returns Plaintext Payload with Cache-Control: no-store
```

---

## 2. In-Memory Secret Model

- **Zero Disk Serialization**: Secrets are stored exclusively in heap memory inside `SecretCache`.
- **String & Buffer Access**: Plaintext values can be accessed as immutable `String`, mutable `char[]`, or UTF-8 `byte[]`.
- **Redacted toString()**: `SecretValue.toString()` always emits `value=[REDACTED]`.

---

## 3. Concurrency & High-Throughput Protection

1. **Single-Flight Request Coalescing**: When 100 concurrent threads request the same expired secret simultaneously, exactly 1 network call is issued; the other 99 threads await the same shared `CompletableFuture`.
2. **Circuit Breaking**: If consecutive backend calls fail, the client transitions to `OPEN` state, fast-failing subsequent requests to avoid thread stampedes.
3. **Exponential Backoff**: Transient HTTP 429 and 5xx errors are retried using exponential backoff with full jitter and `Retry-After` header compliance.
