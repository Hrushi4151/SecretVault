package com.secretvault.rotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.rotation.engine.RotationValidationEngine;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.entity.RotationValidation;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.model.ValidationType;
import com.secretvault.rotation.repository.RotationValidationRepository;
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
class RotationValidationEngineTest {

    @Mock
    private RotationValidationRepository validationRepository;

    private RotationValidationEngine validationEngine;
    private RotationJob job;

    @BeforeEach
    void setUp() {
        validationEngine = new RotationValidationEngine(validationRepository, new ObjectMapper());
        job = new RotationJob(UUID.randomUUID(), UUID.randomUUID(), null, RotationStrategy.MANUAL, null);
        job.setId(UUID.randomUUID());
        when(validationRepository.save(any(RotationValidation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Should validate none type successfully")
    void testValidateNone() {
        RotationPolicy policy = new RotationPolicy();
        policy.setValidationType(ValidationType.NONE);

        boolean valid = validationEngine.validateSecret("dummy-val", policy, job);
        assertTrue(valid);
        verify(validationRepository, times(1)).save(any(RotationValidation.class));
    }

    @Test
    @DisplayName("Should validate application health successfully for non-blank secret")
    void testValidateApplicationHealth() {
        RotationPolicy policy = new RotationPolicy();
        policy.setValidationType(ValidationType.APPLICATION_HEALTH);

        boolean valid = validationEngine.validateSecret("valid-secret-content-12345", policy, job);
        assertTrue(valid);
        verify(validationRepository, times(1)).save(any(RotationValidation.class));
    }

    @Test
    @DisplayName("Should fail application health validation if secret candidate is too short")
    void testValidateApplicationHealthShort() {
        RotationPolicy policy = new RotationPolicy();
        policy.setValidationType(ValidationType.APPLICATION_HEALTH);

        boolean valid = validationEngine.validateSecret("abc", policy, job);
        assertFalse(valid);
        verify(validationRepository, times(1)).save(any(RotationValidation.class));
    }

    @Test
    @DisplayName("Should validate authentication with default config for valid tokens")
    void testValidateAuthentication() {
        RotationPolicy policy = new RotationPolicy();
        policy.setValidationType(ValidationType.AUTHENTICATION);

        boolean valid = validationEngine.validateSecret("bearer-test-token-777", policy, job);
        assertTrue(valid);
    }
}
