package io.secretvault.sdk.api;

import io.secretvault.sdk.model.SecretBatchResult;
import io.secretvault.sdk.model.SecretMetadata;
import io.secretvault.sdk.model.SecretRefreshListener;
import io.secretvault.sdk.model.SecretScope;
import io.secretvault.sdk.model.SecretValue;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Fluent API interface for secret retrieval, metadata inspection, caching, batching, and dynamic rotation watching.
 */
public interface SecretsApi {

    SecretValue get(String secretName);
    SecretValue get(String secretName, int versionNumber);
    SecretValue get(SecretScope scope, String secretName);
    SecretValue get(SecretScope scope, String secretName, int versionNumber);

    SecretMetadata getMetadata(String secretName);
    SecretMetadata getMetadata(SecretScope scope, String secretName);

    boolean exists(String secretName);
    boolean exists(SecretScope scope, String secretName);

    List<SecretMetadata> list();
    List<SecretMetadata> list(SecretScope scope);

    SecretBatchResult getMany(List<String> secretNames);
    SecretBatchResult getMany(SecretScope scope, List<String> secretNames);

    SecretValue refresh(String secretName);
    SecretValue refresh(SecretScope scope, String secretName);

    void watch(String secretName, SecretRefreshListener listener);
    void watch(SecretScope scope, String secretName, SecretRefreshListener listener);

    CompletableFuture<SecretValue> getAsync(String secretName);
    CompletableFuture<SecretValue> getAsync(SecretScope scope, String secretName);
}
