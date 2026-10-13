package io.secretvault.sdk.api;

import io.secretvault.sdk.cache.CacheKey;
import io.secretvault.sdk.cache.SecretCache;
import io.secretvault.sdk.client.SdkConfig;
import io.secretvault.sdk.client.SecretVaultHttpClient;
import io.secretvault.sdk.exception.ConfigurationException;
import io.secretvault.sdk.exception.ErrorCode;
import io.secretvault.sdk.exception.SecretNotFoundException;
import io.secretvault.sdk.exception.SecretVaultException;
import io.secretvault.sdk.model.PromotionPreviewResult;
import io.secretvault.sdk.model.PromotionResult;
import io.secretvault.sdk.model.SecretBatchResult;
import io.secretvault.sdk.model.SecretChangeEvent;
import io.secretvault.sdk.model.SecretMetadata;
import io.secretvault.sdk.model.SecretRefreshListener;
import io.secretvault.sdk.model.SecretScope;
import io.secretvault.sdk.model.SecretStatus;
import io.secretvault.sdk.model.SecretValue;
import io.secretvault.sdk.model.SecretVersion;
import io.secretvault.sdk.observability.SdkMetrics;
import io.secretvault.sdk.resilience.RequestCoalescer;
import io.secretvault.sdk.resilience.ResiliencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DefaultSecretsApi implements SecretsApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultSecretsApi.class);

    private final SdkConfig config;
    private final SecretVaultHttpClient httpClient;
    private final SecretCache cache;
    private final RequestCoalescer coalescer;
    private final SdkMetrics metrics;
    private final ScheduledExecutorService scheduler;

    private final Map<String, List<SecretRefreshListener>> watchers = new ConcurrentHashMap<>();

    public DefaultSecretsApi(
            SdkConfig config,
            SecretVaultHttpClient httpClient,
            SecretCache cache,
            RequestCoalescer coalescer,
            SdkMetrics metrics,
            ScheduledExecutorService scheduler
    ) {
        this.config = config;
        this.httpClient = httpClient;
        this.cache = cache;
        this.coalescer = coalescer;
        this.metrics = metrics;
        this.scheduler = scheduler;
    }

    @Override
    public SecretValue get(String secretName) {
        return get(resolveDefaultScope(), secretName, null);
    }

    @Override
    public SecretValue get(String secretName, int versionNumber) {
        return get(resolveDefaultScope(), secretName, versionNumber);
    }

    @Override
    public SecretValue get(SecretScope scope, String secretName) {
        return get(scope, secretName, null);
    }

    @Override
    public SecretValue get(SecretScope scope, String secretName, int versionNumber) {
        return get(scope, secretName, Integer.valueOf(versionNumber));
    }

    private SecretValue get(SecretScope scope, String secretName, Integer version) {
        Objects.requireNonNull(secretName, "Secret name cannot be null");
        Objects.requireNonNull(scope, "SecretScope cannot be null");

        CacheKey cacheKey = CacheKey.of(scope.workspace(), scope.project(), scope.environment(), secretName, version);

        if (config.isCacheEnabled()) {
            Optional<SecretValue> cached = cache.get(cacheKey, false);
            if (cached.isPresent()) {
                metrics.recordCacheHit();
                return cached.get();
            }
        }

        // Cache miss -> Single-flight request coalescing
        metrics.recordCacheMiss();
        try {
            CompletableFuture<SecretValue> future = coalescer.coalesce(cacheKey.toKeyString(), () ->
                    CompletableFuture.supplyAsync(() -> fetchAndCache(scope, secretName, version, cacheKey))
            );
            return future.join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;

            // Fail-open policy with bounded stale cache
            if (config.getResiliencePolicy() == ResiliencePolicy.FAIL_OPEN_WITH_CACHE && config.isCacheEnabled()) {
                Optional<SecretValue> stale = cache.get(cacheKey, true);
                if (stale.isPresent()) {
                    log.warn("SecretVault backend unreachable. Serving stale cached secret for '{}' per FAIL_OPEN_WITH_CACHE policy.", secretName);
                    return stale.get();
                }
            }

            if (cause instanceof SecretVaultException sve) {
                throw sve;
            }
            throw new SecretVaultException("Failed to retrieve secret '" + secretName + "': " + cause.getMessage(), ErrorCode.SV_INTERNAL_ERROR, cause);
        }
    }

    private SecretValue fetchAndCache(SecretScope scope, String secretName, Integer version, CacheKey cacheKey) {
        UUID wsId = httpClient.resolveWorkspaceId(scope.workspace());
        UUID projId = httpClient.resolveProjectId(wsId, scope.project());
        UUID envId = httpClient.resolveEnvironmentId(wsId, projId, scope.environment());
        UUID secretId = httpClient.resolveSecretId(wsId, projId, envId, secretName);

        SecretValue revealed = httpClient.revealSecret(wsId, projId, envId, secretId, secretName, version);

        if (config.isCacheEnabled()) {
            cache.put(cacheKey, revealed);
        }

        return revealed;
    }

    @Override
    public SecretMetadata getMetadata(String secretName) {
        return getMetadata(resolveDefaultScope(), secretName);
    }

    @Override
    public SecretMetadata getMetadata(SecretScope scope, String secretName) {
        UUID wsId = httpClient.resolveWorkspaceId(scope.workspace());
        UUID projId = httpClient.resolveProjectId(wsId, scope.project());
        UUID envId = httpClient.resolveEnvironmentId(wsId, projId, scope.environment());
        UUID secretId = httpClient.resolveSecretId(wsId, projId, envId, secretName);
        return httpClient.getSecretMetadata(wsId, projId, envId, secretId);
    }

    @Override
    public boolean exists(String secretName) {
        return exists(resolveDefaultScope(), secretName);
    }

    @Override
    public boolean exists(SecretScope scope, String secretName) {
        try {
            getMetadata(scope, secretName);
            return true;
        } catch (SecretNotFoundException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<SecretMetadata> list() {
        return list(resolveDefaultScope());
    }

    @Override
    public List<SecretMetadata> list(SecretScope scope) {
        UUID wsId = httpClient.resolveWorkspaceId(scope.workspace());
        UUID projId = httpClient.resolveProjectId(wsId, scope.project());
        UUID envId = httpClient.resolveEnvironmentId(wsId, projId, scope.environment());
        return httpClient.listSecrets(wsId, projId, envId, null, SecretStatus.ACTIVE);
    }

    @Override
    public SecretBatchResult getMany(List<String> secretNames) {
        return getMany(resolveDefaultScope(), secretNames);
    }

    @Override
    public SecretBatchResult getMany(SecretScope scope, List<String> secretNames) {
        if (secretNames == null || secretNames.isEmpty()) {
            return new SecretBatchResult(Map.of(), Map.of());
        }

        Map<String, SecretValue> successes = new HashMap<>();
        Map<String, SecretVaultException> failures = new HashMap<>();

        for (String name : secretNames) {
            try {
                SecretValue val = get(scope, name);
                successes.put(name, val);
            } catch (SecretVaultException e) {
                failures.put(name, e);
            } catch (Exception e) {
                failures.put(name, new SecretVaultException("Failed to get " + name, ErrorCode.SV_INTERNAL_ERROR, e));
            }
        }

        return new SecretBatchResult(successes, failures);
    }

    @Override
    public SecretValue refresh(String secretName) {
        return refresh(resolveDefaultScope(), secretName);
    }

    @Override
    public SecretValue refresh(SecretScope scope, String secretName) {
        CacheKey cacheKey = CacheKey.ofLatest(scope.workspace(), scope.project(), scope.environment(), secretName);
        cache.evict(cacheKey);

        SecretValue newValue = fetchAndCache(scope, secretName, null, cacheKey);
        metrics.recordSecretRefreshed(secretName);

        // Notify registered listeners
        notifyWatchers(scope, secretName, newValue);

        return newValue;
    }

    @Override
    public void watch(String secretName, SecretRefreshListener listener) {
        watch(resolveDefaultScope(), secretName, listener);
    }

    @Override
    public void watch(SecretScope scope, String secretName, SecretRefreshListener listener) {
        String watchKey = scope.workspace() + "::" + scope.project() + "::" + scope.environment() + "::" + secretName;
        watchers.computeIfAbsent(watchKey, k -> new CopyOnWriteArrayList<>()).add(listener);

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    SecretMetadata meta = getMetadata(scope, secretName);
                    CacheKey cacheKey = CacheKey.ofLatest(scope.workspace(), scope.project(), scope.environment(), secretName);
                    Optional<SecretValue> cached = cache.get(cacheKey, true);

                    if (cached.isPresent() && cached.get().version() != meta.currentVersion()) {
                        log.info("Detected secret rotation for '{}' (v{} -> v{}). Triggering background refresh.",
                                secretName, cached.get().version(), meta.currentVersion());
                        refresh(scope, secretName);
                    }
                } catch (Exception e) {
                    log.warn("Background watch polling failed for '{}': {}", secretName, e.getMessage());
                }
            }, 30, 30, TimeUnit.SECONDS);
        }
    }

    @Override
    public CompletableFuture<SecretValue> getAsync(String secretName) {
        return CompletableFuture.supplyAsync(() -> get(secretName));
    }

    @Override
    public CompletableFuture<SecretValue> getAsync(SecretScope scope, String secretName) {
        return CompletableFuture.supplyAsync(() -> get(scope, secretName));
    }

    @Override
    public SecretMetadata create(String secretName, String value) {
        return create(resolveDefaultScope(), secretName, value, null);
    }

    @Override
    public SecretMetadata create(String secretName, String value, String description) {
        return create(resolveDefaultScope(), secretName, value, description);
    }

    @Override
    public SecretMetadata create(SecretScope scope, String secretName, String value, String description) {
        Objects.requireNonNull(scope, "SecretScope cannot be null");
        Objects.requireNonNull(secretName, "Secret name cannot be null");
        Objects.requireNonNull(value, "Secret value cannot be null");

        UUID wsId = httpClient.resolveWorkspaceId(scope.workspace());
        UUID projId = httpClient.resolveProjectId(wsId, scope.project());
        UUID envId = httpClient.resolveEnvironmentId(wsId, projId, scope.environment());

        SecretMetadata meta = httpClient.createSecret(wsId, projId, envId, secretName, value, description);
        if (config.isCacheEnabled()) {
            cache.evict(CacheKey.ofLatest(scope.workspace(), scope.project(), scope.environment(), secretName));
        }
        return meta;
    }

    @Override
    public SecretMetadata update(String secretName, String value) {
        return update(resolveDefaultScope(), secretName, value, null, null);
    }

    @Override
    public SecretMetadata update(String secretName, String value, String description, String reason) {
        return update(resolveDefaultScope(), secretName, value, description, reason);
    }

    @Override
    public SecretMetadata update(SecretScope scope, String secretName, String value, String description, String reason) {
        Objects.requireNonNull(scope, "SecretScope cannot be null");
        Objects.requireNonNull(secretName, "Secret name cannot be null");

        UUID wsId = httpClient.resolveWorkspaceId(scope.workspace());
        UUID projId = httpClient.resolveProjectId(wsId, scope.project());
        UUID envId = httpClient.resolveEnvironmentId(wsId, projId, scope.environment());
        UUID secretId = httpClient.resolveSecretId(wsId, projId, envId, secretName);

        SecretMetadata meta = httpClient.updateSecret(wsId, projId, envId, secretId, value, description, reason);
        if (config.isCacheEnabled()) {
            cache.evict(CacheKey.ofLatest(scope.workspace(), scope.project(), scope.environment(), secretName));
        }
        return meta;
    }

    @Override
    public void delete(String secretName) {
        delete(resolveDefaultScope(), secretName);
    }

    @Override
    public void delete(SecretScope scope, String secretName) {
        Objects.requireNonNull(scope, "SecretScope cannot be null");
        Objects.requireNonNull(secretName, "Secret name cannot be null");

        UUID wsId = httpClient.resolveWorkspaceId(scope.workspace());
        UUID projId = httpClient.resolveProjectId(wsId, scope.project());
        UUID envId = httpClient.resolveEnvironmentId(wsId, projId, scope.environment());
        UUID secretId = httpClient.resolveSecretId(wsId, projId, envId, secretName);

        httpClient.deleteSecret(wsId, projId, envId, secretId);
        if (config.isCacheEnabled()) {
            cache.evict(CacheKey.ofLatest(scope.workspace(), scope.project(), scope.environment(), secretName));
        }
    }

    @Override
    public List<SecretVersion> listVersions(String secretName) {
        return listVersions(resolveDefaultScope(), secretName);
    }

    @Override
    public List<SecretVersion> listVersions(SecretScope scope, String secretName) {
        Objects.requireNonNull(scope, "SecretScope cannot be null");
        Objects.requireNonNull(secretName, "Secret name cannot be null");

        UUID wsId = httpClient.resolveWorkspaceId(scope.workspace());
        UUID projId = httpClient.resolveProjectId(wsId, scope.project());
        UUID envId = httpClient.resolveEnvironmentId(wsId, projId, scope.environment());
        UUID secretId = httpClient.resolveSecretId(wsId, projId, envId, secretName);

        return httpClient.listVersions(wsId, projId, envId, secretId);
    }

    @Override
    public SecretVersion rollback(String secretName, int targetVersion, String reason) {
        return rollback(resolveDefaultScope(), secretName, targetVersion, reason);
    }

    @Override
    public SecretVersion rollback(SecretScope scope, String secretName, int targetVersion, String reason) {
        Objects.requireNonNull(scope, "SecretScope cannot be null");
        Objects.requireNonNull(secretName, "Secret name cannot be null");

        UUID wsId = httpClient.resolveWorkspaceId(scope.workspace());
        UUID projId = httpClient.resolveProjectId(wsId, scope.project());
        UUID envId = httpClient.resolveEnvironmentId(wsId, projId, scope.environment());
        UUID secretId = httpClient.resolveSecretId(wsId, projId, envId, secretName);

        SecretVersion rolledBack = httpClient.rollbackSecret(wsId, projId, envId, secretId, targetVersion, reason);
        if (config.isCacheEnabled()) {
            cache.evict(CacheKey.ofLatest(scope.workspace(), scope.project(), scope.environment(), secretName));
        }
        return rolledBack;
    }

    @Override
    public List<String> listTags(String secretName, int versionNumber) {
        return listTags(resolveDefaultScope(), secretName, versionNumber);
    }

    @Override
    public List<String> listTags(SecretScope scope, String secretName, int versionNumber) {
        Objects.requireNonNull(scope, "SecretScope cannot be null");
        Objects.requireNonNull(secretName, "Secret name cannot be null");

        UUID wsId = httpClient.resolveWorkspaceId(scope.workspace());
        UUID projId = httpClient.resolveProjectId(wsId, scope.project());
        UUID envId = httpClient.resolveEnvironmentId(wsId, projId, scope.environment());
        UUID secretId = httpClient.resolveSecretId(wsId, projId, envId, secretName);

        return httpClient.listTags(wsId, projId, envId, secretId, versionNumber);
    }

    @Override
    public void addTag(String secretName, int versionNumber, String tag) {
        addTag(resolveDefaultScope(), secretName, versionNumber, tag);
    }

    @Override
    public void addTag(SecretScope scope, String secretName, int versionNumber, String tag) {
        Objects.requireNonNull(scope, "SecretScope cannot be null");
        Objects.requireNonNull(secretName, "Secret name cannot be null");
        Objects.requireNonNull(tag, "Tag cannot be null");

        UUID wsId = httpClient.resolveWorkspaceId(scope.workspace());
        UUID projId = httpClient.resolveProjectId(wsId, scope.project());
        UUID envId = httpClient.resolveEnvironmentId(wsId, projId, scope.environment());
        UUID secretId = httpClient.resolveSecretId(wsId, projId, envId, secretName);

        httpClient.addTag(wsId, projId, envId, secretId, versionNumber, tag);
    }

    @Override
    public void removeTag(String secretName, int versionNumber, String tag) {
        removeTag(resolveDefaultScope(), secretName, versionNumber, tag);
    }

    @Override
    public void removeTag(SecretScope scope, String secretName, int versionNumber, String tag) {
        Objects.requireNonNull(scope, "SecretScope cannot be null");
        Objects.requireNonNull(secretName, "Secret name cannot be null");
        Objects.requireNonNull(tag, "Tag cannot be null");

        UUID wsId = httpClient.resolveWorkspaceId(scope.workspace());
        UUID projId = httpClient.resolveProjectId(wsId, scope.project());
        UUID envId = httpClient.resolveEnvironmentId(wsId, projId, scope.environment());
        UUID secretId = httpClient.resolveSecretId(wsId, projId, envId, secretName);

        httpClient.removeTag(wsId, projId, envId, secretId, versionNumber, tag);
    }

    @Override
    public PromotionPreviewResult previewPromotion(String targetEnvironment, List<String> secretNames) {
        return previewPromotion(resolveDefaultScope(), targetEnvironment, secretNames);
    }

    @Override
    public PromotionPreviewResult previewPromotion(SecretScope sourceScope, String targetEnvironment, List<String> secretNames) {
        Objects.requireNonNull(sourceScope, "Source SecretScope cannot be null");
        Objects.requireNonNull(targetEnvironment, "Target environment cannot be null");

        UUID wsId = httpClient.resolveWorkspaceId(sourceScope.workspace());
        UUID projId = httpClient.resolveProjectId(wsId, sourceScope.project());
        UUID sourceEnvId = httpClient.resolveEnvironmentId(wsId, projId, sourceScope.environment());
        UUID targetEnvId = httpClient.resolveEnvironmentId(wsId, projId, targetEnvironment);

        return httpClient.previewPromotion(wsId, projId, sourceEnvId, targetEnvId, secretNames);
    }

    @Override
    public PromotionResult executePromotion(String targetEnvironment, List<String> secretNames, String reason) {
        return executePromotion(resolveDefaultScope(), targetEnvironment, secretNames, reason);
    }

    @Override
    public PromotionResult executePromotion(SecretScope sourceScope, String targetEnvironment, List<String> secretNames, String reason) {
        Objects.requireNonNull(sourceScope, "Source SecretScope cannot be null");
        Objects.requireNonNull(targetEnvironment, "Target environment cannot be null");

        UUID wsId = httpClient.resolveWorkspaceId(sourceScope.workspace());
        UUID projId = httpClient.resolveProjectId(wsId, sourceScope.project());
        UUID sourceEnvId = httpClient.resolveEnvironmentId(wsId, projId, sourceScope.environment());
        UUID targetEnvId = httpClient.resolveEnvironmentId(wsId, projId, targetEnvironment);

        PromotionResult res = httpClient.executePromotion(wsId, projId, sourceEnvId, targetEnvId, secretNames, reason);
        if (config.isCacheEnabled()) {
            cache.clear();
        }
        return res;
    }

    private void notifyWatchers(SecretScope scope, String secretName, SecretValue newValue) {
        String watchKey = scope.workspace() + "::" + scope.project() + "::" + scope.environment() + "::" + secretName;
        List<SecretRefreshListener> list = watchers.get(watchKey);
        if (list != null && !list.isEmpty()) {
            SecretChangeEvent event = new SecretChangeEvent(
                    secretName,
                    newValue.version() > 1 ? newValue.version() - 1 : 1,
                    newValue.version(),
                    SecretChangeEvent.ChangeType.ROTATED,
                    Instant.now()
            );
            for (SecretRefreshListener l : list) {
                try {
                    l.onSecretRefreshed(event, newValue);
                } catch (Exception e) {
                    log.error("Error executing secret refresh listener for '{}': {}", secretName, e.getMessage());
                }
            }
        }
    }

    private SecretScope resolveDefaultScope() {
        if (config.getDefaultScope() == null) {
            throw new ConfigurationException("No default SecretScope configured. Pass SecretScope explicitly or configure defaultScope().");
        }
        return config.getDefaultScope();
    }
}
