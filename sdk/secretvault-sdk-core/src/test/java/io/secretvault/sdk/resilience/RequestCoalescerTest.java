package io.secretvault.sdk.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCoalescerTest {

    @Test
    @DisplayName("Single-flight coalescer executes only 1 network fetch for 100 concurrent threads")
    void testSingleFlightDeduplication() throws Exception {
        RequestCoalescer coalescer = new RequestCoalescer();
        AtomicInteger networkCalls = new AtomicInteger(0);

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startSignal.await();
                CompletableFuture<String> future = coalescer.coalesce("ws::proj::dev::DB_PASS", () ->
                        CompletableFuture.supplyAsync(() -> {
                            networkCalls.incrementAndGet();
                            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                            return "database-secret-value";
                        })
                );
                return future.join();
            }));
        }

        // Release all 100 threads concurrently
        startSignal.countDown();

        for (Future<String> f : futures) {
            String result = f.get();
            assertThat(result).isEqualTo("database-secret-value");
        }

        executor.shutdown();

        // Exactly 1 network execution should have occurred!
        assertThat(networkCalls.get()).isEqualTo(1);
    }
}
