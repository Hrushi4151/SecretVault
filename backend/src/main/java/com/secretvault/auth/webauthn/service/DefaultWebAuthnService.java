package com.secretvault.auth.webauthn.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.dto.UserResponse;
import com.secretvault.auth.entity.RefreshToken;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.mfa.service.MfaService;
import com.secretvault.auth.repository.RefreshTokenRepository;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.security.JwtTokenProvider;
import com.secretvault.auth.session.entity.UserSession;
import com.secretvault.auth.session.enums.AuthMethod;
import com.secretvault.auth.session.repository.UserSessionRepository;
import com.secretvault.auth.session.service.SessionService;
import com.secretvault.auth.session.util.UserAgentParser;
import com.secretvault.auth.stepup.dto.StepUpProofResponse;
import com.secretvault.auth.stepup.model.StepUpChallengePayload;
import com.secretvault.auth.stepup.model.StepUpFactor;
import com.secretvault.auth.stepup.model.StepUpProofPayload;
import com.secretvault.auth.webauthn.config.WebAuthnProperties;
import com.secretvault.auth.webauthn.dto.WebAuthnAuthenticationOptionsResponse;
import com.secretvault.auth.webauthn.dto.WebAuthnCredentialResponse;
import com.secretvault.auth.webauthn.dto.WebAuthnRegistrationOptionsResponse;
import com.secretvault.auth.webauthn.entity.UserWebAuthnCredential;
import com.secretvault.auth.webauthn.model.WebAuthnAuthenticationChallengePayload;
import com.secretvault.auth.webauthn.model.WebAuthnCeremonyType;
import com.secretvault.auth.webauthn.model.WebAuthnRegistrationChallengePayload;
import com.secretvault.auth.webauthn.repository.UserWebAuthnCredentialRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.security.state.SecurityStateStore;
import com.secretvault.workspace.dto.WorkspaceResponse;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link WebAuthnService}.
 * Enforces cryptographic verification via Yubico RelyingParty, Redis single-use ephemeral challenges,
 * session binding, clone detection handling, and comprehensive audit logging.
 */
@Service
public class DefaultWebAuthnService implements WebAuthnService {

    private static final Logger log = LoggerFactory.getLogger(DefaultWebAuthnService.class);

    private static final String CATEGORY_REG_CHALLENGE = "webauthn_reg_challenge";
    private static final String CATEGORY_AUTH_CHALLENGE = "webauthn_auth_challenge";
    private static final String CATEGORY_STEP_UP_CHALLENGE = "step_up_challenge";
    private static final String CATEGORY_STEP_UP_PROOF = "step_up_proof";
    private static final long REFRESH_TOKEN_EXPIRATION_DAYS = 30;

