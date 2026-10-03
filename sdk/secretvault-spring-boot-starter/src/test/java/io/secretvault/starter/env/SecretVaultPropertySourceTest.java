package io.secretvault.starter.env;

import io.secretvault.sdk.api.SecretVaultClient;
import io.secretvault.sdk.auth.StaticTokenProvider;
import io.secretvault.sdk.cache.CacheKey;
import io.secretvault.sdk.client.SdkConfig;
import io.secretvault.sdk.model.SecretValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class SecretVaultPropertySourceTest {

    @Test
    @DisplayName("PropertySource resolves ${secretvault:KEY} and secretvault://KEY syntax")
    void testPropertyResolution() {
        SdkConfig config = SdkConfig.builder()
                .endpoint(URI.create("http://localhost:8080"))
                .credentials(new StaticTokenProvider("test-token"))
                .defaultScope("default", "payment-gateway", "development")
                .allowHttp(true)
                .build();

        SecretVaultClient client = SecretVaultClient.create(config);
        client.getCache().put(CacheKey.ofLatest("default", "payment-gateway", "development", "DB_PASSWORD"),
                SecretValue.of("DB_PASSWORD", "db-pass-12345", 1, "development"));
        client.getCache().put(CacheKey.ofLatest("default", "payment-gateway", "development", "API_KEY"),
                SecretValue.of("API_KEY", "api-key-999", 1, "development"));

        SecretVaultPropertySource propertySource = new SecretVaultPropertySource(client);

        Object val1 = propertySource.getProperty("secretvault:DB_PASSWORD");
        assertThat(val1).isEqualTo("db-pass-12345");

        Object val2 = propertySource.getProperty("secretvault://API_KEY");
        assertThat(val2).isEqualTo("api-key-999");

        Object nonSecret = propertySource.getProperty("server.port");
        assertThat(nonSecret).isNull();
    }
}
