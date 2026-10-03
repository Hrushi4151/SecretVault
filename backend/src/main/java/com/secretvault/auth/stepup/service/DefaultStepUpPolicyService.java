package com.secretvault.auth.stepup.service;

import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpContext;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link StepUpPolicyService}.
 * Centralizes security policies for privileged and sensitive actions.
 */
@Service
public class DefaultStepUpPolicyService implements StepUpPolicyService {

    private static final Logger log = LoggerFactory.getLogger(DefaultStepUpPolicyService.class);

    private final EnvironmentRepository environmentRepository;

    public DefaultStepUpPolicyService(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean requiresStepUp(UUID userId, StepUpAction action, StepUpContext context) {
        if (action == null) {
            return false;
        }

        switch (action) {
            case SECRET_REVEAL, SECRET_DELETE, SECRET_ROLLBACK -> {
                if (context == null || context.environmentId() == null) {
                    return false;
                }
                return isProductionOrProtectedEnvironment(context.environmentId());
            }

            case ENVIRONMENT_PROMOTE -> {
                if (context == null || context.environmentId() == null) {
                    return false;
                }
                // Target promotion environment is sensitive if it is production or protected
                return isProductionOrProtectedEnvironment(context.environmentId());
            }

            case SESSION_REVOKE_ALL -> {
                // Bulk session termination is inherently a critical security action
                return true;
            }

            case MFA_DISABLE -> {
                // MFA disable is always a sensitive security modification
                return true;
            }

            case ACCESS_GRANT, ACCESS_REVOKE, JIT_APPROVE -> {
                if (context != null && context.environmentId() != null) {
                    return isProductionOrProtectedEnvironment(context.environmentId());
                }
                return false;
            }

            default -> {
                log.debug("No specific step-up policy configured for action [{}]. Defaulting to false.", action);
                return false;
            }
        }
    }

    private boolean isProductionOrProtectedEnvironment(UUID environmentId) {
        if (environmentId == null || environmentRepository == null) {
            return false;
        }
        Optional<Environment> envOpt = environmentRepository.findById(environmentId);
        if (envOpt.isEmpty()) {
            return false;
        }
        Environment env = envOpt.get();
        return env.isProtected() || env.getEnvType() == EnvType.PRODUCTION;
    }
}
