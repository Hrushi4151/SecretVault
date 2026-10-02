package com.secretvault.auth.service;

import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.dto.LoginRequest;
import com.secretvault.auth.dto.RefreshTokenRequest;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.auth.dto.UserResponse;
import com.secretvault.auth.entity.RefreshToken;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.mfa.dto.MfaRecoveryVerifyRequest;
import com.secretvault.auth.mfa.dto.MfaTotpVerifyRequest;
import com.secretvault.auth.mfa.model.MfaChallengeInfo;
import com.secretvault.auth.mfa.model.MfaVerificationResult;
import com.secretvault.auth.mfa.service.MfaService;
import com.secretvault.auth.repository.RefreshTokenRepository;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.security.JwtTokenProvider;
import com.secretvault.auth.session.entity.UserSession;
import com.secretvault.auth.session.enums.AuthMethod;
import com.secretvault.auth.session.service.SessionService;
import com.secretvault.auth.session.util.UserAgentParser;
import com.secretvault.common.exception.ApiException;
import com.secretvault.organization.entity.Organization;
import com.secretvault.organization.repository.OrganizationRepository;
import com.secretvault.workspace.dto.WorkspaceResponse;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing user lifecycle, authentication, credential validation,
 * token generation, session lifecycle binding, multi-tenant organization/workspace bootstrap,
 * and MFA login flow.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final long REFRESH_TOKEN_EXPIRATION_DAYS = 30;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OrganizationRepository organizationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final MfaService mfaService;
    private final SessionService sessionService;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            OrganizationRepository organizationRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            MfaService mfaService,
            SessionService sessionService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.organizationRepository = organizationRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.mfaService = mfaService;
        this.sessionService = sessionService;
    }

    /**
     * Registers a new user account, provisions their primary organization and default workspace,
     * assigns OWNER role, establishes an authenticated session, and issues initial access + refresh tokens.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw ApiException.conflict("Email address is already registered");
        }

        // 1. Create and persist User
        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(normalizedEmail, passwordHash, request.fullName().trim());
        user = userRepository.save(user);

        // 2. Create Organization
        String orgName = StringUtils.hasText(request.organizationName())
                ? request.organizationName().trim()
                : request.fullName().trim() + "'s Organization";
        String baseOrgSlug = generateSlug(orgName);
        String orgSlug = ensureUniqueOrgSlug(baseOrgSlug);

        Organization organization = new Organization(orgName, orgSlug, "FREE");
        organization = organizationRepository.save(organization);

        // 3. Create Default Workspace
        Workspace defaultWorkspace = new Workspace(organization.getId(), "Default Workspace", "default", true);
        defaultWorkspace = workspaceRepository.save(defaultWorkspace);

        // 4. Assign OWNER Role in Default Workspace
        WorkspaceMembership membership = new WorkspaceMembership(defaultWorkspace.getId(), user.getId(), WorkspaceRole.OWNER);
        membershipRepository.save(membership);

        log.info("Registered new user [{}] with organization [{}] and default workspace [{}]",
                user.getId(), organization.getId(), defaultWorkspace.getId());

        // 5. Generate Session, Access and Refresh Tokens
        return generateAuthResponse(user, defaultWorkspace, WorkspaceRole.OWNER, AuthMethod.PASSWORD, null);
    }

    /**
     * Authenticates existing user credentials, validates account status,
     * and either issues tokens and establishes a session (if MFA disabled) or creates and returns an MFA challenge.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("User account is " + user.getStatus().name().toLowerCase(Locale.ROOT));
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }

        // Check if MFA is active on user account
        if (mfaService.isMfaEnabled(user.getId())) {
            MfaChallengeInfo challenge = mfaService.createLoginChallenge(user.getId());
            log.info("User [{}] authenticated with primary credentials; MFA challenge [{}] issued.",
                    user.getId(), challenge.challengeId());
            return AuthResponse.mfaRequired(challenge.challengeId(), challenge.expiresAt());
        }

        // MFA is disabled - complete single-factor authentication and establish session
        WorkspaceMembership primaryMembership = resolvePrimaryMembership(user.getId());
        Workspace workspace = workspaceRepository.findById(primaryMembership.getWorkspaceId())
                .orElseThrow(() -> ApiException.notFound("Assigned workspace could not be found"));

        log.info("User [{}] authenticated successfully without MFA. Active workspace: [{}]", user.getId(), workspace.getId());

        return generateAuthResponse(user, workspace, primaryMembership.getRole(), AuthMethod.PASSWORD, null);
    }

    /**
     * Completes authentication by verifying a TOTP code against an active MFA login challenge,
     * establishing an authenticated session with MFA TOTP metadata.
     */
    @Transactional
    public AuthResponse completeMfaTotpLogin(MfaTotpVerifyRequest request) {
        MfaVerificationResult result = mfaService.verifyLoginTotp(request.challengeId(), request.code());
        if (!result.success()) {
            String errorMsg = result.errorMessage() != null ? result.errorMessage() : "MFA TOTP verification failed";
            throw ApiException.unauthorized(errorMsg);
        }

        User user = userRepository.findById(result.userId())
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("User account is " + user.getStatus().name().toLowerCase(Locale.ROOT));
        }

        WorkspaceMembership primaryMembership = resolvePrimaryMembership(user.getId());
        Workspace workspace = workspaceRepository.findById(primaryMembership.getWorkspaceId())
                .orElseThrow(() -> ApiException.notFound("Assigned workspace could not be found"));

        log.info("User [{}] successfully verified TOTP challenge [{}]. Session tokens issued.",
                user.getId(), request.challengeId());

        return generateAuthResponse(user, workspace, primaryMembership.getRole(), AuthMethod.PASSWORD_MFA_TOTP, null);
    }

    /**
     * Completes authentication by verifying a backup recovery code against an active MFA login challenge,
     * establishing an authenticated session with MFA Recovery metadata.
     */
    @Transactional
    public AuthResponse completeMfaRecoveryLogin(MfaRecoveryVerifyRequest request) {
        MfaVerificationResult result = mfaService.verifyLoginRecoveryCode(request.challengeId(), request.recoveryCode());
        if (!result.success()) {
            String errorMsg = result.errorMessage() != null ? result.errorMessage() : "MFA recovery verification failed";
            throw ApiException.unauthorized(errorMsg);
        }

        User user = userRepository.findById(result.userId())
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("User account is " + user.getStatus().name().toLowerCase(Locale.ROOT));
        }

        WorkspaceMembership primaryMembership = resolvePrimaryMembership(user.getId());
        Workspace workspace = workspaceRepository.findById(primaryMembership.getWorkspaceId())
                .orElseThrow(() -> ApiException.notFound("Assigned workspace could not be found"));

        log.info("User [{}] successfully verified recovery code challenge [{}]. Session tokens issued.",
                user.getId(), request.challengeId());

        return generateAuthResponse(user, workspace, primaryMembership.getRole(), AuthMethod.PASSWORD_MFA_RECOVERY, null);
    }

    /**
     * Rotates refresh token, validates bound session status and user account state,
     * updates session activity, and issues a fresh JWT access token.
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String hashedToken = tokenProvider.hashToken(request.refreshToken().trim());

        // 1. Atomic Revocation: Guarantee only one concurrent caller can consume this active token
        int consumed = refreshTokenRepository.markRevokedIfActive(hashedToken);
        if (consumed != 1) {
            // Either does not exist, or was already revoked (replay attack / concurrent use)
            Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(hashedToken);
            if (tokenOpt.isPresent() && tokenOpt.get().isRevoked()) {
                log.warn("Replay attack detected: Attempt to reuse revoked refresh token for user [{}]", tokenOpt.get().getUserId());
                if (tokenOpt.get().getSessionId() != null) {
                    sessionService.findById(tokenOpt.get().getSessionId()).ifPresent(s -> {
                        if (!s.isRevoked()) {
                            sessionService.revokeSession(s.getUserId(), s.getSessionIdentifier());
                        }
                    });
                }
            }
            throw ApiException.unauthorized("Invalid or expired refresh token");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired refresh token"));

        if (refreshToken.isExpired()) {
            throw ApiException.unauthorized("Refresh token has expired");
        }

        // Validate session validity if token is bound to a session
        if (refreshToken.getSessionId() != null) {
            Optional<UserSession> sessionOpt = sessionService.findById(refreshToken.getSessionId());
            if (sessionOpt.isEmpty() || !sessionOpt.get().isActive()) {
                throw ApiException.unauthorized("Session has been revoked or expired");
            }
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("User associated with token not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("User account is " + user.getStatus().name().toLowerCase(Locale.ROOT));
        }

        // Resolve workspace
        List<WorkspaceMembership> memberships = membershipRepository.findByUserId(user.getId());
        WorkspaceMembership primaryMembership = memberships.isEmpty() ? null : memberships.getFirst();
        Workspace workspace = (primaryMembership != null)
                ? workspaceRepository.findById(primaryMembership.getWorkspaceId()).orElse(null)
                : null;
        WorkspaceRole role = primaryMembership != null ? primaryMembership.getRole() : WorkspaceRole.VIEWER;

        return generateAuthResponse(user, workspace, role, null, refreshToken.getSessionId());
    }

    /**
     * Returns current user profile representation.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return UserResponse.fromEntity(user);
    }

    /**
     * Revokes a specific session or all active sessions and refresh tokens for the user upon sign out.
     */
    @Transactional
    public void logout(UUID userId, String sessionIdentifier) {
        if (sessionIdentifier != null && !sessionIdentifier.isBlank()) {
            sessionService.revokeSession(userId, sessionIdentifier);
            log.info("Revoked active session [{}] for user [{}]", sessionIdentifier, userId);
        } else {
            sessionService.revokeAllSessions(userId);
            log.info("Revoked all active sessions and refresh tokens for user [{}]", userId);
        }
    }

    /**
     * Legacy/default logout revoking all active sessions for the user.
     */
    @Transactional
    public void logout(UUID userId) {
        logout(userId, null);
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
            UUID existingSessionId) {

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
                    authMethod != null ? authMethod : AuthMethod.PASSWORD,
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

    private String generateSlug(String input) {
        String slug = input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return StringUtils.hasText(slug) ? slug : "org-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String ensureUniqueOrgSlug(String baseSlug) {
        String candidate = baseSlug;
        int counter = 1;
        while (organizationRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + counter++;
        }
        return candidate;
    }
}
