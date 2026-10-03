package io.secretvault.sdk.observability;

public interface SdkMetrics {
    void recordRequest(String operation, boolean success, long durationMillis);
    void recordCacheHit();
    void recordCacheMiss();
    void recordSecretRefreshed(String operation);
    void recordAuthRefresh(boolean success);
    void recordCircuitOpen();

    long getRequestCount();
    long getFailureCount();
    long getCacheHits();
    long getCacheMisses();
    long getRefreshCount();
    long getCircuitOpenCount();
}
