package com.secretvault.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs for Generalized Step-Up Authentication ceremony.
 */
public final class StepUpDtos {
    private StepUpDtos() {}

    public enum StepUpAction {
        SECRET_REVEAL,
        SECRET_DELETE,
        SECRET_ROLLBACK,
        ENVIRONMENT_PROMOTE,
        ACCESS_GRANT,
        ACCESS_REVOKE,
        JIT_APPROVE,
        JIT_REVOKE,
        MFA_DISABLE,
        SESSION_REVOKE_ALL,
        WEBAUTHN_CREDENTIAL_REVOKE,
        ROLE_CHANGE,
        MEMBER_REMOVE,
        PROJECT_ACCESS_CHANGE,
        ENVIRONMENT_ACCESS_CHANGE,
        PRIVILEGED_POLICY_CHANGE,
        BREAK_GLASS_REQUEST,
        PRIVILEGED_APPROVE,
        PRIVILEGED_EXECUTE
    }

    public enum StepUpFactor {
        PASSWORD,
        TOTP,
        RECOVERY_CODE,
        WEBAUTHN
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StepUpContext(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID targetUserId,
            Map<String, String> attributes
    ) {
        public StepUpContext {
            attributes = attributes != null ? Collections.unmodifiableMap(attributes) : Collections.emptyMap();
        }

        public static StepUpContext forSecret(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId) {
            return new StepUpContext(workspaceId, projectId, environmentId, secretId, null, Collections.emptyMap());
        }

        public static StepUpContext forEnvironment(UUID workspaceId, UUID projectId, UUID environmentId) {
            return new StepUpContext(workspaceId, projectId, environmentId, null, null, Collections.emptyMap());
        }

        public static StepUpContext forWorkspace(UUID workspaceId) {
            return new StepUpContext(workspaceId, null, null, null, null, Collections.emptyMap());
        }

        public static StepUpContext empty() {
            return new StepUpContext(null, null, null, null, null, Collections.emptyMap());
        }
    }

    public record StepUpChallengeRequest(
            StepUpAction action,
            StepUpContext context
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StepUpChallengeResponse(
            String challengeId,
            StepUpAction action,
            List<StepUpFactor> supportedFactors,
            Instant expiresAt
    ) {}

    public record TotpStepUpRequest(
            String code
    ) {}

    public record PasswordStepUpRequest(
            String password
    ) {}

    public record RecoveryCodeStepUpRequest(
            String recoveryCode
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StepUpProofResponse(
            String proofToken,
            StepUpAction action,
            StepUpFactor factorUsed,
            Instant expiresAt
    ) {}
}