    private final RelyingParty relyingParty;
    private final WebAuthnProperties properties;
    private final UserWebAuthnCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final SecurityStateStore securityStateStore;
    private final SessionService sessionService;
    private final JwtTokenProvider tokenProvider;
    private final AuditService auditService;
    private final MfaService mfaService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public DefaultWebAuthnService(
            RelyingParty relyingParty,
            WebAuthnProperties properties,
            UserWebAuthnCredentialRepository credentialRepository,
            UserRepository userRepository,
            UserSessionRepository sessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            SecurityStateStore securityStateStore,
            SessionService sessionService,
            JwtTokenProvider tokenProvider,
            AuditService auditService,
            MfaService mfaService,
            ObjectMapper objectMapper
    ) {
        this.relyingParty = Objects.requireNonNull(relyingParty, "RelyingParty must not be null");
        this.properties = Objects.requireNonNull(properties, "WebAuthnProperties must not be null");
        this.credentialRepository = Objects.requireNonNull(credentialRepository, "UserWebAuthnCredentialRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "UserSessionRepository must not be null");
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "RefreshTokenRepository must not be null");
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository, "WorkspaceRepository must not be null");
        this.membershipRepository = Objects.requireNonNull(membershipRepository, "WorkspaceMembershipRepository must not be null");
        this.securityStateStore = Objects.requireNonNull(securityStateStore, "SecurityStateStore must not be null");
        this.sessionService = Objects.requireNonNull(sessionService, "SessionService must not be null");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "JwtTokenProvider must not be null");
        this.auditService = Objects.requireNonNull(auditService, "AuditService must not be null");
        this.mfaService = Objects.requireNonNull(mfaService, "MfaService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public WebAuthnRegistrationOptionsResponse startRegistration(
            UUID userId,
            String sessionIdentifier,
            String friendlyName
    ) {
        if (userId == null) {
            throw ApiException.unauthorized("Authentication required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("User account is inactive");
        }

        validateActiveSession(userId, sessionIdentifier);

        String trimmedName = (friendlyName != null && !friendlyName.isBlank())
                ? friendlyName.trim()
                : "Passkey (" + Instant.now().toString().substring(0, 10) + ")";

        try {
            UserIdentity userIdentity = UserIdentity.builder()
                    .name(user.getEmail())
                    .displayName(user.getFullName())
                    .id(new ByteArray(user.getId().toString().getBytes(StandardCharsets.UTF_8)))
                    .build();

            UserVerificationRequirement uv = "REQUIRED".equalsIgnoreCase(properties.getUserVerification())
                    ? UserVerificationRequirement.REQUIRED
                    : UserVerificationRequirement.PREFERRED;

            StartRegistrationOptions startOptions = StartRegistrationOptions.builder()
                    .user(userIdentity)
                    .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                            .residentKey(ResidentKeyRequirement.PREFERRED)
                            .userVerification(uv)
                            .build())
                    .timeout(properties.getTimeoutSeconds() * 1000)
                    .build();

            PublicKeyCredentialCreationOptions creationOptions = relyingParty.startRegistration(startOptions);
            String optionsJson = creationOptions.toJson();

            String challengeId = UUID.randomUUID().toString();
            long ttl = properties.getChallengeTtlSeconds();

            WebAuthnRegistrationChallengePayload payload = WebAuthnRegistrationChallengePayload.of(
                    challengeId,
                    userId,
                    sessionIdentifier,
                    trimmedName,
                    optionsJson,
                    ttl
            );

            securityStateStore.put(CATEGORY_REG_CHALLENGE, challengeId, payload, Duration.ofSeconds(ttl));

            auditService.recordAudit(
                    null,
                    null,
                    userId,
                    "USER",
                    AuditAction.WEBAUTHN_REGISTRATION_STARTED,
                    "USER",
                    userId,
                    challengeId,
                    null,
                    "SUCCESS"
            );

            return new WebAuthnRegistrationOptionsResponse(
                    challengeId,
                    optionsJson,
                    payload.getExpiresAt()
            );

        } catch (Exception ex) {
            log.error("Failed to generate WebAuthn registration options: {}", ex.getMessage(), ex);
            throw ApiException.internal("WEBAUTHN_REGISTRATION_FAILED", "Failed to start WebAuthn registration", ex);
        }
    }

    @Override
    @Transactional
    public WebAuthnCredentialResponse finishRegistration(
            UUID userId,
            String sessionIdentifier,
            String challengeId,
            String friendlyName,
            String credentialJson
    ) {
        if (userId == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        if (challengeId == null || challengeId.isBlank()) {
            throw ApiException.badRequest("challengeId is required");
        }
        if (credentialJson == null || credentialJson.isBlank()) {
            throw ApiException.badRequest("credentialJson is required");
        }

        validateActiveSession(userId, sessionIdentifier);

        // 1. Atomically consume registration challenge (Single-use guarantee)
        Optional<WebAuthnRegistrationChallengePayload> challengeOpt = securityStateStore.consumeAtomic(
                CATEGORY_REG_CHALLENGE,
                challengeId,
                WebAuthnRegistrationChallengePayload.class
        );

        if (challengeOpt.isEmpty()) {
            auditService.recordAudit(
                    null,
                    null,
                    userId,
                    "USER",
                    AuditAction.WEBAUTHN_REGISTRATION_FAILED,
                    "USER",
                    userId,
                    challengeId,
                    null,
                    "INVALID_CHALLENGE"
            );
            throw ApiException.badRequest("WEBAUTHN_CHALLENGE_INVALID", "Invalid or expired registration challenge");
        }

        WebAuthnRegistrationChallengePayload payload = challengeOpt.get();
        if (!payload.getUserId().equals(userId)) {
            log.warn("Security violation: User [{}] attempted to complete registration for challenge owned by [{}]",
                    userId, payload.getUserId());
            auditService.recordAudit(
                    null,
                    null,
                    userId,
                    "USER",
                    AuditAction.WEBAUTHN_REGISTRATION_FAILED,
                    "USER",
                    userId,
                    challengeId,
                    null,
                    "USER_MISMATCH"
            );
            throw ApiException.forbidden("Registration challenge does not belong to the authenticated user");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        try {
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc =
                    PublicKeyCredential.parseRegistrationResponseJson(credentialJson);

            PublicKeyCredentialCreationOptions creationOptions =
                    PublicKeyCredentialCreationOptions.fromJson(payload.getRequestJson());

            FinishRegistrationOptions finishOptions = FinishRegistrationOptions.builder()
                    .request(creationOptions)
                    .response(pkc)
                    .build();

            RegistrationResult result = relyingParty.finishRegistration(finishOptions);

            String base64UrlCredentialId = result.getKeyId().getId().getBase64Url();

            if (credentialRepository.existsByCredentialId(base64UrlCredentialId)) {
                throw ApiException.conflict("This credential has already been registered");
            }

            byte[] publicKeyCose = result.getPublicKeyCose().getBytes();
            long signCount = result.getSignatureCount();
            String aaguid = result.getAaguid() != null ? result.getAaguid().getHex() : null;
            String attestationFormat = result.getAttestationType() != null ? result.getAttestationType().name().toLowerCase() : "none";

            // Extract transports from client JSON if present
            String transports = extractTransports(credentialJson);

            String effectiveName = (friendlyName != null && !friendlyName.isBlank())
                    ? friendlyName.trim()
                    : payload.getFriendlyName();

            HttpServletRequest currentRequest = getCurrentHttpRequest();
            String clientIp = UserAgentParser.extractClientIp(currentRequest);

            UserWebAuthnCredential credential = new UserWebAuthnCredential(
                    userId,
                    base64UrlCredentialId,
                    publicKeyCose,
                    signCount,
                    aaguid,
                    attestationFormat,
                    transports,
                    true,
                    result.isBackupEligible(),
                    result.isBackedUp(),
                    result.isDiscoverable().orElse(true),
                    effectiveName
            );
            credential.setLastUsedIp(clientIp);

            UserWebAuthnCredential saved = credentialRepository.save(credential);

            auditService.recordAudit(
                    null,
                    null,
                    userId,
                    "USER",
                    AuditAction.WEBAUTHN_REGISTRATION_SUCCESS,
                    "WEBAUTHN_CREDENTIAL",
                    saved.getId(),
                    challengeId,
                    clientIp,
                    "SUCCESS"
            );

            log.info("Registered WebAuthn credential [{}] for user [{}]", saved.getId(), userId);

            return WebAuthnCredentialResponse.fromEntity(saved);

        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("WebAuthn registration verification failed: {}", ex.getMessage(), ex);
            auditService.recordAudit(
                    null,
                    null,
                    userId,
                    "USER",
                    AuditAction.WEBAUTHN_REGISTRATION_FAILED,
                    "USER",
                    userId,
                    challengeId,
                    null,
                    "VERIFICATION_FAILED"
            );
            throw ApiException.badRequest("WEBAUTHN_VERIFICATION_FAILED", "WebAuthn registration verification failed: " + ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public WebAuthnAuthenticationOptionsResponse startAuthentication(String email) {
        try {
            var startAssertionBuilder = StartAssertionOptions.builder();

            if (email != null && !email.isBlank()) {
                String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
                User user = userRepository.findByEmail(normalizedEmail)
                        .orElseThrow(() -> ApiException.unauthorized("Invalid email or credentials"));
                if (user.getStatus() != UserStatus.ACTIVE) {
                    throw ApiException.forbidden("User account is inactive");
                }
                startAssertionBuilder.username(Optional.of(normalizedEmail));
            }

            UserVerificationRequirement uv = "REQUIRED".equalsIgnoreCase(properties.getUserVerification())
                    ? UserVerificationRequirement.REQUIRED
                    : UserVerificationRequirement.PREFERRED;

            startAssertionBuilder.userVerification(uv);
            startAssertionBuilder.timeout(properties.getTimeoutSeconds() * 1000);

            AssertionRequest assertionRequest = relyingParty.startAssertion(startAssertionBuilder.build());
            String requestJson = assertionRequest.toJson();

            String challengeId = UUID.randomUUID().toString();
            long ttl = properties.getChallengeTtlSeconds();

            WebAuthnAuthenticationChallengePayload payload = WebAuthnAuthenticationChallengePayload.of(
                    challengeId,
                    null,
                    null,
                    WebAuthnCeremonyType.LOGIN,
                    requestJson,
                    null,
                    null,
                    ttl
            );

            securityStateStore.put(CATEGORY_AUTH_CHALLENGE, challengeId, payload, Duration.ofSeconds(ttl));

            auditService.recordAudit(
                    null,
                    null,
                    null,
                    "USER",
                    AuditAction.WEBAUTHN_AUTHENTICATION_STARTED,
                    "WEBAUTHN_CHALLENGE",
                    null,
                    challengeId,
                    null,
                    "SUCCESS"
            );

            return new WebAuthnAuthenticationOptionsResponse(
                    challengeId,
                    requestJson,
                    payload.getExpiresAt()
            );

        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to generate WebAuthn authentication options: {}", ex.getMessage(), ex);
            throw ApiException.internal("WEBAUTHN_AUTH_OPTIONS_FAILED", "Failed to generate authentication options", ex);
        }
    }

    @Override
    @Transactional
    public AuthResponse finishAuthentication(String challengeId, String credentialJson) {
        if (challengeId == null || challengeId.isBlank()) {
            throw ApiException.badRequest("challengeId is required");
        }
        if (credentialJson == null || credentialJson.isBlank()) {
            throw ApiException.badRequest("credentialJson is required");
        }

        // 1. Atomically consume authentication challenge (Single-use guarantee)
        Optional<WebAuthnAuthenticationChallengePayload> challengeOpt = securityStateStore.consumeAtomic(
                CATEGORY_AUTH_CHALLENGE,
                challengeId,
                WebAuthnAuthenticationChallengePayload.class
        );

        if (challengeOpt.isEmpty()) {
            auditService.recordAudit(
                    null,
                    null,
                    null,
                    "USER",
                    AuditAction.WEBAUTHN_AUTHENTICATION_FAILED,
                    "WEBAUTHN_CHALLENGE",
                    null,
                    challengeId,
                    null,
                    "INVALID_CHALLENGE"
            );
            throw ApiException.badRequest("WEBAUTHN_CHALLENGE_INVALID", "Invalid or expired authentication challenge");
        }

        WebAuthnAuthenticationChallengePayload payload = challengeOpt.get();

        try {
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc =
                    PublicKeyCredential.parseAssertionResponseJson(credentialJson);

            AssertionRequest assertionRequest = AssertionRequest.fromJson(payload.getRequestJson());

            FinishAssertionOptions finishOptions = FinishAssertionOptions.builder()
                    .request(assertionRequest)
                    .response(pkc)
                    .build();

            AssertionResult result = relyingParty.finishAssertion(finishOptions);

            if (!result.isSuccess()) {
                auditService.recordAudit(
                        null,
                        null,
                        null,
                        "USER",
                        AuditAction.WEBAUTHN_AUTHENTICATION_FAILED,
                        "WEBAUTHN_CHALLENGE",
                        null,
                        challengeId,
                        null,
                        "ASSERTION_UNSUCCESSFUL"
                );
                throw ApiException.unauthorized("WEBAUTHN_ASSERTION_INVALID", "WebAuthn assertion verification failed");
            }

            String username = result.getUsername();
            User user = userRepository.findByEmail(username.trim().toLowerCase())
                    .orElseThrow(() -> ApiException.unauthorized("User associated with assertion not found"));

            if (user.getStatus() != UserStatus.ACTIVE) {
                throw ApiException.forbidden("User account is inactive");
            }

            // 2. Validate Credential & Signature Counter (Clone Detection)
            String base64UrlCredentialId = result.getCredential().getCredentialId().getBase64Url();
            UserWebAuthnCredential credential = credentialRepository
                    .findByCredentialIdAndRevokedAtIsNull(base64UrlCredentialId)
                    .orElseThrow(() -> ApiException.unauthorized("WEBAUTHN_CREDENTIAL_REVOKED", "Credential not found or revoked"));

            long storedSignCount = credential.getSignCount();
            long newSignCount = result.getSignatureCount();

            if (storedSignCount > 0 && newSignCount <= storedSignCount) {
                log.warn("CLONE DETECTED: Credential [{}] for user [{}] signature counter rollback: stored={}, received={}",
                        credential.getId(), user.getId(), storedSignCount, newSignCount);

                auditService.recordAudit(
                        null,
                        null,
                        user.getId(),
                        "USER",
                        AuditAction.WEBAUTHN_CLONE_DETECTED,
                        "WEBAUTHN_CREDENTIAL",
                        credential.getId(),
                        challengeId,
                        null,
                        "COUNTER_ROLLBACK"
                );

                if ("BLOCK".equalsIgnoreCase(properties.getCloneDetectionAction())) {
                    throw ApiException.unauthorized("WEBAUTHN_CLONE_DETECTED", "Authenticator clone anomaly detected");
                }
            }

            HttpServletRequest currentRequest = getCurrentHttpRequest();
            String clientIp = UserAgentParser.extractClientIp(currentRequest);
            credential.recordUsage(newSignCount, clientIp);
            credentialRepository.save(credential);

            // 3. Establish Session & Generate Auth Response
            WorkspaceMembership primaryMembership = resolvePrimaryMembership(user.getId());
            Workspace workspace = workspaceRepository.findById(primaryMembership.getWorkspaceId())
                    .orElseThrow(() -> ApiException.notFound("Assigned workspace could not be found"));

            auditService.recordAudit(
                    null,
                    workspace.getId(),
                    user.getId(),
                    "USER",
                    AuditAction.WEBAUTHN_AUTHENTICATION_SUCCESS,
                    "USER",
                    user.getId(),
                    challengeId,
                    clientIp,
                    "SUCCESS"
            );

            log.info("User [{}] authenticated successfully with WebAuthn credential [{}]", user.getId(), credential.getId());

            return generateAuthResponse(user, workspace, primaryMembership.getRole(), AuthMethod.WEBAUTHN_PASSKEY, null);

        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("WebAuthn assertion verification failed: {}", ex.getMessage(), ex);
            auditService.recordAudit(
                    null,
                    null,
                    null,
                    "USER",
                    AuditAction.WEBAUTHN_AUTHENTICATION_FAILED,
                    "WEBAUTHN_CHALLENGE",
                    null,
                    challengeId,
                    null,
                    "VERIFICATION_FAILED"
            );
            throw ApiException.unauthorized("WEBAUTHN_ASSERTION_INVALID", "WebAuthn assertion verification failed: " + ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public WebAuthnAuthenticationOptionsResponse startStepUpAssertion(
            UUID userId,
            String sessionIdentifier,
            String stepUpChallengeId
    ) {
        if (userId == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        if (stepUpChallengeId == null || stepUpChallengeId.isBlank()) {
            throw ApiException.badRequest("stepUpChallengeId is required");
        }

        validateActiveSession(userId, sessionIdentifier);

        // Verify base step-up challenge exists in Redis
        Optional<StepUpChallengePayload> stepUpPayloadOpt = securityStateStore.get(
                CATEGORY_STEP_UP_CHALLENGE,
                stepUpChallengeId,
                StepUpChallengePayload.class
        );

        if (stepUpPayloadOpt.isEmpty()) {
            throw ApiException.badRequest("STEP_UP_CHALLENGE_EXPIRED", "Step-up challenge not found or expired");
        }

        StepUpChallengePayload stepUpPayload = stepUpPayloadOpt.get();
        if (!stepUpPayload.userId().equals(userId)) {
            throw ApiException.forbidden("Step-up challenge does not belong to caller");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        try {
            var startAssertionBuilder = StartAssertionOptions.builder();
            startAssertionBuilder.username(Optional.of(user.getEmail()));

            UserVerificationRequirement uv = "REQUIRED".equalsIgnoreCase(properties.getUserVerification())
                    ? UserVerificationRequirement.REQUIRED
                    : UserVerificationRequirement.PREFERRED;

            startAssertionBuilder.userVerification(uv);
            startAssertionBuilder.timeout(properties.getTimeoutSeconds() * 1000);

            AssertionRequest assertionRequest = relyingParty.startAssertion(startAssertionBuilder.build());
            String requestJson = assertionRequest.toJson();

            String webauthnChallengeId = "stepup_" + stepUpChallengeId;
            long ttl = properties.getChallengeTtlSeconds();

            WebAuthnAuthenticationChallengePayload payload = WebAuthnAuthenticationChallengePayload.of(
                    webauthnChallengeId,
                    userId,
                    sessionIdentifier,
                    WebAuthnCeremonyType.STEP_UP,
                    requestJson,
                    stepUpPayload.action(),
                    stepUpPayload.context(),
                    ttl
            );

            securityStateStore.put(CATEGORY_AUTH_CHALLENGE, webauthnChallengeId, payload, Duration.ofSeconds(ttl));

            return new WebAuthnAuthenticationOptionsResponse(
                    webauthnChallengeId,
                    requestJson,
                    payload.getExpiresAt()
            );

        } catch (Exception ex) {
            log.error("Failed to generate step-up WebAuthn options: {}", ex.getMessage(), ex);
            throw ApiException.internal("WEBAUTHN_STEPUP_FAILED", "Failed to generate WebAuthn step-up options", ex);
        }
    }

    @Override
    @Transactional
    public StepUpProofResponse finishStepUpAssertion(
            UUID userId,
            String sessionIdentifier,
            String stepUpChallengeId,
            String credentialJson
    ) {
        if (userId == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        if (stepUpChallengeId == null || stepUpChallengeId.isBlank()) {
            throw ApiException.badRequest("stepUpChallengeId is required");
        }
        if (credentialJson == null || credentialJson.isBlank()) {
            throw ApiException.badRequest("credentialJson is required");
        }

        validateActiveSession(userId, sessionIdentifier);

        // 1. Consume step-up WebAuthn challenge
        String webauthnChallengeId = "stepup_" + stepUpChallengeId;
        Optional<WebAuthnAuthenticationChallengePayload> authPayloadOpt = securityStateStore.consumeAtomic(
                CATEGORY_AUTH_CHALLENGE,
                webauthnChallengeId,
                WebAuthnAuthenticationChallengePayload.class
        );

        if (authPayloadOpt.isEmpty()) {
            throw ApiException.badRequest("WEBAUTHN_CHALLENGE_INVALID", "Invalid or expired step-up WebAuthn challenge");
        }

        // 2. Consume original step-up challenge
        Optional<StepUpChallengePayload> stepUpOpt = securityStateStore.consumeAtomic(
                CATEGORY_STEP_UP_CHALLENGE,
                stepUpChallengeId,
                StepUpChallengePayload.class
        );

        if (stepUpOpt.isEmpty()) {
            throw ApiException.badRequest("STEP_UP_CHALLENGE_EXPIRED", "Step-up challenge expired or already consumed");
        }

        StepUpChallengePayload stepUpPayload = stepUpOpt.get();
        if (!stepUpPayload.userId().equals(userId)) {
            throw ApiException.forbidden("Step-up challenge does not belong to caller");
        }

        try {
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc =
                    PublicKeyCredential.parseAssertionResponseJson(credentialJson);

            AssertionRequest assertionRequest = AssertionRequest.fromJson(authPayloadOpt.get().getRequestJson());

            FinishAssertionOptions finishOptions = FinishAssertionOptions.builder()
                    .request(assertionRequest)
                    .response(pkc)
                    .build();

            AssertionResult result = relyingParty.finishAssertion(finishOptions);

            if (!result.isSuccess()) {
                throw ApiException.unauthorized("WEBAUTHN_ASSERTION_INVALID", "WebAuthn step-up assertion verification failed");
            }

            String base64UrlCredentialId = result.getCredential().getCredentialId().getBase64Url();
            UserWebAuthnCredential credential = credentialRepository
                    .findByCredentialIdAndRevokedAtIsNull(base64UrlCredentialId)
                    .orElseThrow(() -> ApiException.unauthorized("WEBAUTHN_CREDENTIAL_REVOKED", "Credential not found or revoked"));

            long storedSignCount = credential.getSignCount();
            long newSignCount = result.getSignatureCount();

            if (storedSignCount > 0 && newSignCount <= storedSignCount) {
                log.warn("CLONE DETECTED during step-up for credential [{}]", credential.getId());
                auditService.recordAudit(
                        null,
                        null,
                        userId,
                        "USER",
                        AuditAction.WEBAUTHN_CLONE_DETECTED,
                        "WEBAUTHN_CREDENTIAL",
                        credential.getId(),
                        stepUpChallengeId,
                        null,
                        "COUNTER_ROLLBACK"
                );
                if ("BLOCK".equalsIgnoreCase(properties.getCloneDetectionAction())) {
                    throw ApiException.unauthorized("WEBAUTHN_CLONE_DETECTED", "Authenticator clone anomaly detected");
                }
            }

            HttpServletRequest currentRequest = getCurrentHttpRequest();
            String clientIp = UserAgentParser.extractClientIp(currentRequest);
            credential.recordUsage(newSignCount, clientIp);
            credentialRepository.save(credential);

            // 3. Issue Step-Up Proof Token
            byte[] proofBytes = new byte[32];
            secureRandom.nextBytes(proofBytes);
            String proofToken = HexFormat.of().formatHex(proofBytes);

            long proofTtl = 300L;
            StepUpProofPayload proofPayload = StepUpProofPayload.of(
                    proofToken,
                    userId,
                    sessionIdentifier,
                    stepUpPayload.action(),
                    stepUpPayload.context(),
                    StepUpFactor.WEBAUTHN,
                    proofTtl
            );

            securityStateStore.put(CATEGORY_STEP_UP_PROOF, proofToken, proofPayload, Duration.ofSeconds(proofTtl));

            auditService.recordAudit(
                    null,
                    stepUpPayload.context() != null ? stepUpPayload.context().workspaceId() : null,
                    userId,
                    "USER",
                    AuditAction.STEP_UP_VERIFICATION_SUCCESS,
                    "STEP_UP_PROOF",
                    userId,
                    proofToken.substring(0, 8) + "...",
                    clientIp,
                    "SUCCESS"
            );

            log.info("Issued step-up proof via WebAuthn for user [{}] action [{}]", userId, stepUpPayload.action());

            return new StepUpProofResponse(proofToken, stepUpPayload.action(), StepUpFactor.WEBAUTHN, proofPayload.expiresAt());

        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("WebAuthn step-up verification failed: {}", ex.getMessage(), ex);
            throw ApiException.unauthorized("WEBAUTHN_STEPUP_FAILED", "WebAuthn step-up verification failed: " + ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebAuthnCredentialResponse> listCredentials(UUID userId) {
        if (userId == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        List<UserWebAuthnCredential> credentials = credentialRepository.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId);
        return credentials.stream()
                .map(WebAuthnCredentialResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public WebAuthnCredentialResponse renameCredential(UUID userId, UUID credentialId, String newFriendlyName) {
        if (userId == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        if (credentialId == null) {
            throw ApiException.badRequest("credentialId is required");
        }
        if (newFriendlyName == null || newFriendlyName.isBlank()) {
            throw ApiException.badRequest("friendlyName cannot be blank");
        }

        UserWebAuthnCredential credential = credentialRepository.findByIdAndUserId(credentialId, userId)
                .orElseThrow(() -> ApiException.notFound("Credential not found or does not belong to user"));

        if (credential.isRevoked()) {
            throw ApiException.badRequest("Cannot rename a revoked credential");
        }

        String trimmed = newFriendlyName.trim();
        credential.setFriendlyName(trimmed);
        UserWebAuthnCredential saved = credentialRepository.save(credential);

        auditService.recordAudit(
                null,
                null,
                userId,
                "USER",
                AuditAction.WEBAUTHN_CREDENTIAL_RENAMED,
                "WEBAUTHN_CREDENTIAL",
                credential.getId(),
                null,
                null,
                "SUCCESS"
        );

        return WebAuthnCredentialResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void revokeCredential(UUID userId, UUID credentialId) {
        if (userId == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        if (credentialId == null) {
            throw ApiException.badRequest("credentialId is required");
        }

        UserWebAuthnCredential credential = credentialRepository.findByIdAndUserId(credentialId, userId)
                .orElseThrow(() -> ApiException.notFound("Credential not found or does not belong to user"));

        if (credential.isRevoked()) {
            return; // Idempotent
        }

        // Lockout Prevention Evaluation:
        // Ensure user retains at least one authentication path
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        long remainingActiveCredentials = credentialRepository.countByUserIdAndRevokedAtIsNull(userId) - 1;
        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        boolean hasMfa = mfaService.isMfaEnabled(userId);

        if (remainingActiveCredentials <= 0 && !hasPassword && !hasMfa) {
            throw ApiException.badRequest(
                    "LOCKOUT_PREVENTION",
                    "Cannot remove the only remaining authentication credential for this account."
            );
        }

        credential.revoke("User revoked credential via account security settings");
        credentialRepository.save(credential);

        auditService.recordAudit(
                null,
                null,
                userId,
                "USER",
                AuditAction.WEBAUTHN_CREDENTIAL_REVOKED,
                "WEBAUTHN_CREDENTIAL",
                credential.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.info("Revoked WebAuthn credential [{}] for user [{}]", credentialId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasWebAuthnCredentials(UUID userId) {
        if (userId == null) return false;
        return credentialRepository.countByUserIdAndRevokedAtIsNull(userId) > 0;
    }

    private void validateActiveSession(UUID userId, String sessionIdentifier) {
        if (sessionIdentifier != null && !sessionIdentifier.isBlank()) {
            Optional<UserSession> sessionOpt = sessionRepository.findBySessionIdentifier(sessionIdentifier);
            if (sessionOpt.isEmpty() || !sessionOpt.get().isActive()) {
                throw ApiException.unauthorized("Session has been revoked or expired");
            }
            if (!sessionOpt.get().getUserId().equals(userId)) {
                throw ApiException.forbidden("Session does not belong to the authenticated user");
            }
        }
    }

    private String extractTransports(String credentialJson) {
        try {
            JsonNode node = objectMapper.readTree(credentialJson);
            JsonNode responseNode = node.get("response");
            if (responseNode != null && responseNode.has("transports") && responseNode.get("transports").isArray()) {
                List<String> transportsList = new ArrayList<>();
                for (JsonNode t : responseNode.get("transports")) {
                    transportsList.add(t.asText());
                }
                return String.join(",", transportsList);
            }
        } catch (Exception ex) {
            log.debug("Could not parse transports from credential JSON: {}", ex.getMessage());
        }
        return null;
    }

    private WorkspaceMembership resolvePrimaryMembership(UUID userId) {
        List<WorkspaceMembership> memberships = membershipRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            throw ApiException.notFound("No active workspace found for user");
        }

        return memberships.stream()
                .filter(m -> {
                    Workspace w = workspaceRepository.findById(m.getWorkspaceId()).orElse(null);
                    return w != null && w.isDefault();
                })
                .findFirst()
                .orElse(memberships.getFirst());
    }

    private AuthResponse generateAuthResponse(
            User user,
            Workspace workspace,
            WorkspaceRole role,
            AuthMethod authMethod,
            UUID existingSessionId
    ) {
        HttpServletRequest currentRequest = getCurrentHttpRequest();
        String clientIp = UserAgentParser.extractClientIp(currentRequest);
        String userAgent = currentRequest != null ? currentRequest.getHeader("User-Agent") : null;

        Instant refreshExpiry = Instant.now().plusSeconds(REFRESH_TOKEN_EXPIRATION_DAYS * 86400L);

        UserSession session;
        if (existingSessionId != null) {
            session = sessionService.recordSessionActivity(existingSessionId, clientIp, userAgent);
            if (session == null || !session.isActive()) {
                throw ApiException.unauthorized("Session has been revoked or expired");
            }
        } else {
            session = sessionService.createSession(
                    user.getId(),
                    authMethod != null ? authMethod : AuthMethod.WEBAUTHN_PASSKEY,
                    clientIp,
                    userAgent,
                    refreshExpiry
            );
        }

        String rawRefreshToken = tokenProvider.generateRefreshToken();
        String hashedRefreshToken = tokenProvider.hashToken(rawRefreshToken);

        RefreshToken refreshTokenEntity = new RefreshToken(
                user.getId(),
                session.getId(),
                hashedRefreshToken,
                refreshExpiry
        );
        refreshTokenRepository.save(refreshTokenEntity);

        String accessToken = tokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                session.getSessionIdentifier()
        );

        UserResponse userResponse = UserResponse.fromEntity(user);
        WorkspaceResponse workspaceResponse = (workspace != null)
                ? WorkspaceResponse.fromEntity(workspace, role)
                : null;

        return AuthResponse.of(
                accessToken,
                rawRefreshToken,
                tokenProvider.getExpirationSeconds(),
                userResponse,
                workspaceResponse
        );
    }

    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
