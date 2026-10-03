package io.secretvault.sdk.resilience;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Single-flight request deduplication/coalescer.
 * Ensures that if multiple application threads concurrently request the exact same cache key upon expiry,
 * only a single outbound network request is dispatched while others await its completion.
 */
public class RequestCoalescer {

    private final ConcurrentHashMap<String, CompletableFuture<?>> inFlight = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> coalesce(String key, Supplier<CompletableFuture<T>> supplier) {
        CompletableFuture<?> future = inFlight.computeIfAbsent(key, k -> {
            CompletableFuture<T> created = supplier.get();
            created.whenComplete((res, err) -> inFlight.remove(k));
            return created;
        });

        return (CompletableFuture<T>) future;
    }

    public int getInFlightCount() {
        return inFlight.size();
    }
}
