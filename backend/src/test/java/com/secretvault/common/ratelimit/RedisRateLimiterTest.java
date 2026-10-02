package com.secretvault.common.ratelimit;

import com.secretvault.common.exception.ApiException;
import com.secretvault.common.observability.RedisMetrics;
import com.secretvault.common.redis.RedisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisMetrics redisMetrics;

    private RedisProperties redisProperties;
    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        redisProperties = new RedisProperties();
        redisProperties.getRateLimit().setEnabled(true);
        redisProperties.getRateLimit().setFailOpen(false);

        rateLimiter = new RedisRateLimiter(stringRedisTemplate, redisProperties, redisMetrics);
    }

    @Test
    @DisplayName("Request under limit: Returns allowed with remaining count")
    void testUnderLimitAllowed() {
        when(stringRedisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList("test-key")), eq("60")))
                .thenReturn(List.of(3L, 55L));

        RateLimitResult result = rateLimiter.checkRateLimit("test-key", 5, Duration.ofSeconds(60));

        assertTrue(result.allowed());
        assertEquals(2, result.remainingRequests());
        assertEquals(0, result.retryAfterSeconds());
        verify(redisMetrics).rateLimitAllowed(anyString());
    }

    @Test
    @DisplayName("Request exactly at limit: Returns allowed with 0 remaining")
    void testExactlyAtLimitAllowed() {
        when(stringRedisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList("test-key")), eq("60")))
                .thenReturn(List.of(5L, 40L));

        RateLimitResult result = rateLimiter.checkRateLimit("test-key", 5, Duration.ofSeconds(60));

        assertTrue(result.allowed());
        assertEquals(0, result.remainingRequests());
        assertEquals(0, result.retryAfterSeconds());
    }

    @Test
    @DisplayName("Request over limit: Returns denied with retryAfterSeconds from TTL")
    void testOverLimitDenied() {
        when(stringRedisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList("test-key")), eq("60")))
                .thenReturn(List.of(6L, 35L));

        RateLimitResult result = rateLimiter.checkRateLimit("test-key", 5, Duration.ofSeconds(60));

        assertFalse(result.allowed());
        assertEquals(0, result.remainingRequests());
        assertEquals(35, result.retryAfterSeconds());
        verify(redisMetrics).rateLimitDenied(anyString());
    }

    @Test
    @DisplayName("checkRateLimitOrThrow throws RateLimitExceededException (HTTP 429) when limit exceeded")
    void testCheckRateLimitOrThrow() {
        when(stringRedisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList("test-key")), eq("60")))
                .thenReturn(List.of(11L, 45L));

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, () ->
                rateLimiter.checkRateLimitOrThrow("test-key", 10, Duration.ofSeconds(60), "Too many requests")
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals("RATE_LIMIT_EXCEEDED", ex.getCode());
        assertEquals(45, ex.getRetryAfterSeconds());
        assertEquals("Too many requests", ex.getMessage());
    }

    @Test
    @DisplayName("Redis failure with FAIL_CLOSED policy throws internal exception to protect system")
    void testRedisFailureFailClosed() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        ApiException ex = assertThrows(ApiException.class, () ->
                rateLimiter.checkRateLimit("test-key", 5, Duration.ofSeconds(60))
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertEquals("RATE_LIMIT_UNAVAILABLE", ex.getCode());
        verify(redisMetrics).redisFailure("ratelimit");
    }

    @Test
    @DisplayName("Redis failure with FAIL_OPEN policy permits request gracefully")
    void testRedisFailureFailOpen() {
        redisProperties.getRateLimit().setFailOpen(true);
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        RateLimitResult result = rateLimiter.checkRateLimit("test-key", 5, Duration.ofSeconds(60));

        assertTrue(result.allowed());
        verify(redisMetrics).redisFailure("ratelimit");
    }

    @Test
    @DisplayName("Disabled rate limiting always returns allowed")
    void testDisabledRateLimiting() {
        redisProperties.getRateLimit().setEnabled(false);

        RateLimitResult result = rateLimiter.checkRateLimit("test-key", 5, Duration.ofSeconds(60));

        assertTrue(result.allowed());
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    @DisplayName("Reset deletes rate limit key from Redis")
    void testReset() {
        rateLimiter.reset("test-key");
        verify(stringRedisTemplate).delete("test-key");
    }

    @Test
    @DisplayName("Simulated concurrency with 100 concurrent requests")
    void testConcurrentExecution() throws InterruptedException {
        int threads = 100;
        int limit = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger currentCounter = new AtomicInteger(0);

        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenAnswer(inv -> {
                    long count = currentCounter.incrementAndGet();
                    return List.of(count, 60L);
                });

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger deniedCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    RateLimitResult res = rateLimiter.checkRateLimit("concurrent-key", limit, Duration.ofSeconds(60));
                    if (res.allowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        deniedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(20, allowedCount.get());
        assertEquals(80, deniedCount.get());
    }
}
