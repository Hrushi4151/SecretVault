package com.secretvault.rotation;

import com.secretvault.common.exception.ApiException;
import com.secretvault.rotation.engine.SecretGenerationEngine;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.model.SecretType;
import com.secretvault.rotation.provider.DatabaseRotator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseRotatorDualUserTest {

    @Mock
    private SecretGenerationEngine generationEngine;

    private DatabaseRotator databaseRotator;
    private RotationPolicy policy;
    private RotationJob job;

    @BeforeEach
    void setUp() {
        databaseRotator = new DatabaseRotator(generationEngine, new ObjectMapper());

        policy = new RotationPolicy();
        policy.setId(UUID.randomUUID());
        policy.setWorkspaceId(UUID.randomUUID());
        policy.setSecretId(UUID.randomUUID());
        policy.setSecretType(SecretType.DATABASE_CREDENTIAL);
        policy.setStrategy(RotationStrategy.SCHEDULED);
        policy.setSecretGeneratorConfig("{\"database\":\"app_db\",\"baseUsername\":\"app_user\",\"schema\":\"public\",\"engine\":\"POSTGRESQL\",\"host\":\"localhost\",\"port\":5432}");

        job = new RotationJob();
        job.setId(UUID.randomUUID());
        job.setWorkspaceId(policy.getWorkspaceId());
        job.setSecretId(policy.getSecretId());
        job.setPolicyId(policy.getId());
        job.setPreviousVersionNumber(1);
        job.setTargetVersionNumber(2);
    }

    @Test
    @DisplayName("Should generate valid database credential JSON with alternate username")
    void testGenerateCredential() {
        when(generationEngine.generateSecret(eq(SecretType.DATABASE_CREDENTIAL), any())).thenReturn("ValidSecureP@ssw0rd123!");

        String generatedJson = databaseRotator.generate(policy, job);

        assertNotNull(generatedJson);
        assertTrue(generatedJson.contains("username"));
        assertTrue(generatedJson.contains("password"));
        assertTrue(generatedJson.contains("database"));
        assertTrue(generatedJson.contains("engine"));
        // Alternate username for target version 2 should be app_user_b
        assertTrue(generatedJson.contains("app_user_b") || generatedJson.contains("app_user_v2"));
    }

    @Test
    @DisplayName("Should reject SQL injection attempts in username or schema identifiers")
    void testSqlInjectionRejectionInIdentifiers() {
        // Attack payload trying SQL injection via username
        policy.setSecretGeneratorConfig("{\"database\":\"app_db\",\"baseUsername\":\"user'; DROP TABLE secrets; --\",\"engine\":\"POSTGRESQL\"}");

        ApiException ex = assertThrows(ApiException.class, () -> databaseRotator.generate(policy, job));
        assertTrue(ex.getMessage().contains("Invalid database identifier") || ex.getMessage().contains("identifier"));
    }

    @Test
    @DisplayName("Should reject SQL injection attempts in schema name")
    void testSqlInjectionRejectionInSchema() {
        policy.setSecretGeneratorConfig("{\"database\":\"app_db\",\"baseUsername\":\"app_user\",\"schema\":\"public' OR '1'='1\",\"engine\":\"POSTGRESQL\"}");

        ApiException ex = assertThrows(ApiException.class, () -> databaseRotator.generate(policy, job));
        assertTrue(ex.getMessage().contains("Invalid database identifier") || ex.getMessage().contains("identifier"));
    }

    @Test
    @DisplayName("Should reject malformed credential JSON during stage")
    void testStageInvalidJson() {
        ApiException ex = assertThrows(ApiException.class, () -> databaseRotator.stage("{bad-json: broken", policy, job));
        assertTrue(ex.getMessage().contains("Failed to parse database credential"));
    }

    @Test
    @DisplayName("Should safely parse and extract database configuration")
    void testParseDatabaseConfig() {
        when(generationEngine.generateSecret(eq(SecretType.DATABASE_CREDENTIAL), any())).thenReturn("AnotherP@ss123!");
        String json = databaseRotator.generate(policy, job);
        assertNotNull(json);

        // Ensure validate method handles parsing safely
        // If connection cannot be made (no live DB in unit test), it returns false safely without throwing uncaught exceptions
        boolean valid = databaseRotator.validate(json, policy, job);
        assertFalse(valid, "Should return false when database host is unreachable rather than crashing");
    }

    @Test
    @DisplayName("Should support MySQL engine configuration and alternate user rollover")
    void testMySqlEngineSupport() {
        policy.setSecretGeneratorConfig("{\"database\":\"mysql_db\",\"baseUsername\":\"mysql_user\",\"engine\":\"MYSQL\",\"host\":\"127.0.0.1\",\"port\":3306}");
        when(generationEngine.generateSecret(eq(SecretType.DATABASE_CREDENTIAL), any())).thenReturn("MySqlSecurePass987#");

        String generatedJson = databaseRotator.generate(policy, job);
        assertNotNull(generatedJson);
        assertTrue(generatedJson.contains("mysql_user_b") || generatedJson.contains("mysql_user_v2"));
        assertTrue(generatedJson.contains("MYSQL"));
    }
}
