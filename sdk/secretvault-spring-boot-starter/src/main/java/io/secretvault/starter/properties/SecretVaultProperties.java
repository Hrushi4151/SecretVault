package io.secretvault.starter.properties;

import io.secretvault.sdk.resilience.ResiliencePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Configuration properties for SecretVault Spring Boot integration.
 */
@ConfigurationProperties(prefix = "secretvault")
public class SecretVaultProperties {

    private boolean enabled = true;
    private URI endpoint = URI.create("http://localhost:8080");
    private String workspace = "default";
    private String project;
    private String environment = "development";

    private final Authentication authentication = new Authentication();
    private final Cache cache = new Cache();
    private final Resilience resilience = new Resilience();
    private final Refresh refresh = new Refresh();
    private final Secrets secrets = new Secrets();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public URI getEndpoint() { return endpoint; }
    public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }

    public String getWorkspace() { return workspace; }
    public void setWorkspace(String workspace) { this.workspace = workspace; }

    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public Authentication getAuthentication() { return authentication; }
    public Cache getCache() { return cache; }
    public Resilience getResilience() { return resilience; }
    public Refresh getRefresh() { return refresh; }
    public Secrets getSecrets() { return secrets; }

    public static class Authentication {
        public enum Mode { STATIC, MACHINE, OIDC, ENV, AUTO }

        private Mode mode = Mode.AUTO;
        private String token;
        private UUID machineId;
        private UUID providerId;
        private String oidcToken;

        public Mode getMode() { return mode; }
        public void setMode(Mode mode) { this.mode = mode; }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }

        public UUID getMachineId() { return machineId; }
        public void setMachineId(UUID machineId) { this.machineId = machineId; }

        public UUID getProviderId() { return providerId; }
        public void setProviderId(UUID providerId) { this.providerId = providerId; }

        public String getOidcToken() { return oidcToken; }
        public void setOidcToken(String oidcToken) { this.oidcToken = oidcToken; }
    }

    public static class Cache {
        private boolean enabled = true;
        private Duration ttl = Duration.ofSeconds(60);
        private int maxEntries = 500;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }

        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
    }

    public static class Resilience {
        private ResiliencePolicy mode = ResiliencePolicy.FAIL_CLOSED;
        private Duration maxStale = Duration.ofMinutes(5);
        private int retryAttempts = 3;
        private boolean circuitBreakerEnabled = true;

        public ResiliencePolicy getMode() { return mode; }
        public void setMode(ResiliencePolicy mode) { this.mode = mode; }

        public Duration getMaxStale() { return maxStale; }
        public void setMaxStale(Duration maxStale) { this.maxStale = maxStale; }

        public int getRetryAttempts() { return retryAttempts; }
        public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

        public boolean isCircuitBreakerEnabled() { return circuitBreakerEnabled; }
        public void setCircuitBreakerEnabled(boolean circuitBreakerEnabled) { this.circuitBreakerEnabled = circuitBreakerEnabled; }
    }

    public static class Refresh {
        private boolean enabled = false;
        private Duration interval = Duration.ofSeconds(30);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Duration getInterval() { return interval; }
        public void setInterval(Duration interval) { this.interval = interval; }
    }

    public static class Secrets {
        private List<String> required = new ArrayList<>();
        private List<String> optional = new ArrayList<>();

        public List<String> getRequired() { return required; }
        public void setRequired(List<String> required) { this.required = required; }

        public List<String> getOptional() { return optional; }
        public void setOptional(List<String> optional) { this.optional = optional; }
    }
}
