package com.demo;

import io.secretvault.sdk.api.SecretVaultClient;
import io.secretvault.sdk.auth.EnvironmentTokenProvider;
import io.secretvault.sdk.client.SdkConfig;
import io.secretvault.sdk.model.SecretValue;

import java.net.URI;
import java.time.Duration;

public class JavaSdkDemoApplication {

    public static void main(String[] args) {
        String endpoint = System.getenv().getOrDefault("SECRETVAULT_ENDPOINT", "http://localhost:8080");
        String workspace = System.getenv().getOrDefault("SECRETVAULT_WORKSPACE", "default");
        String project = System.getenv().getOrDefault("SECRETVAULT_PROJECT", "payment-gateway");
        String environment = System.getenv().getOrDefault("SECRETVAULT_ENVIRONMENT", "development");

        System.out.println("=== SecretVault Java SDK Demo ===");
        System.out.println("Endpoint:    " + endpoint);
        System.out.println("Scope:       " + workspace + " / " + project + " / " + environment);

        SdkConfig config = SdkConfig.builder()
                .endpoint(URI.create(endpoint))
                .credentials(new EnvironmentTokenProvider())
                .defaultScope(workspace, project, environment)
                .cacheEnabled(true)
                .cacheTtl(Duration.ofSeconds(60))
                .allowHttp(true)
                .build();

        try (SecretVaultClient client = SecretVaultClient.create(config)) {
            System.out.println("Server connectivity ping: " + (client.ping() ? "UP" : "DOWN"));

            // 1. Initial retrieval (Cache miss -> Network fetch)
            long t1 = System.nanoTime();
            SecretValue dbPass = client.secrets().get("DB_PASSWORD");
            long d1 = (System.nanoTime() - t1) / 1_000_000;
            System.out.println("Fetched secret: " + dbPass.name() + " (v" + dbPass.version() + ") in " + d1 + "ms");
            System.out.println("Safe toString output: " + dbPass); // [REDACTED]

            // 2. Second retrieval (In-memory Cache Hit)
            long t2 = System.nanoTime();
            SecretValue cachedPass = client.secrets().get("DB_PASSWORD");
            long d2 = (System.nanoTime() - t2) / 1_000; // microseconds!
            System.out.println("Cached retrieval in " + d2 + " µs (< 1ms cache hit!)");

            // 3. Metrics verification
            System.out.println("SDK Cache Hits: " + client.getMetrics().getCacheHits());
            System.out.println("SDK Cache Misses: " + client.getMetrics().getCacheMisses());
            System.out.println("=== Demo finished successfully ===");
        } catch (Exception e) {
            System.err.println("SDK Demo Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
