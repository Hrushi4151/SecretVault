package com.secretvault.auth.stepup.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.mfa.service.MfaService;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.session.entity.UserSession;
import com.secretvault.auth.session.repository.UserSessionRepository;
import com.secretvault.auth.stepup.dto.StepUpChallengeResponse;
import com.secretvault.auth.stepup.dto.StepUpProofResponse;
import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpChallengePayload;
import com.secretvault.auth.stepup.model.StepUpContext;
import com.secretvault.auth.stepup.model.StepUpFactor;
import com.secretvault.auth.stepup.model.StepUpProofPayload;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.security.state.SecurityStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link StepUpAuthenticationService}.
 * Coordinates Redis-backed ephemeral challenge/proof state, single-use atomic consumption,
 * password/TOTP verification, session binding, and full audit trailing.
 */
@Service
public class DefaultStepUpAuthenticationService implements StepUpAuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultStepUpAuthenticationService.class);

    private static final String CATEGORY_STEP_UP_CHALLENGE = "step_up_challenge";
    private static final String CATEGORY_STEP_UP_PROOF = "step_up_proof";
    private static final long CHALLENGE_TTL_SECONDS = 300L;
    private static final long PROOF_TTL_SECONDS = 300L;
    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final MfaService mfaService;
    private final SecurityStateStore securityStateStore;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final EffectiveAccessService effectiveAccessService;
    private final SecureRandom secureRandom = new SecureRandom();

    public DefaultStepUpAuthenticationService(
            UserRepository userRepository,
            UserSessionRepository sessionRepository,
            MfaService mfaService,
            SecurityStateStore securityStateStore,
            AuditService auditService,
            PasswordEncoder passwordEncoder,
            @Autowired(required = false) EffectiveAccessService effectiveAccessService
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "UserSessionRepository must not be null");
        this.mfaService = Objects.requireNonNull(mfaService, "MfaService must not be null");
        this.securityStateStore = Objects.requireNonNull(securityStateStore, "SecurityStateStore must not be null");
        this.auditService = Objects.requireNonNull(auditService, "AuditService must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder must not be null");
        this.effectiveAccessService = effectiveAccessService;
    }

    @Override
    @Transactional(readOnly = true)
    public StepUpChallengeResponse createChallenge(
            UUID userId,
            String sessionIdentifier,
            StepUpAction action,
            StepUpContext context
    ) {
        if (userId == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        if (action == null) {
            throw ApiException.badRequest("Action is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("User account is inactive");
        }

        validateActiveSession(userId, sessionIdentifier);

        // Pre-authorization check: ensure user has base permissions before creating challenge
        validateBaseAuthorization(userId, action, context);

        List<StepUpFactor> supportedFactors = new ArrayList<>();
        supportedFactors.add(StepUpFactor.PASSWORD);
        if (mfaService.isMfaEnabled(userId)) {
            supportedFactors.add(StepUpFactor.TOTP);
            supportedFactors.add(StepUpFactor.RECOVERY_CODE);
        }

        String challengeId = UUID.randomUUID().toString();
        StepUpChallengePayload payload = StepUpChallengePayload.of(
                challengeId,
                userId,
                sessionIdentifier,
                action,
                context,
                supportedFactors,
                CHALLENGE_TTL_SECONDS,
                MAX_ATTEMPTS
        );

        try {
            securityStateStore.put(CATEGORY_STEP_UP_CHALLENGE, challengeId, payload, Duration.ofSeconds(CHALLENGE_TTL_SECONDS));
        } catch (Exception ex) {
            log.error("Failed to store step-up challenge in Redis for user [{}]", userId, ex);
            throw ApiException.internal("SECURITY_STATE_ERROR", "Step-up authentication service temporarily unavailable", ex);
        }

        auditService.recordAudit(
                null,
                context != null ? context.workspaceId() : null,
                userId,
                "USER",
                AuditAction.STEP_UP_CHALLENGE_CREATED,
                "STEP_UP_CHALLENGE",
                userId,
                challengeId,
                null,
                "SUCCESS"
        );

        return new StepUpChallengeResponse(challengeId, action, supportedFactors, payload.expiresAt());
    }

    @Override
    @Transactional
    public StepUpProofResponse verifyPassword(
            String challengeId,
            UUID userId,
            String sessionIdentifier,
            String password
    ) {
        if (password == null || password.isBlank()) {
            throw ApiException.badRequest("Password is required");
        }

        StepUpChallengePayload challenge = getChallengeOrThrow(challengeId);
        validateChallengeBinding(challenge, userId, sessionIdentifier);
        validateActiveSession(userId, sessionIdentifier);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("User account is inactive");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            handleFailedAttempt(challengeId, userId, challenge.maxAttempts());
            auditService.recordAudit(
                    null,
                    challenge.context() != null ? challenge.context().workspaceId() : null,
                    userId,
                    "USER",
                    AuditAction.STEP_UP_VERIFICATION_FAILED,
                    "STEP_UP_CHALLENGE",
                    userId,
                    challengeId,
                    null,
                    "INVALID_PASSWORD"
            );
            throw ApiException.unauthorized("Invalid password for step-up verification");
        }

        return issueProof(challenge, userId, sessionIdentifier, StepUpFactor.PASSWORD);
    }

    @Override
    @Transactional
    public StepUpProofResponse verifyTotp(
            String challengeId,
            UUID userId,
            String sessionIdentifier,
            String code
    ) {
        if (code == null || code.isBlank()) {
            throw ApiException.badRequest("Verification code is required");
        }

        StepUpChallengePayload challenge = getChallengeOrThrow(challengeId);
        validateChallengeBinding(challenge, userId, sessionIdentifier);
        validateActiveSession(userId, sessionIdentifier);

        if (!mfaService.isMfaEnabled(userId)) {
            throw ApiException.badRequest("MFA is not enabled for this user");
        }

        boolean valid = mfaService.verifyTotp(userId, code);
        if (!valid) {
            handleFailedAttempt(challengeId, userId, challenge.maxAttempts());
            auditService.recordAudit(
                    null,
                    challenge.context() != null ? challenge.context().workspaceId() : null,
                    userId,
                    "USER",
                    AuditAction.STEP_UP_VERIFICATION_FAILED,
                    "STEP_UP_CHALLENGE",
                    userId,
                    challengeId,
                    null,
                    "INVALID_TOTP"
            );
            throw ApiException.badRequest("Invalid authentication code for step-up verification");
        }

        return issueProof(challenge, userId, sessionIdentifier, StepUpFactor.TOTP);
    }

    @Override
    @Transactional
    public StepUpProofResponse verifyRecoveryCode(
            String challengeId,
            UUID userId,
            String sessionIdentifier,
            String recoveryCode
    ) {
        if (recoveryCode == null || recoveryCode.isBlank()) {
            throw ApiException.badRequest("Recovery code is required");
        }

        StepUpChallengePayload challenge = getChallengeOrThrow(challengeId);
        validateChallengeBinding(challenge, userId, sessionIdentifier);
        validateActiveSession(userId, sessionIdentifier);

        if (!mfaService.isMfaEnabled(userId)) {
            throw ApiException.badRequest("MFA is not enabled for this user");
        }

        boolean valid = mfaService.verifyAndConsumeRecoveryCode(userId, recoveryCode);
        if (!valid) {
            handleFailedAttempt(challengeId, userId, challenge.maxAttempts());
            auditService.recordAudit(
                    null,
                    challenge.context() != null ? challenge.context().workspaceId() : null,
                    userId,
                    "USER",
                    AuditAction.STEP_UP_VERIFICATION_FAILED,
                    "STEP_UP_CHALLENGE",
                    userId,
                    challengeId,
                    null,
                    "INVALID_RECOVERY_CODE"
            );
            throw ApiException.badRequest("Invalid or already-used backup recovery code");
        }

        return issueProof(challenge, userId, sessionIdentifier, StepUpFactor.RECOVERY_CODE);
    }

    @Override
    @Transactional
    public void verifyAndConsumeProof(
            String proofToken,
            UUID userId,
            String sessionIdentifier,
            StepUpAction action,
            StepUpContext context
    ) {
        if (proofToken == null || proofToken.isBlank()) {
            throw ApiException.forbidden("STEP_UP_REQUIRED", "Step-up authentication is required for this operation");
        }

        Optional<StepUpProofPayload> consumedOpt;
        try {
            consumedOpt = securityStateStore.consumeAtomic(CATEGORY_STEP_UP_PROOF, proofToken, StepUpProofPayload.class);
        } catch (Exception ex) {
            log.error("Redis failure during step-up proof consumption", ex);
            throw ApiException.internal("SECURITY_STATE_ERROR", "Step-up verification temporarily unavailable", ex);
        }

        if (consumedOpt.isEmpty()) {
            auditService.recordAudit(
                    null,
                    context != null ? context.workspaceId() : null,
                    userId,
                    "USER",
                    AuditAction.STEP_UP_REPLAY_REJECTED,
                    "STEP_UP_PROOF",
                    userId,
                    proofToken,
                    null,
                    "EXPIRED_OR_REPLAYED"
            );
            throw ApiException.forbidden("STEP_UP_INVALID", "Step-up proof is invalid, expired, or has already been used");
        }

        StepUpProofPayload proof = consumedOpt.get();

        if (!Objects.equals(proof.userId(), userId)) {
            auditService.recordAudit(
                    null,
                    context != null ? context.workspaceId() : null,
                    userId,
                    "USER",
                    AuditAction.STEP_UP_REPLAY_REJECTED,
                    "STEP_UP_PROOF",
                    userId,
                    proofToken,
                    null,
                    "USER_MISMATCH"
            );
            throw ApiException.forbidden("STEP_UP_MISMATCH", "Step-up proof was issued to a different user");
        }

        if (proof.sessionIdentifier() != null && !Objects.equals(proof.sessionIdentifier(), sessionIdentifier)) {
            auditService.recordAudit(
                    null,
                    context != null ? context.workspaceId() : null,
                    userId,
                    "USER",
                    AuditAction.STEP_UP_REPLAY_REJECTED,
                    "STEP_UP_PROOF",
                    userId,
                    proofToken,
                    null,
                    "SESSION_MISMATCH"
            );
            throw ApiException.forbidden("STEP_UP_MISMATCH", "Step-up proof was issued to a different session");
        }

        // Validate caller's session is still active in database
        validateActiveSession(userId, sessionIdentifier);

        if (proof.action() != action) {
            auditService.recordAudit(
                    null,
                    context != null ? context.workspaceId() : null,
                    userId,
                    "USER",
                    AuditAction.STEP_UP_REPLAY_REJECTED,
                    "STEP_UP_PROOF",
                    userId,
                    proofToken,
                    null,
                    "ACTION_MISMATCH"
            );
            throw ApiException.forbidden("STEP_UP_MISMATCH", "Step-up proof was issued for a different action");
        }

        if (proof.context() != null && context != null && !proof.context().matches(context)) {
            auditService.recordAudit(
                    null,
                    context != null ? context.workspaceId() : null,
                    userId,
                    "USER",
                    AuditAction.STEP_UP_REPLAY_REJECTED,
                    "STEP_UP_PROOF",
                    userId,
                    proofToken,
                    null,
                    "CONTEXT_MISMATCH"
            );
            throw ApiException.forbidden("STEP_UP_MISMATCH", "Step-up proof was issued for a different resource scope");
        }

        auditService.recordAudit(
                null,
                context != null ? context.workspaceId() : null,
                userId,
                "USER",
                AuditAction.STEP_UP_PROOF_CONSUMED,
                "STEP_UP_PROOF",
                userId,
                proofToken,
                null,
                "SUCCESS"
        );
    }

    private StepUpProofResponse issueProof(
            StepUpChallengePayload challenge,
            UUID userId,
            String sessionIdentifier,
            StepUpFactor factor
    ) {
        securityStateStore.delete(CATEGORY_STEP_UP_CHALLENGE, challenge.challengeId());

        String proofToken = generateProofToken();
        StepUpProofPayload proof = StepUpProofPayload.of(
                proofToken,
                userId,
                sessionIdentifier,
                challenge.action(),
                challenge.context(),
                factor,
                PROOF_TTL_SECONDS
        );

        try {
            securityStateStore.put(CATEGORY_STEP_UP_PROOF, proofToken, proof, Duration.ofSeconds(PROOF_TTL_SECONDS));
        } catch (Exception ex) {
            log.error("Failed to store step-up proof in Redis for user [{}]", userId, ex);
            throw ApiException.internal("SECURITY_STATE_ERROR", "Failed to issue step-up proof", ex);
        }

        auditService.recordAudit(
                null,
                challenge.context() != null ? challenge.context().workspaceId() : null,
                userId,
                "USER",
                AuditAction.STEP_UP_VERIFICATION_SUCCESS,
                "STEP_UP_PROOF",
                userId,
                challenge.challengeId(),
                null,
                factor.name()
        );

        return new StepUpProofResponse(proofToken, challenge.action(), factor, proof.expiresAt());
    }

    private StepUpChallengePayload getChallengeOrThrow(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            throw ApiException.badRequest("Challenge ID is required");
        }
        Optional<StepUpChallengePayload> opt;
        try {
            opt = securityStateStore.get(CATEGORY_STEP_UP_CHALLENGE, challengeId, StepUpChallengePayload.class);
        } catch (Exception ex) {
            log.error("Failed to read step-up challenge [{}] from Redis", challengeId, ex);
            throw ApiException.internal("SECURITY_STATE_ERROR", "Step-up service temporarily unavailable", ex);
        }
        if (opt.isEmpty()) {
            throw ApiException.badRequest("Step-up challenge not found or expired");
        }
        return opt.get();
    }

    private void validateChallengeBinding(StepUpChallengePayload challenge, UUID userId, String sessionIdentifier) {
        if (!challenge.matchesUserAndSession(userId, sessionIdentifier)) {
            handleFailedAttempt(challenge.challengeId(), userId, challenge.maxAttempts());
            throw ApiException.unauthorized("Step-up challenge does not match authenticated user or session");
        }
    }

    private void validateActiveSession(UUID userId, String sessionIdentifier) {
        if (sessionIdentifier != null && !sessionIdentifier.isBlank()) {
            Optional<UserSession> sessionOpt = sessionRepository.findBySessionIdentifierAndUserId(sessionIdentifier, userId);
            if (sessionOpt.isEmpty() || !sessionOpt.get().isActive()) {
                throw ApiException.unauthorized("Session has been revoked or expired");
            }
        }
    }

    private void validateBaseAuthorization(UUID userId, StepUpAction action, StepUpContext context) {
        if (effectiveAccessService != null && context != null && context.workspaceId() != null) {
            AccessPermission permission = mapActionToPermission(action);
            if (permission != null && context.projectId() != null && context.environmentId() != null) {
                effectiveAccessService.checkPermission(
                        context.workspaceId(),
                        context.projectId(),
                        context.environmentId(),
                        null,
                        permission,
                        userId
                );
            }
        }
    }

    private AccessPermission mapActionToPermission(StepUpAction action) {
        return switch (action) {
            case SECRET_REVEAL -> AccessPermission.SECRET_REVEAL;
            case SECRET_DELETE -> AccessPermission.SECRET_DELETE;
            case SECRET_ROLLBACK -> AccessPermission.SECRET_ROLLBACK;
            case ENVIRONMENT_PROMOTE -> AccessPermission.ENVIRONMENT_PROMOTE;
            case ACCESS_GRANT, ACCESS_REVOKE -> AccessPermission.ACCESS_MANAGE;
            case JIT_APPROVE -> AccessPermission.JIT_APPROVE;
            default -> null;
        };
    }

    private void handleFailedAttempt(String challengeId, UUID userId, int maxAttempts) {
        try {
            long attemptCount = securityStateStore.incrementAttempts(
                    CATEGORY_STEP_UP_CHALLENGE,
                    challengeId,
                    Duration.ofSeconds(CHALLENGE_TTL_SECONDS)
            );
            if (attemptCount >= maxAttempts) {
                securityStateStore.delete(CATEGORY_STEP_UP_CHALLENGE, challengeId);
                auditService.recordAudit(
                        null,
                        null,
                        userId,
                        "USER",
                        AuditAction.STEP_UP_VERIFICATION_FAILED,
                        "STEP_UP_CHALLENGE",
                        userId,
                        challengeId,
                        null,
                        "MAX_ATTEMPTS_EXCEEDED"
                );
            }
        } catch (Exception ex) {
            log.error("Failed to increment security attempts for step-up challenge [{}]", challengeId, ex);
        }
    }

    private String generateProofToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "stup_" + HexFormat.of().formatHex(bytes);
    }
}
