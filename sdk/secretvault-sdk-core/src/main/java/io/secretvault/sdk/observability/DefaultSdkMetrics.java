package io.secretvault.sdk.observability;

import java.util.concurrent.atomic.AtomicLong;

public class DefaultSdkMetrics implements SdkMetrics {

    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong refreshCount = new AtomicLong(0);
    private final AtomicLong authRefreshCount = new AtomicLong(0);
    private final AtomicLong circuitOpenCount = new AtomicLong(0);

    @Override
    public void recordRequest(String operation, boolean success, long durationMillis) {
        requestCount.incrementAndGet();
        if (!success) {
            failureCount.incrementAndGet();
        }
    }

    @Override
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    @Override
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    @Override
    public void recordSecretRefreshed(String operation) {
        refreshCount.incrementAndGet();
    }

    @Override
    public void recordAuthRefresh(boolean success) {
        authRefreshCount.incrementAndGet();
    }

    @Override
    public void recordCircuitOpen() {
        circuitOpenCount.incrementAndGet();
    }

    @Override public long getRequestCount() { return requestCount.get(); }
    @Override public long getFailureCount() { return failureCount.get(); }
    @Override public long getCacheHits() { return cacheHits.get(); }
    @Override public long getCacheMisses() { return cacheMisses.get(); }
    @Override public long getRefreshCount() { return refreshCount.get(); }
    @Override public long getCircuitOpenCount() { return circuitOpenCount.get(); }
}
