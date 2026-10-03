package io.secretvault.starter.config;

import io.secretvault.sdk.api.SecretVaultClient;
import io.secretvault.sdk.auth.CredentialProviderChain;
import io.secretvault.sdk.auth.CredentialsProvider;
import io.secretvault.sdk.auth.EnvironmentTokenProvider;
import io.secretvault.sdk.auth.MachineTokenProvider;
import io.secretvault.sdk.auth.OidcTokenProvider;
import io.secretvault.sdk.auth.StaticTokenProvider;
import io.secretvault.sdk.client.SdkConfig;
import io.secretvault.starter.annotation.SecretVaultValueBeanPostProcessor;
import io.secretvault.starter.health.SecretVaultHealthIndicator;
import io.secretvault.starter.properties.SecretVaultProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.UUID;

/**
 * Spring Boot AutoConfiguration for SecretVault.
 */
@AutoConfiguration
@EnableConfigurationProperties(SecretVaultProperties.class)
@ConditionalOnProperty(prefix = "secretvault", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecretVaultAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecretVaultAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SecretVaultClient secretVaultClient(SecretVaultProperties props) {
        log.info("Initializing SecretVault client bean for endpoint: {}", props.getEndpoint());

        CredentialsProvider credentials = resolveCredentials(props);

        SdkConfig.Builder builder = SdkConfig.builder()
                .endpoint(props.getEndpoint())
                .credentials(credentials)
                .cacheEnabled(props.getCache().isEnabled())
                .cacheTtl(props.getCache().getTtl())
                .cacheMaxEntries(props.getCache().getMaxEntries())
                .resiliencePolicy(props.getResilience().getMode())
                .maxStaleDuration(props.getResilience().getMaxStale())
                .retryAttempts(props.getResilience().getRetryAttempts())
                .circuitBreakerEnabled(props.getResilience().isCircuitBreakerEnabled())
                .allowHttp(true);

        if (props.getProject() != null && !props.getProject().isEmpty()) {
            builder.defaultScope(props.getWorkspace(), props.getProject(), props.getEnvironment());
        }

        return SecretVaultClient.create(builder.build());
    }

    @Bean
    @ConditionalOnMissingBean
    public SecretVaultValueBeanPostProcessor secretVaultValueBeanPostProcessor(SecretVaultClient client) {
        return new SecretVaultValueBeanPostProcessor(client);
    }

    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(name = "secretVaultHealthIndicator")
    public SecretVaultHealthIndicator secretVaultHealthIndicator(SecretVaultClient client, SecretVaultProperties props) {
        return new SecretVaultHealthIndicator(client, props);
    }

    private CredentialsProvider resolveCredentials(SecretVaultProperties props) {
        SecretVaultProperties.Authentication auth = props.getAuthentication();
        switch (auth.getMode()) {
            case STATIC:
                if (auth.getToken() != null) return new StaticTokenProvider(auth.getToken());
                break;
            case MACHINE:
                if (auth.getToken() != null && auth.getMachineId() != null) {
                    return new MachineTokenProvider(auth.getMachineId(), auth.getToken());
                }
                break;
            case OIDC:
                if (auth.getProviderId() != null && auth.getMachineId() != null && auth.getOidcToken() != null) {
                    return new OidcTokenProvider(props.getEndpoint(), auth.getProviderId(), auth.getMachineId(), auth::getOidcToken);
                }
                break;
            case ENV:
                return new EnvironmentTokenProvider();
            case AUTO:
            default:
                if (auth.getToken() != null && !auth.getToken().isEmpty()) {
                    return new StaticTokenProvider(auth.getToken());
                }
                return CredentialProviderChain.defaultChain();
        }
        return CredentialProviderChain.defaultChain();
    }
}
