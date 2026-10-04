package com.secretvault.system;

import com.secretvault.encryption.kms.KmsKeyProvider;
import com.secretvault.system.dto.SloStatusResponse;
import com.secretvault.system.dto.SystemHealthResponse;
import com.secretvault.system.service.SystemHealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemHealthServiceTest {

    private DataSource mockDataSource;
    private StringRedisTemplate mockRedisTemplate;
    private KmsKeyProvider mockKmsKeyProvider;
    private SystemHealthService healthService;

    @BeforeEach
    void setUp() throws Exception {
        mockDataSource = mock(DataSource.class);
        Connection mockConn = mock(Connection.class);
        Statement mockStmt = mock(Statement.class);
        when(mockDataSource.getConnection()).thenReturn(mockConn);
        when(mockConn.createStatement()).thenReturn(mockStmt);
        when(mockStmt.execute(anyString())).thenReturn(true);

        mockRedisTemplate = mock(StringRedisTemplate.class);
        RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);
        RedisConnection mockRedisConn = mock(RedisConnection.class);
        when(mockRedisTemplate.getConnectionFactory()).thenReturn(mockFactory);
        when(mockFactory.getConnection()).thenReturn(mockRedisConn);
        when(mockRedisConn.ping()).thenReturn("PONG");

        mockKmsKeyProvider = mock(KmsKeyProvider.class);
        when(mockKmsKeyProvider.getDefaultKeyReference()).thenReturn("alias/secretvault-kek");

        healthService = new SystemHealthService(mockDataSource, mockRedisTemplate, mockKmsKeyProvider);
    }

    @Test
    @DisplayName("evaluateSystemHealth: returns HEALTHY with high score when all tiers pass")
    void testEvaluateSystemHealthHealthy() {
        SystemHealthResponse health = healthService.evaluateSystemHealth();

        assertThat(health.healthScore()).isGreaterThanOrEqualTo(85);
        assertThat(health.status()).isEqualTo("HEALTHY");
        assertThat(health.components()).containsKeys("database", "redis", "kms", "workers");
        assertThat(health.components().get("database").status()).isEqualTo("UP");
        assertThat(health.components().get("redis").status()).isEqualTo("UP");
        assertThat(health.components().get("kms").status()).isEqualTo("UP");
    }

    @Test
    @DisplayName("evaluateSlos: evaluates standard enterprise SLOs with compliance percentage")
    void testEvaluateSlos() {
        SloStatusResponse slos = healthService.evaluateSlos();

        assertThat(slos.slos()).isNotEmpty();
        assertThat(slos.overallCompliancePercentage()).isEqualTo(100.0);
        assertThat(slos.slos()).extracting(SloStatusResponse.SloMetric::name)
                .contains("API Availability", "Secret Retrieval Latency (p95)", "Secret Rollover Success Rate");
    }
}
