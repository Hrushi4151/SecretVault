package com.secretvault.secret.reveal.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.repository.AuditLogRepository;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.session.entity.UserSession;
import com.secretvault.auth.session.service.SessionService;
import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpContext;
import com.secretvault.auth.stepup.service.StepUpAuthenticationService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.security.state.SecurityStateStore;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.dto.SecretRevealResponse;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import com.secretvault.secret.reveal.dto.CreateRevealIntentRequest;
import com.secretvault.secret.reveal.dto.ExecuteRevealRequest;
import com.secretvault.secret.reveal.dto.SecretRevealAuditResponse;
import com.secretvault.secret.reveal.dto.SecretRevealIntentResponse;
import com.secretvault.secret.reveal.model.SecretRevealIntentPayload;
import com.secretvault.secret.reveal.model.SecretRevealPolicyEvaluation;
import com.secretvault.secret.service.SecretAuthorizationHelper;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class DefaultSecretRevealService implements SecretRevealService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSecretRevealService.class);
    private static final long DEFAULT_INTENT_TTL_SECONDS = 60L;

    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final EncryptionService encryptionService;
    private final EffectiveAccessService effectiveAccessService;
    private final SecretAuthorizationHelper authHelper;
    private final SecretRevealPolicyService policyService;
    private final StepUpAuthenticationService stepUpAuthenticationService;
    private final SecurityStateStore securityStateStore;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final SessionService sessionService;
    private final SecurityEventService securityEventService;
    private final SecureRandom secureRandom = new SecureRandom();

    public DefaultSecretRevealService(
            SecretRepository secretRepository,
            SecretVersionRepository secretVersionRepository,
            EncryptionService encryptionService,
            EffectiveAccessService effectiveAccessService,
            SecretAuthorizationHelper authHelper,
            SecretRevealPolicyService policyService,
            @Autowired(required = false) StepUpAuthenticationService stepUpAuthenticationService,
            SecurityStateStore securityStateStore,
            AuditService auditService,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            EnvironmentRepository environmentRepository,
            ProjectRepository projectRepository,
            @Autowired(required = false) SessionService sessionService,
            @Autowired(required = false) SecurityEventService securityEventService
    ) {
        this.secretRepository = secretRepository;
        this.secretVersionRepository = secretVersionRepository;
        this.encryptionService = encryptionService;
        this.effectiveAccessService = effectiveAccessService;
        this.authHelper = authHelper;
        this.policyService = policyService;
        this.stepUpAuthenticationService = stepUpAuthenticationService;
        this.securityStateStore = securityStateStore;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.sessionService = sessionService;
        this.securityEventService = securityEventService;
    }

    @Override
    @Transactional(readOnly = true)
    public SecretRevealPolicyEvaluation getRevealPolicy(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID userId
    ) {
        authHelper.verifyHierarchy(workspaceId, projectId, environmentId, userId);
        return policyService.evaluatePolicy(workspaceId, projectId, environmentId, secretId, userId);
    }

    @Override
    @Transactional
    public SecretRevealIntentResponse createRevealIntent(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            CreateRevealIntentRequest request,
            UUID userId,
            String sessionIdentifier,
            String requestId,
            String ipAddress
    ) {
        // 1. Authenticated user validation
        validateActiveUser(userId);

        // 2. Session validation
        validateActiveSession(sessionIdentifier, userId);

        // 3. Verify hierarchy and base SECRET_REVEAL authorization
        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndRevealAccess(
                workspaceId, projectId, environmentId, userId
        );

        // 4. Validate Secret status
        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        if (secret.getStatus() == SecretStatus.DELETED) {
            throw ApiException.badRequest("Cannot reveal a deleted secret");
        }

        if (secret.getStatus() == SecretStatus.DISABLED) {
            throw ApiException.badRequest("Cannot reveal a disabled secret. Please enable the secret first.");
        }

        // 5. Target version validation
        Integer targetVersion = request != null && request.versionNumber() != null && request.versionNumber() > 0
                ? request.versionNumber()
                : secret.getCurrentVersionNumber();

        secretVersionRepository.findBySecretIdAndVersionNumber(secretId, targetVersion)
                .orElseThrow(() -> ApiException.notFound("Secret version " + targetVersion + " not found"));

        // 6. Evaluate Secret Reveal Policy
        SecretRevealPolicyEvaluation policy = policyService.evaluatePolicy(
                workspaceId, projectId, environmentId, secretId, userId
        );

        // 7. Validate justification reason
        String reason = request != null ? request.reason() : null;
        policyService.validateReason(reason, policy);

        // 8. Enforce Step-Up Authentication if required by policy
        if (policy.requireStepUp()) {
            if (stepUpAuthenticationService == null) {
                throw ApiException.internal("STEP_UP_UNAVAILABLE", "Step-up authentication service is unavailable");
            }
            if (request == null || request.stepUpProof() == null || request.stepUpProof().isBlank()) {
                throw ApiException.forbidden("STEP_UP_REQUIRED", "Step-up authentication is required to reveal secrets in this protected environment");
            }

            StepUpContext stepUpCtx = StepUpContext.forSecret(workspaceId, projectId, environmentId, secretId);
            stepUpAuthenticationService.verifyAndConsumeProof(
                    request.stepUpProof(),
                    userId,
                    sessionIdentifier,
                    StepUpAction.SECRET_REVEAL,
                    stepUpCtx
            );
        }

        // 9. Generate cryptographically strong single-use intent token
        String intentToken = generateSecureToken();

        // 10. Persist intent payload in Redis Security State Store with short TTL
        SecretRevealIntentPayload payload = SecretRevealIntentPayload.create(
                intentToken,
                userId,
                sessionIdentifier,
                workspaceId,
                projectId,
                environmentId,
                secretId,
                targetVersion,
                policy.policyLevel(),
                policy.copyAllowed(),
                policy.maxDisplayDurationSeconds(),
                policy.clipboardTimeoutSeconds(),
                reason,
                DEFAULT_INTENT_TTL_SECONDS
        );

        securityStateStore.put(CATEGORY_SECRET_REVEAL_INTENT, intentToken, payload, Duration.ofSeconds(DEFAULT_INTENT_TTL_SECONDS));

        // 11. Audit and Telemetry
        auditService.recordSecretAudit(
                context.workspace().getOrganizationId(),
                workspaceId,
                userId,
                AuditAction.SECRET_REVEAL_INTENT_CREATED,
                secret.getId(),
                requestId,
                ipAddress,
                "SUCCESS"
        );

        emitSecurityEvent(SecurityEventType.SECRET_REVEAL_INTENT_CREATED, workspaceId, projectId, environmentId, userId, secretId, "Reveal intent created");

        log.info("Issued secret reveal intent token for secret [{}] version [{}] to user [{}]",
                secret.getId(), targetVersion, userId);

        return new SecretRevealIntentResponse(
                intentToken,
                payload.expiresAt(),
                policy.maxDisplayDurationSeconds(),
                policy.copyAllowed(),
                policy.clipboardTimeoutSeconds(),
                policy.policyLevel(),
                policy.requireReason(),
                policy.requireStepUp(),
                policy.allowedStepUpFactors()
        );
    }

    @Override
    @Transactional
    public SecretRevealResponse executeReveal(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            ExecuteRevealRequest request,
            UUID userId,
            String sessionIdentifier,
            String requestId,
            String ipAddress
    ) {
        // 1. Authenticated user validation
        validateActiveUser(userId);

        // 2. Session validation
        validateActiveSession(sessionIdentifier, userId);

        // 3. Resolve execution parameters & policy
        Integer requestedVersion = request != null ? request.versionNumber() : null;
        SecretRevealPolicyEvaluation policy = policyService.evaluatePolicy(workspaceId, projectId, environmentId, secretId, userId);

        if (request != null && request.intentToken() != null && !request.intentToken().isBlank()) {
            // Flow A: Two-Phase Reveal with Single-Use Intent Token
            String token = request.intentToken().trim();
            Optional<SecretRevealIntentPayload> intentOpt = securityStateStore.consumeAtomic(
                    CATEGORY_SECRET_REVEAL_INTENT, token, SecretRevealIntentPayload.class
            );

            if (intentOpt.isEmpty()) {
                emitSecurityEvent(SecurityEventType.SECRET_REVEAL_DENIED, workspaceId, projectId, environmentId, userId, secretId, "Invalid, expired, or replayed reveal intent token");
                auditService.recordSecretAudit(null, workspaceId, userId, AuditAction.SECRET_REVEAL_REPLAY_BLOCKED, secretId, requestId, ipAddress, "DENIED");
                throw ApiException.forbidden("INVALID_OR_EXPIRED_INTENT", "Reveal intent is invalid, expired, or already consumed");
            }

            SecretRevealIntentPayload intent = intentOpt.get();

            if (!intent.matches(userId, sessionIdentifier, workspaceId, projectId, environmentId, secretId, requestedVersion)) {
                emitSecurityEvent(SecurityEventType.SECRET_REVEAL_DENIED, workspaceId, projectId, environmentId, userId, secretId, "Contextual mismatch on reveal intent token");
                auditService.recordSecretAudit(null, workspaceId, userId, AuditAction.SECRET_REVEAL_DENIED, secretId, requestId, ipAddress, "DENIED");
                throw ApiException.forbidden("INTENT_CONTEXT_MISMATCH", "Reveal intent does not match the requested resource, version, or session");
            }

            if (intent.versionNumber() != null) {
                requestedVersion = intent.versionNumber();
            }
        } else {
            // Flow B: Direct Single-Phase Atomic Reveal
            policyService.validateReason(request != null ? request.reason() : null, policy);

            if (policy.requireStepUp()) {
                if (stepUpAuthenticationService == null) {
                    throw ApiException.internal("STEP_UP_UNAVAILABLE", "Step-up authentication service is unavailable");
                }
                if (request == null || request.stepUpProof() == null || request.stepUpProof().isBlank()) {
                    throw ApiException.forbidden("STEP_UP_REQUIRED", "Step-up authentication is required to reveal secrets in this protected environment");
                }

                StepUpContext stepUpCtx = StepUpContext.forSecret(workspaceId, projectId, environmentId, secretId);
                stepUpAuthenticationService.verifyAndConsumeProof(
                        request.stepUpProof(),
                        userId,
                        sessionIdentifier,
                        StepUpAction.SECRET_REVEAL,
                        stepUpCtx
                );
            }
        }

        // 4. Time-of-Use Authorization Re-Evaluation (TOCTOU Defense)
        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndRevealAccess(
                workspaceId, projectId, environmentId, userId
        );

        // 5. Target Secret & Version validation
        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        if (secret.getStatus() == SecretStatus.DELETED) {
            throw ApiException.badRequest("Cannot reveal a deleted secret");
        }

        if (secret.getStatus() == SecretStatus.DISABLED) {
            throw ApiException.badRequest("Cannot reveal a disabled secret. Please enable the secret first.");
        }

        int targetVersionNumber = (requestedVersion != null && requestedVersion > 0)
                ? requestedVersion
                : secret.getCurrentVersionNumber();

        SecretVersion version = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, targetVersionNumber)
                .orElseThrow(() -> ApiException.notFound("Secret version " + targetVersionNumber + " not found"));

        // 6. In-Memory Cryptographic Decryption & Zeroization
        EncryptedPayload encryptedPayload = new EncryptedPayload(
                version.getCiphertext(),
                version.getEncryptedDek(),
                version.getIv(),
                version.getAuthTag(),
                version.getKeyReference()
        );

        String aad = SecretAuthorizationHelper.buildAad(secret.getId(), environmentId, version.getVersionNumber());
        byte[] plaintextBytes = encryptionService.decrypt(encryptedPayload, aad);
        String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);

        // Explicit byte-level zeroization of decrypted memory buffer
        Arrays.fill(plaintextBytes, (byte) 0);

        // 7. Immutable Audit Record & Telemetry
        boolean isHistorical = !version.getVersionNumber().equals(secret.getCurrentVersionNumber());
        AuditAction auditAction = isHistorical ? AuditAction.SECRET_HISTORICAL_REVEALED : AuditAction.SECRET_REVEALED;
        SecurityEventType eventType = isHistorical ? SecurityEventType.SECRET_HISTORICAL_REVEALED : SecurityEventType.SECRET_REVEALED;

        auditService.recordSecretAudit(
                context.workspace().getOrganizationId(),
                workspaceId,
                userId,
                auditAction,
                secret.getId(),
                requestId,
                ipAddress,
                "SUCCESS"
        );

        emitSecurityEvent(eventType, workspaceId, projectId, environmentId, userId, secret.getId(),
                "Secret revealed (version " + version.getVersionNumber() + ")");

        log.info("Secret [{}] (version {}) revealed by user [{}] in workspace [{}]",
                secret.getId(), version.getVersionNumber(), userId, workspaceId);

        return new SecretRevealResponse(
                secret.getId(),
                environmentId,
                secret.getName(),
                version.getVersionNumber(),
                plaintext,
                Instant.now(),
                policy.maxDisplayDurationSeconds(),
                policy.copyAllowed(),
                policy.clipboardTimeoutSeconds(),
                policy.policyLevel()
        );
    }

    @Override
    @Transactional
    public SecretRevealResponse revealHistoricalVersion(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer versionNumber,
            ExecuteRevealRequest request,
            UUID userId,
            String sessionIdentifier,
            String requestId,
            String ipAddress
    ) {
        ExecuteRevealRequest req = new ExecuteRevealRequest(
                request != null ? request.intentToken() : null,
                versionNumber,
                request != null ? request.reason() : null,
                request != null ? request.stepUpProof() : null
        );
        return executeReveal(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, requestId, ipAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SecretRevealAuditResponse> getRevealAuditHistory(
            UUID workspaceId,
            UUID actorUserId,
            Pageable pageable
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.SECURITY_VIEW, actorUserId);

        List<AuditAction> revealActions = List.of(
                AuditAction.SECRET_REVEALED,
                AuditAction.SECRET_HISTORICAL_REVEALED,
                AuditAction.SECRET_REVEAL_DENIED,
                AuditAction.SECRET_REVEAL_INTENT_CREATED,
                AuditAction.SECRET_REVEAL_REPLAY_BLOCKED
        );

        Page<AuditLog> auditPage = auditLogRepository.findByWorkspaceIdAndActionInOrderByCreatedAtDesc(
                workspaceId, revealActions, pageable
        );

        List<SecretRevealAuditResponse> dtoList = auditPage.getContent().stream().map(logEntry -> {
            String actorEmail = null;
            if (logEntry.getActorId() != null) {
                actorEmail = userRepository.findById(logEntry.getActorId())
                        .map(User::getEmail)
                        .orElse(logEntry.getActorId().toString());
            }

            String secretName = null;
            UUID envId = null;
            String envName = null;
            if (logEntry.getResourceId() != null) {
                Optional<Secret> sOpt = secretRepository.findById(logEntry.getResourceId());
                if (sOpt.isPresent()) {
                    Secret s = sOpt.get();
                    secretName = s.getName();
                    envId = s.getEnvironmentId();
                    envName = environmentRepository.findById(envId).map(Environment::getName).orElse(null);
                }
            }

            return new SecretRevealAuditResponse(
                    logEntry.getId(),
                    logEntry.getWorkspaceId(),
                    logEntry.getActorId(),
                    actorEmail,
                    logEntry.getAction().name(),
                    logEntry.getResourceId(),
                    secretName,
                    null,
                    envId,
                    envName,
                    logEntry.getOutcome(),
                    null,
                    logEntry.getIpAddress(),
                    logEntry.getRequestId(),
                    logEntry.getCreatedAt()
            );
        }).toList();

        return new PageImpl<>(dtoList, pageable, auditPage.getTotalElements());
    }

    private void validateActiveUser(UUID userId) {
        if (userId == null) {
            throw ApiException.unauthorized("Authentication is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User account not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("User account is disabled");
        }
    }

    private void validateActiveSession(String sessionIdentifier, UUID userId) {
        if (sessionService != null && sessionIdentifier != null && !sessionIdentifier.isBlank()) {
            Optional<UserSession> sessionOpt = sessionService.findByIdentifier(sessionIdentifier);
            if (sessionOpt.isEmpty() || !sessionOpt.get().isActive() || !sessionOpt.get().getUserId().equals(userId)) {
                throw ApiException.unauthorized("Active session has been revoked or expired");
            }
        }
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return "sec_rev_" + HexFormat.of().formatHex(randomBytes);
    }

    private void emitSecurityEvent(SecurityEventType type, UUID workspaceId, UUID projectId, UUID environmentId, UUID userId, UUID resourceId, String details) {
        if (securityEventService != null) {
            try {
                securityEventService.recordEvent(
                        workspaceId,
                        projectId,
                        environmentId,
                        userId,
                        type,
                        SecurityEventSeverity.INFO,
                        SecurityEventOutcome.SUCCESS,
                        "SECRET_REVEAL",
                        null,
                        null,
                        details,
                        resourceId != null ? Map.of("secretId", resourceId.toString()) : Map.of()
                );
            } catch (Exception ex) {
                log.warn("Failed to record security event [{}]: {}", type, ex.getMessage());
            }
        }
    }
}
