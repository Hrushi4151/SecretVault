# SecretVault Spring Boot Starter Integration

## Maven Dependency

```xml
<dependency>
    <groupId>io.secretvault</groupId>
    <artifactId>secretvault-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 1. Declarative Configuration (`application.yml`)

```yaml
secretvault:
  enabled: true
  endpoint: http://localhost:8080
  workspace: default
  project: payment-gateway
  environment: development
  authentication:
    mode: STATIC
    token: ${SECRET_VAULT_TOKEN}
  cache:
    enabled: true
    ttl: 60s
  resilience:
    mode: FAIL_CLOSED

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payment_db
    username: postgres
    password: ${secretvault:DB_PASSWORD}
```

---

## 2. Dynamic Injection via `@SecretVaultValue`

```java
@Service
public class PaymentProcessingService {

    @SecretVaultValue(value = "STRIPE_SECRET_KEY", autoRefresh = true)
    private String stripeApiKey;

    public void chargeCard() {
        StripeClient client = new StripeClient(stripeApiKey);
        // Process charge
    }
}
```

---

## 3. Actuator Health Telemetry (`/actuator/health`)

```json
{
  "status": "UP",
  "components": {
    "secretVault": {
      "status": "UP",
      "details": {
        "endpoint": "http://localhost:8080",
        "workspace": "default",
        "project": "payment-gateway",
        "environment": "development",
        "circuitBreaker": "CLOSED",
        "cachedSecretsCount": 2
      }
    }
  }
}
```
*(Guaranteed: Zero secret keys or payload values are exposed in actuator output).*
