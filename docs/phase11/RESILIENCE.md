# SecretVault SDK — Resilience & Outage Protection

## Resilience Policies

### 1. `FAIL_CLOSED` (Default)
If the SecretVault backend is down and a requested secret is not fresh in cache, the SDK throws `SecretVaultUnavailableException`.

### 2. `FAIL_OPEN_WITH_CACHE`
If the backend is unreachable, the SDK allows serving stale cache entries up to `maxStaleDuration` (default 5 minutes). Once `maxStaleDuration` expires, access is denied.

---

## Circuit Breaker States

- **`CLOSED`**: Normal operation.
- **`OPEN`**: 5 consecutive failures trips circuit; requests fast-fail immediately without network overhead.
- **`HALF_OPEN`**: After 10s cooldown, permits 1 probe request to determine if backend has recovered.
