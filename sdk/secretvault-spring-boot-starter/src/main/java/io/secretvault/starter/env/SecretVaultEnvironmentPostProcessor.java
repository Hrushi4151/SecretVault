package io.secretvault.starter.env;

import io.secretvault.sdk.api.SecretVaultClient;
import io.secretvault.sdk.auth.CredentialProviderChain;
import io.secretvault.sdk.auth.CredentialsProvider;
import io.secretvault.sdk.auth.EnvironmentTokenProvider;
import io.secretvault.sdk.auth.MachineTokenProvider;
import io.secretvault.sdk.auth.OidcTokenProvider;
import io.secretvault.sdk.auth.StaticTokenProvider;
import io.secretvault.sdk.client.SdkConfig;
import io.secretvault.sdk.exception.SecretVaultException;
import io.secretvault.sdk.resilience.ResiliencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/**
 * Early-phase {@link EnvironmentPostProcessor} that mounts SecretVault property source
 * before Spring application beans and datasources are initialized.
 */
public class SecretVaultEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SecretVaultEnvironmentPostProcessor.class);

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String enabledStr = environment.getProperty("secretvault.enabled", "true");
        if ("false".equalsIgnoreCase(enabledStr)) {
            log.info("SecretVault starter is disabled (secretvault.enabled=false).");
            return;
        }

        String endpointStr = environment.getProperty("secretvault.endpoint", "http://localhost:8080");
        String workspace = environment.getProperty("secretvault.workspace", "default");
        String project = environment.getProperty("secretvault.project");
        String env = environment.getProperty("secretvault.environment", "development");

        if (project == null || project.trim().isEmpty()) {
            // No project configured at bootstrap; PropertySource will be mounted via AutoConfiguration
            return;
        }

        try {
            URI endpoint = URI.create(endpointStr);
            CredentialsProvider creds = resolveCredentials(environment, endpoint);

            SdkConfig.Builder configBuilder = SdkConfig.builder()
                    .endpoint(endpoint)
                    .credentials(creds)
                    .defaultScope(workspace, project, env)
                    .cacheEnabled(Boolean.parseBoolean(environment.getProperty("secretvault.cache.enabled", "true")))
                    .allowHttp(true);

            String resilienceMode = environment.getProperty("secretvault.resilience.mode", "FAIL_CLOSED");
            if ("FAIL_OPEN_WITH_CACHE".equalsIgnoreCase(resilienceMode)) {
                configBuilder.resiliencePolicy(ResiliencePolicy.FAIL_OPEN_WITH_CACHE);
            }

            SecretVaultClient client = SecretVaultClient.create(configBuilder.build());
            PropertySource<?> propertySource = new SecretVaultPropertySource(client);
            environment.getPropertySources().addFirst(propertySource);

            log.info("SecretVault bootstrap property source mounted successfully for scope [{}/{}/{}].",
                    workspace, project, env);

        } catch (Exception e) {
            String resilienceMode = environment.getProperty("secretvault.resilience.mode", "FAIL_CLOSED");
            if ("FAIL_CLOSED".equalsIgnoreCase(resilienceMode)) {
                log.error("Failed to initialize SecretVault environment post-processor: {}", e.getMessage());
                throw new SecretVaultException("Failed to bootstrap SecretVault property source: " + e.getMessage(), io.secretvault.sdk.exception.ErrorCode.SV_UNAVAILABLE, e);
            } else {
                log.warn("SecretVault bootstrap failed in FAIL_OPEN mode: {}", e.getMessage());
            }
        }
    }

    private CredentialsProvider resolveCredentials(ConfigurableEnvironment env, URI endpoint) {
        String mode = env.getProperty("secretvault.authentication.mode", "AUTO").toUpperCase();
        String token = env.getProperty("secretvault.authentication.token");
        String machineIdStr = env.getProperty("secretvault.authentication.machine-id");
        String providerIdStr = env.getProperty("secretvault.authentication.provider-id");
        String oidcToken = env.getProperty("secretvault.authentication.oidc-token");

        switch (mode) {
            case "STATIC":
                if (token != null && !token.isEmpty()) return new StaticTokenProvider(token);
                break;
            case "MACHINE":
                if (token != null && machineIdStr != null) {
                    return new MachineTokenProvider(UUID.fromString(machineIdStr), token);
                }
                break;
            case "OIDC":
                if (providerIdStr != null && machineIdStr != null && oidcToken != null) {
                    return new OidcTokenProvider(endpoint, UUID.fromString(providerIdStr), UUID.fromString(machineIdStr), () -> oidcToken);
                }
                break;
            case "ENV":
                return new EnvironmentTokenProvider();
            case "AUTO":
            default:
                if (token != null && !token.isEmpty()) return new StaticTokenProvider(token);
                return CredentialProviderChain.defaultChain();
        }
        return CredentialProviderChain.defaultChain();
    }
}
