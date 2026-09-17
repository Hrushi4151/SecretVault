package com.secretvault.auth.service;

import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.dto.LoginRequest;
import com.secretvault.auth.dto.RefreshTokenRequest;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.auth.dto.UserResponse;
import com.secretvault.auth.entity.RefreshToken;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.repository.RefreshTokenRepository;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.security.JwtTokenProvider;
import com.secretvault.common.exception.ApiException;
import com.secretvault.organization.entity.Organization;
import com.secretvault.organization.repository.OrganizationRepository;
import com.secretvault.workspace.dto.WorkspaceResponse;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service managing user lifecycle, authentication, credential validation,
 * token generation, and multi-tenant organization/workspace bootstrap.
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

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            OrganizationRepository organizationRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.organizationRepository = organizationRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Registers a new user account, provisions their primary organization and default workspace,
     * assigns OWNER role, and issues initial access + refresh tokens.
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

        // 5. Generate Access and Refresh Tokens
        return generateAuthResponse(user, defaultWorkspace, WorkspaceRole.OWNER);
    }

    /**
     * Authenticates existing user credentials, validates account status,
     * and issues a new session with JWT and refresh token.
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

        // Find primary/default workspace for user
        List<WorkspaceMembership> memberships = membershipRepository.findByUserId(user.getId());
        if (memberships.isEmpty()) {
            throw ApiException.notFound("No active workspace found for user");
        }

        // Prefer default workspace or first membership
        WorkspaceMembership primaryMembership = memberships.stream()
                .filter(m -> {
                    Workspace w = workspaceRepository.findById(m.getWorkspaceId()).orElse(null);
                    return w != null && w.isDefault();
                })
                .findFirst()
                .orElse(memberships.getFirst());

        Workspace workspace = workspaceRepository.findById(primaryMembership.getWorkspaceId())
                .orElseThrow(() -> ApiException.notFound("Assigned workspace could not be found"));

        log.info("User [{}] authenticated successfully. Active workspace: [{}]", user.getId(), workspace.getId());

        return generateAuthResponse(user, workspace, primaryMembership.getRole());
    }

    /**
     * Rotates refresh token and issues a fresh JWT access token.
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String hashedToken = tokenProvider.hashToken(request.refreshToken().trim());

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(hashedToken)
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired refresh token"));

        if (refreshToken.isExpired()) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw ApiException.unauthorized("Refresh token has expired");
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("User associated with token not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("User account is inactive");
        }

        // Revoke the used refresh token (strict token rotation)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        // Resolve workspace
        List<WorkspaceMembership> memberships = membershipRepository.findByUserId(user.getId());
        WorkspaceMembership primaryMembership = memberships.isEmpty() ? null : memberships.getFirst();
        Workspace workspace = (primaryMembership != null)
                ? workspaceRepository.findById(primaryMembership.getWorkspaceId()).orElse(null)
                : null;
        WorkspaceRole role = primaryMembership != null ? primaryMembership.getRole() : WorkspaceRole.VIEWER;

        return generateAuthResponse(user, workspace, role);
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
     * Revokes active refresh tokens for the user upon sign out.
     */
    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Revoked all active refresh tokens for user [{}]", userId);
    }

    private AuthResponse generateAuthResponse(User user, Workspace workspace, WorkspaceRole role) {
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getFullName());
        String rawRefreshToken = tokenProvider.generateRefreshToken();
        String hashedRefreshToken = tokenProvider.hashToken(rawRefreshToken);

        Instant refreshExpiry = Instant.now().plusSeconds(REFRESH_TOKEN_EXPIRATION_DAYS * 86400L);
        RefreshToken refreshTokenEntity = new RefreshToken(user.getId(), hashedRefreshToken, refreshExpiry);
        refreshTokenRepository.save(refreshTokenEntity);

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
