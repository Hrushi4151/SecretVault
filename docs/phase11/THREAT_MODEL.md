# SecretVault Phase 11 — Threat Model & Risk Analysis

| Threat | Impact | Mitigation in Phase 11 SDK | Residual Risk |
| :--- | :--- | :--- | :--- |
| **Token Theft via Process Arguments** | Attacker reads tokens via `ps aux` | SDK uses in-memory suppliers and standard input pipes (`--token-stdin`) | Root access on host machine |
| **Secret Leakage in Logs** | Plaintext printed in application logs | `SecretValue.toString()` redacts payload (`[REDACTED]`); `RedactionUtil` masks headers | Application explicitly calling `.value()` in logging statements |
| **Actuator Endpoint Exposure** | Unauthenticated callers read `/actuator/health` | `SecretVaultHealthIndicator` emits only high-level status; zero secret names or values | None |
| **Stale Secret Persistence** | Old revoked secrets used indefinitely | Strict TTL expiration; `FAIL_CLOSED` default; max stale bound enforced | Bounded staleness during temporary network outages in `FAIL_OPEN` mode |
| **Thread Stampede / DoS** | Outage triggers thousands of parallel backend requests | `RequestCoalescer` single-flight deduplication + `CircuitBreaker` fast-fail | Extreme high load on cache misses across distinct keys |
| **Cross-Tenant Cache Collision** | Tenant A receives Tenant B secret | Multi-tenant composite cache keys (`workspace::project::environment::name`) | Zero |
| **Man-In-The-Middle (MITM)** | Attacker intercepts secrets in transit | Strict TLS certificate chain and hostname verification enforced | CA compromise |
