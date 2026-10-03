# SecretVault Java SDK Core Reference

## Maven Coordinates

```xml
<dependency>
    <groupId>io.secretvault</groupId>
    <artifactId>secretvault-sdk-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Client Configuration & Builder

```java
SdkConfig config = SdkConfig.builder()
    .endpoint("https://vault.company.com")
    .credentials(new StaticTokenProvider("machine-token-abc"))
    .defaultScope("default", "payment-service", "production")
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(10))
    .cacheEnabled(true)
    .cacheTtl(Duration.ofSeconds(60))
    .cacheMaxEntries(500)
    .resiliencePolicy(ResiliencePolicy.FAIL_CLOSED)
    .maxStaleDuration(Duration.ofMinutes(5))
    .retryAttempts(3)
    .circuitBreakerEnabled(true)
    .allowHttp(false)
    .build();

SecretVaultClient client = SecretVaultClient.create(config);
```

---

## Secret Operations

```java
// 1. Get secret value (Latest active version)
SecretValue dbPass = client.secrets().get("DB_PASSWORD");
String rawValue = dbPass.value();

// 2. Get specific historical version
SecretValue v2 = client.secrets().get("DB_PASSWORD", 2);

// 3. Inspect metadata without revealing plaintext
SecretMetadata meta = client.secrets().getMetadata("DB_PASSWORD");
System.out.println("Active Version: " + meta.currentVersion());

// 4. Batch retrieval
SecretBatchResult batch = client.secrets().getMany(List.of("DB_PASSWORD", "API_KEY"));
if (batch.isCompleteSuccess()) {
    SecretValue apiKey = batch.get("API_KEY").get();
}

// 5. Watch for secret rotation
client.secrets().watch("DB_PASSWORD", (event, newValue) -> {
    System.out.println("Rotated from v" + event.oldVersion() + " to v" + event.newVersion());
});
```
