package com.secretvault.common.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisHealthIndicatorTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection redisConnection;

    @Test
    @DisplayName("Health is UP when Redis connection returns PONG")
    void testHealthUp() {
        when(connectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        RedisHealthIndicator indicator = new RedisHealthIndicator(connectionFactory);
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("AVAILABLE", health.getDetails().get("status"));
        assertNull(health.getDetails().get("host"));
        assertNull(health.getDetails().get("password"));
    }

    @Test
    @DisplayName("Health is DOWN when Redis connection fails, without leaking credentials")
    void testHealthDown() {
        when(connectionFactory.getConnection()).thenThrow(new RedisConnectionFailureException("Connection refused"));

        RedisHealthIndicator indicator = new RedisHealthIndicator(connectionFactory);
        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("UNAVAILABLE", health.getDetails().get("status"));
        assertEquals("Redis connection check failed", health.getDetails().get("error"));
        assertNull(health.getDetails().get("host"));
        assertNull(health.getDetails().get("password"));
    }
}
