package io.secretvault.sdk.api;

import io.secretvault.sdk.model.PromotionPreviewResult;
import io.secretvault.sdk.model.PromotionResult;
import io.secretvault.sdk.model.SecretBatchResult;
import io.secretvault.sdk.model.SecretMetadata;
import io.secretvault.sdk.model.SecretRefreshListener;
import io.secretvault.sdk.model.SecretScope;
import io.secretvault.sdk.model.SecretValue;
import io.secretvault.sdk.model.SecretVersion;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Fluent API interface for secret retrieval, mutations, lifecycle management, version history, and promotion.
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

    // --- Secret Lifecycle Mutations & Management ---
    SecretMetadata create(String secretName, String value);
    SecretMetadata create(String secretName, String value, String description);
    SecretMetadata create(SecretScope scope, String secretName, String value, String description);

    SecretMetadata update(String secretName, String value);
    SecretMetadata update(String secretName, String value, String description, String reason);
    SecretMetadata update(SecretScope scope, String secretName, String value, String description, String reason);

    void delete(String secretName);
    void delete(SecretScope scope, String secretName);

    List<SecretVersion> listVersions(String secretName);
    List<SecretVersion> listVersions(SecretScope scope, String secretName);

    SecretVersion rollback(String secretName, int targetVersion, String reason);
    SecretVersion rollback(SecretScope scope, String secretName, int targetVersion, String reason);

    List<String> listTags(String secretName, int versionNumber);
    List<String> listTags(SecretScope scope, String secretName, int versionNumber);

    void addTag(String secretName, int versionNumber, String tag);
    void addTag(SecretScope scope, String secretName, int versionNumber, String tag);

    void removeTag(String secretName, int versionNumber, String tag);
    void removeTag(SecretScope scope, String secretName, int versionNumber, String tag);

    PromotionPreviewResult previewPromotion(String targetEnvironment, List<String> secretNames);
    PromotionPreviewResult previewPromotion(SecretScope sourceScope, String targetEnvironment, List<String> secretNames);

    PromotionResult executePromotion(String targetEnvironment, List<String> secretNames, String reason);
    PromotionResult executePromotion(SecretScope sourceScope, String targetEnvironment, List<String> secretNames, String reason);
}
