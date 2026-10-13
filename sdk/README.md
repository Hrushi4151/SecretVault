# SecretVault — Official Client SDKs

Lightweight runtime client SDKs for securely fetching, caching, and dynamically rotating secrets in production applications.

## Supported Languages

| Language | Directory | Package / Artifact | Key Features |
| :--- | :--- | :--- | :--- |
| **Java / Spring Boot** | `sdk/secretvault-sdk-core`<br/>`sdk/secretvault-spring-boot-starter` | `io.secretvault:secretvault-spring-boot-starter:1.0.0`<br/>`io.secretvault:secretvault-sdk-core:1.0.0` | `@SecretVaultValue` hot-reloading, Spring PropertySource `${...}`, CircuitBreaker, Fail-Safe Cache |
| **JavaScript / TypeScript** | `sdk/secretvault-sdk-js` | `@secretvault/sdk` | Node 18+ native fetch, zero dependencies, in-memory TTL cache, dynamic `watch()`, full TypeScript types |
| **Python** | `sdk/secretvault-sdk-python` | `secretvault` | Zero dependencies (`urllib`), thread-safe TTL cache, dynamic `watch()`, fail-open stale resilience |

---

## 1. JavaScript / TypeScript Quick Start

```typescript
import { SecretVaultClient } from '@secretvault/sdk';

const vault = new SecretVaultClient({
  endpoint: 'http://localhost:8080',
  workspace: 'myspace',
  project: 'payment-api',
  environment: 'production',
  token: process.env.SECRETVault_TOKEN,
  cache: { ttlSec: 60 },
  failOpenStale: true
});

const dbPassword = await vault.secrets.get('DB_PASSWORD');
console.log(`Database Password: ${dbPassword.value}`);

// Watch rotation
vault.secrets.watch('JWT_SECRET', (newSecret) => {
  console.log(`Rotated to v${newSecret.versionNumber}`);
});
```

---

## 2. Python Quick Start

```python
from secretvault import SecretVaultClient

vault = SecretVaultClient(
    endpoint="http://localhost:8080",
    workspace="myspace",
    project="payment-api",
    environment="production",
    token=os.getenv("SECRETVault_TOKEN"),
    cache_ttl_sec=60.0,
    fail_open_stale=True
)

db_password = vault.secrets.get("DB_PASSWORD")
print(f"Database Password: {db_password.value}")

# Watch rotation
vault.secrets.watch("JWT_SECRET", lambda s: print(f"Rotated to v{s.version_number}"))
```

---

## 3. Java / Spring Boot Quick Start

```yaml
# application.yml
secretvault:
  endpoint: "http://localhost:8080"
  workspace: "myspace"
  project: "payment-api"
  environment: "production"
  authentication:
    token: "${SECRETVault_TOKEN}"
```

```java
// Spring Boot Component
@Component
public class PaymentGateway {

    @SecretVaultValue(value = "STRIPE_SECRET_KEY", autoRefresh = true)
    private volatile String stripeKey;
}
```
