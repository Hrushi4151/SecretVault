package com.secretvault.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.cache.dto.ProjectSummaryCacheDto;
import com.secretvault.common.cache.dto.WorkspaceSummaryCacheDto;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.observability.RedisMetrics;
import com.secretvault.common.redis.RedisKeyBuilder;
import com.secretvault.common.redis.RedisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisSafeCacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisMetrics redisMetrics;

    private RedisKeyBuilder keyBuilder;
    private RedisProperties redisProperties;
    private ObjectMapper objectMapper;
    private RedisSafeCacheService cacheService;

    @BeforeEach
    void setUp() {
        keyBuilder = new RedisKeyBuilder("test");
        redisProperties = new RedisProperties();
        redisProperties.getCache().setEnabled(true);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        cacheService = new RedisSafeCacheService(stringRedisTemplate, keyBuilder, redisProperties, redisMetrics, objectMapper);
    }

    @Test
    @DisplayName("Cache Hit: Returns cached DTO directly without invoking database supplier")
    void testCacheHit() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        UUID wsId = UUID.randomUUID();
        WorkspaceSummaryCacheDto cachedDto = new WorkspaceSummaryCacheDto(wsId, "Engineering", "engineering", "Dev workspace", UUID.randomUUID(), Instant.now());
        String json = objectMapper.writeValueAsString(cachedDto);

        when(valueOperations.get(anyString())).thenReturn(json);

        AtomicInteger dbCallCount = new AtomicInteger(0);

        WorkspaceSummaryCacheDto result = cacheService.getOrCompute(
                "workspace",
                wsId.toString(),
                WorkspaceSummaryCacheDto.class,
                Duration.ofMinutes(5),
                () -> {
                    dbCallCount.incrementAndGet();
                    return null;
                }
        );

        assertNotNull(result);
        assertEquals("Engineering", result.name());
        assertEquals(0, dbCallCount.get());
        verify(redisMetrics).cacheHit("workspace");
    }

    @Test
    @DisplayName("Cache Miss: Invokes DB supplier and repopulates Redis with TTL")
    void testCacheMissRepopulates() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        UUID wsId = UUID.randomUUID();
        WorkspaceSummaryCacheDto freshDto = new WorkspaceSummaryCacheDto(wsId, "Security", "security", "Security workspace", UUID.randomUUID(), Instant.now());

        AtomicInteger dbCallCount = new AtomicInteger(0);

        WorkspaceSummaryCacheDto result = cacheService.getOrCompute(
                "workspace",
                wsId.toString(),
                WorkspaceSummaryCacheDto.class,
                Duration.ofMinutes(5),
                () -> {
                    dbCallCount.incrementAndGet();
                    return freshDto;
                }
        );

        assertNotNull(result);
        assertEquals("Security", result.name());
        assertEquals(1, dbCallCount.get());
        verify(redisMetrics).cacheMiss("workspace");
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("Evict: Deletes cache key upon update or deletion")
    void testEvict() {
        cacheService.evict("workspace", "ws-123");
        verify(stringRedisTemplate).delete("secretvault:test:cache:workspace:ws-123");
    }

    @Test
    @DisplayName("Security invariant: Strictly rejects caching of sensitive secret or authorization domains")
    void testForbiddenDomainRejection() {
        ApiException ex1 = assertThrows(ApiException.class, () ->
                cacheService.getOrCompute("secret", "sec-1", String.class, Duration.ofMinutes(5), () -> "payload")
        );
        assertEquals("UNSAFE_CACHE_VIOLATION", ex1.getCode());

        ApiException ex2 = assertThrows(ApiException.class, () ->
                cacheService.getOrCompute("effectiveaccess", "user-1", String.class, Duration.ofMinutes(5), () -> "allow")
        );
        assertEquals("UNSAFE_CACHE_VIOLATION", ex2.getCode());
    }

    @Test
    @DisplayName("Graceful Degradation: Redis outage falls back to DB supplier seamlessly for metadata")
    void testRedisOutageFallback() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("Redis timeout"));

        UUID projId = UUID.randomUUID();
        ProjectSummaryCacheDto dbDto = new ProjectSummaryCacheDto(projId, UUID.randomUUID(), "Payment API", "payment-api", "Core payments", Instant.now());

        ProjectSummaryCacheDto result = cacheService.getOrCompute(
                "project",
                projId.toString(),
                ProjectSummaryCacheDto.class,
                Duration.ofMinutes(5),
                () -> dbDto
        );

        assertNotNull(result);
        assertEquals("Payment API", result.name());
        verify(redisMetrics).redisFailure("cache_read");
    }

    @Test
    @DisplayName("Tenant Isolation: Different workspaces and projects have distinct isolated cache keys")
    void testTenantKeyIsolation() {
        UUID wsA = UUID.randomUUID();
        UUID wsB = UUID.randomUUID();

        String keyA = keyBuilder.cacheKey("workspace", wsA.toString());
        String keyB = keyBuilder.cacheKey("workspace", wsB.toString());

        assertNotEquals(keyA, keyB);
        assertTrue(keyA.contains(wsA.toString()));
        assertTrue(keyB.contains(wsB.toString()));
    }
}
