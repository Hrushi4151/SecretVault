package com.secretvault.common.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Sanitized Redis Health Indicator for Spring Boot Actuator.
 * Verifies Redis responsiveness via PING without exposing internal network topology or credentials.
 */
@Component("redisHealth")
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String ping = connection.ping();
            if ("PONG".equalsIgnoreCase(ping)) {
                return Health.up()
                        .withDetail("status", "AVAILABLE")
                        .build();
            }
            return Health.down()
                    .withDetail("status", "UNRESPONSIVE")
                    .build();
        } catch (Exception ex) {
            return Health.down()
                    .withDetail("status", "UNAVAILABLE")
                    .withDetail("error", "Redis connection check failed")
                    .build();
        }
    }
}
