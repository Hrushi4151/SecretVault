package com.secretvault.auth.stepup;

import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpContext;
import com.secretvault.auth.stepup.service.DefaultStepUpPolicyService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StepUpPolicyServiceTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @InjectMocks
    private DefaultStepUpPolicyService policyService;

    private UUID userId;
    private UUID workspaceId;
    private UUID projectId;
    private UUID devEnvId;
    private UUID prodEnvId;
    private UUID protectedEnvId;
    private UUID secretId;

    private Environment devEnv;
    private Environment prodEnv;
    private Environment protectedEnv;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        devEnvId = UUID.randomUUID();
        prodEnvId = UUID.randomUUID();
        protectedEnvId = UUID.randomUUID();
        secretId = UUID.randomUUID();

        devEnv = new Environment(projectId, "Development", "dev", EnvType.DEVELOPMENT, "Dev env", false, userId);
        devEnv.setId(devEnvId);

        prodEnv = new Environment(projectId, "Production", "prod", EnvType.PRODUCTION, "Prod env", false, userId);
        prodEnv.setId(prodEnvId);

        protectedEnv = new Environment(projectId, "Staging Protected", "staging", EnvType.STAGING, "Protected staging", true, userId);
        protectedEnv.setId(protectedEnvId);
    }

    @Test
    @DisplayName("SECRET_REVEAL in Development does not require step-up")
    void testSecretReveal_development_noStepUp() {
        when(environmentRepository.findById(devEnvId)).thenReturn(Optional.of(devEnv));
        StepUpContext context = StepUpContext.forSecret(workspaceId, projectId, devEnvId, secretId);

        boolean required = policyService.requiresStepUp(userId, StepUpAction.SECRET_REVEAL, context);
        assertFalse(required);
    }

    @Test
    @DisplayName("SECRET_REVEAL in Production requires step-up")
    void testSecretReveal_production_requiresStepUp() {
        when(environmentRepository.findById(prodEnvId)).thenReturn(Optional.of(prodEnv));
        StepUpContext context = StepUpContext.forSecret(workspaceId, projectId, prodEnvId, secretId);

        boolean required = policyService.requiresStepUp(userId, StepUpAction.SECRET_REVEAL, context);
        assertTrue(required);
    }

    @Test
    @DisplayName("SECRET_REVEAL in Protected Staging requires step-up")
    void testSecretReveal_protectedStaging_requiresStepUp() {
        when(environmentRepository.findById(protectedEnvId)).thenReturn(Optional.of(protectedEnv));
        StepUpContext context = StepUpContext.forSecret(workspaceId, projectId, protectedEnvId, secretId);

        boolean required = policyService.requiresStepUp(userId, StepUpAction.SECRET_REVEAL, context);
        assertTrue(required);
    }

    @Test
    @DisplayName("SECRET_DELETE in Production requires step-up")
    void testSecretDelete_production_requiresStepUp() {
        when(environmentRepository.findById(prodEnvId)).thenReturn(Optional.of(prodEnv));
        StepUpContext context = StepUpContext.forSecret(workspaceId, projectId, prodEnvId, secretId);

        boolean required = policyService.requiresStepUp(userId, StepUpAction.SECRET_DELETE, context);
        assertTrue(required);
    }

    @Test
    @DisplayName("ENVIRONMENT_PROMOTE to Production requires step-up")
    void testEnvironmentPromote_production_requiresStepUp() {
        when(environmentRepository.findById(prodEnvId)).thenReturn(Optional.of(prodEnv));
        StepUpContext context = StepUpContext.forEnvironment(workspaceId, projectId, prodEnvId);

        boolean required = policyService.requiresStepUp(userId, StepUpAction.ENVIRONMENT_PROMOTE, context);
        assertTrue(required);
    }

    @Test
    @DisplayName("SESSION_REVOKE_ALL always requires step-up")
    void testSessionRevokeAll_requiresStepUp() {
        boolean required = policyService.requiresStepUp(userId, StepUpAction.SESSION_REVOKE_ALL, StepUpContext.empty());
        assertTrue(required);
    }

    @Test
    @DisplayName("MFA_DISABLE always requires step-up")
    void testMfaDisable_requiresStepUp() {
        boolean required = policyService.requiresStepUp(userId, StepUpAction.MFA_DISABLE, StepUpContext.empty());
        assertTrue(required);
    }

    @Test
    @DisplayName("Null action returns false")
    void testNullAction() {
        boolean required = policyService.requiresStepUp(userId, null, StepUpContext.empty());
        assertFalse(required);
    }
}
