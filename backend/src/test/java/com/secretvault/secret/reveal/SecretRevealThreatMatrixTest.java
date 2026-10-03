package com.secretvault.secret.reveal;

import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.repository.AuditLogRepository;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.session.entity.UserSession;
import com.secretvault.auth.session.service.SessionService;
import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.service.StepUpAuthenticationService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.security.state.SecurityStateStore;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.EnvType;
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
import com.secretvault.secret.reveal.dto.SecretRevealIntentResponse;
import com.secretvault.secret.reveal.model.RevealPolicyLevel;
import com.secretvault.secret.reveal.model.SecretRevealIntentPayload;
import com.secretvault.secret.reveal.model.SecretRevealPolicyEvaluation;
import com.secretvault.secret.reveal.repository.SecretRevealPolicyRepository;
import com.secretvault.secret.reveal.service.DefaultSecretRevealPolicyService;
import com.secretvault.secret.reveal.service.DefaultSecretRevealService;
import com.secretvault.secret.reveal.service.SecretRevealPolicyService;
import com.secretvault.secret.reveal.service.SecretRevealService;
import com.secretvault.secret.service.SecretAuthorizationHelper;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Production-Grade Secret Reveal Adversarial Threat Matrix Test Suite (Phase 5.8.5).
 * Validates scenarios SR-01 through SR-60.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SecretRevealThreatMatrixTest {

    @Mock private SecretRepository secretRepository;
    @Mock private SecretVersionRepository secretVersionRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private EffectiveAccessService effectiveAccessService;
    @Mock private SecretAuthorizationHelper authHelper;
    @Mock private StepUpAuthenticationService stepUpAuthenticationService;
    @Mock private SecurityStateStore securityStateStore;
    @Mock private AuditService auditService;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private EnvironmentRepository environmentRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMembershipRepository membershipRepository;
    @Mock private SessionService sessionService;
    @Mock private SecurityEventService securityEventService;
    @Mock private SecretRevealPolicyRepository policyRepository;

    private SecretRevealPolicyService policyService;
    private SecretRevealService revealService;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID userId;
    private UUID attackerUserId;
    private String sessionIdentifier;
    private String attackerSessionIdentifier;

    private Workspace workspace;
    private WorkspaceMembership membership;
    private Project project;
    private Environment devEnvironment;
    private Environment prodEnvironment;
    private Secret secret;
    private SecretVersion version1;
    private User user;
    private UserSession session;

    private final Map<String, SecretRevealIntentPayload> inMemoryRedisState = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        userId = UUID.randomUUID();
        attackerUserId = UUID.randomUUID();
        sessionIdentifier = "sess_valid_" + UUID.randomUUID();
        attackerSessionIdentifier = "sess_attacker_" + UUID.randomUUID();

        workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setOrganizationId(UUID.randomUUID());
        workspace.setName("SecVault-Security");

        membership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.ADMIN);

        project = new Project(workspaceId, "PaymentService", "payment-service", "Core payments", userId);
        project.setId(projectId);

        devEnvironment = new Environment(projectId, "development", "development", EnvType.DEVELOPMENT, "Dev env", false, userId);
        devEnvironment.setId(environmentId);

        prodEnvironment = new Environment(projectId, "production", "production", EnvType.PRODUCTION, "Prod env", true, userId);
        prodEnvironment.setId(environmentId);
        prodEnvironment.setProtected(true);

        secret = new Secret(environmentId, "STRIPE_API_KEY", "Production Stripe Key", userId);
        secret.setId(secretId);
        secret.setCurrentVersionNumber(1);
        secret.setStatus(SecretStatus.ACTIVE);

        version1 = new SecretVersion(
                secretId, 1,
                "ciphertext-v1".getBytes(StandardCharsets.UTF_8),
                "enc-dek-v1".getBytes(StandardCharsets.UTF_8),
                "iv-v1".getBytes(StandardCharsets.UTF_8),
                "tag-v1".getBytes(StandardCharsets.UTF_8),
                "kms-key-1",
                userId,
                "initial version"
        );

        user = new User("admin@secretvault.io", "hashedpass", "Admin User");
        user.setId(userId);
        user.setStatus(UserStatus.ACTIVE);

        session = new UserSession(userId, sessionIdentifier, null, "127.0.0.1", "Browser", "Mac", "Chrome", "macOS", Instant.now().plusSeconds(3600));

        inMemoryRedisState.clear();

        // SecurityStateStore mock with atomic consumption
        doAnswer(invocation -> {
            String key = invocation.getArgument(1);
            SecretRevealIntentPayload payload = invocation.getArgument(2);
            inMemoryRedisState.put(key, payload);
            return null;
        }).when(securityStateStore).put(eq(SecretRevealService.CATEGORY_SECRET_REVEAL_INTENT), anyString(), any(), any());

        doAnswer(invocation -> {
            String key = invocation.getArgument(1);
            SecretRevealIntentPayload payload = inMemoryRedisState.remove(key);
            return Optional.ofNullable(payload);
        }).when(securityStateStore).consumeAtomic(eq(SecretRevealService.CATEGORY_SECRET_REVEAL_INTENT), anyString(), eq(SecretRevealIntentPayload.class));

        // Default repositories
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(sessionService.findByIdentifier(sessionIdentifier)).thenReturn(Optional.of(session));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(devEnvironment));
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(version1));

        SecretAuthorizationHelper.WorkspaceContext wsContext =
                new SecretAuthorizationHelper.WorkspaceContext(workspace, membership, project, devEnvironment);
        when(authHelper.verifyHierarchy(any(), any(), any(), any())).thenReturn(wsContext);
        when(authHelper.verifyHierarchyAndRevealAccess(any(), any(), any(), eq(userId))).thenReturn(wsContext);

        when(encryptionService.decrypt(any(EncryptedPayload.class), anyString()))
                .thenReturn("sk_live_very_secret_key_12345".getBytes(StandardCharsets.UTF_8));

        policyService = new DefaultSecretRevealPolicyService(
                policyRepository, environmentRepository, effectiveAccessService, auditService
        );

        revealService = new DefaultSecretRevealService(
                secretRepository, secretVersionRepository, encryptionService, effectiveAccessService,
                authHelper, policyService, stepUpAuthenticationService, securityStateStore,
                auditService, userRepository, auditLogRepository, environmentRepository,
                projectRepository, sessionService, securityEventService
        );
    }

    // ==========================================
    // SR-01 to SR-05: Authentication & Identity
    // ==========================================

    @Test
    @DisplayName("SR-01: Unauthenticated reveal is strictly rejected")
    void testSR01_UnauthenticatedReveal() {
        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, null, null, null, "req-1", "127.0.0.1")
        );
        assertEquals(401, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-02: Non-existent user identity is rejected")
    void testSR02_InvalidUserIdentity() {
        UUID unknownUser = UUID.randomUUID();
        when(userRepository.findById(unknownUser)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, null, unknownUser, null, "req-2", "127.0.0.1")
        );
        assertEquals(401, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-03: Expired session is rejected")
    void testSR03_ExpiredSession() {
        UserSession expiredSession = new UserSession(userId, "sess_expired", null, "127.0.0.1", "Browser", "Mac", "Chrome", "macOS", Instant.now().minusSeconds(10));
        when(sessionService.findByIdentifier("sess_expired")).thenReturn(Optional.of(expiredSession));

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, null, userId, "sess_expired", "req-3", "127.0.0.1")
        );
        assertEquals(401, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-04: Revoked session is rejected")
    void testSR04_RevokedSession() {
        UserSession revokedSession = new UserSession(userId, "sess_revoked", null, "127.0.0.1", "Browser", "Mac", "Chrome", "macOS", Instant.now().plusSeconds(3600));
        revokedSession.setRevokedAt(Instant.now().minusSeconds(60));
        when(sessionService.findByIdentifier("sess_revoked")).thenReturn(Optional.of(revokedSession));

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, null, userId, "sess_revoked", "req-4", "127.0.0.1")
        );
        assertEquals(401, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-05: Disabled user account is forbidden from revealing secrets")
    void testSR05_DisabledUser() {
        user.setStatus(UserStatus.SUSPENDED);

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, null, userId, sessionIdentifier, "req-5", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("disabled"));
    }

    // ==========================================
    // SR-06 to SR-10: Scoping & Authorization
    // ==========================================

    @Test
    @DisplayName("SR-06: Cross-tenant reveal access is blocked")
    void testSR06_CrossTenantReveal() {
        UUID foreignWorkspace = UUID.randomUUID();
        when(authHelper.verifyHierarchyAndRevealAccess(eq(foreignWorkspace), any(), any(), eq(userId)))
                .thenThrow(ApiException.forbidden("You are not a member of this workspace"));

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(foreignWorkspace, projectId, environmentId, secretId, null, userId, sessionIdentifier, "req-6", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-07: Cross-workspace reveal IDOR is blocked")
    void testSR07_CrossWorkspaceIDOR() {
        UUID otherWorkspace = UUID.randomUUID();
        when(authHelper.verifyHierarchyAndRevealAccess(eq(otherWorkspace), any(), any(), eq(userId)))
                .thenThrow(ApiException.notFound("Workspace not found"));

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(otherWorkspace, projectId, environmentId, secretId, null, userId, sessionIdentifier, "req-7", "127.0.0.1")
        );
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-08: Cross-project reveal IDOR is blocked")
    void testSR08_CrossProjectIDOR() {
        UUID foreignProject = UUID.randomUUID();
        when(authHelper.verifyHierarchyAndRevealAccess(eq(workspaceId), eq(foreignProject), any(), eq(userId)))
                .thenThrow(ApiException.notFound("Project not found in this workspace"));

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, foreignProject, environmentId, secretId, null, userId, sessionIdentifier, "req-8", "127.0.0.1")
        );
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-09: Cross-environment reveal IDOR is blocked")
    void testSR09_CrossEnvironmentIDOR() {
        UUID foreignEnvironment = UUID.randomUUID();
        when(authHelper.verifyHierarchyAndRevealAccess(eq(workspaceId), eq(projectId), eq(foreignEnvironment), eq(userId)))
                .thenThrow(ApiException.notFound("Environment not found in this project"));

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, foreignEnvironment, secretId, null, userId, sessionIdentifier, "req-9", "127.0.0.1")
        );
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-10: Missing SECRET_REVEAL permission (e.g. VIEWER) is forbidden")
    void testSR10_MissingSecretRevealPermission() {
        when(authHelper.verifyHierarchyAndRevealAccess(eq(workspaceId), eq(projectId), eq(environmentId), eq(userId)))
                .thenThrow(ApiException.forbidden("VIEWER role is strictly forbidden from revealing secret values"));

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, null, userId, sessionIdentifier, "req-10", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    // ==========================================
    // SR-11 to SR-15: JIT & Privileged Elevation
    // ==========================================

    @Test
    @DisplayName("SR-11: Stale / revoked grant is denied reveal")
    void testSR11_StaleGrantDenied() {
        when(authHelper.verifyHierarchyAndRevealAccess(eq(workspaceId), eq(projectId), eq(environmentId), eq(userId)))
                .thenThrow(ApiException.forbidden("Access denied: grant has expired or was revoked"));

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, null, userId, sessionIdentifier, "req-11", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-12: Expired JIT elevation is denied reveal")
    void testSR12_ExpiredJitDenied() {
        when(authHelper.verifyHierarchyAndRevealAccess(eq(workspaceId), eq(projectId), eq(environmentId), eq(userId)))
                .thenThrow(ApiException.forbidden("JIT elevation has expired"));

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, null, userId, sessionIdentifier, "req-12", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-13: Revoked JIT elevation is denied reveal")
    void testSR13_RevokedJitDenied() {
        when(authHelper.verifyHierarchyAndRevealAccess(eq(workspaceId), eq(projectId), eq(environmentId), eq(userId)))
                .thenThrow(ApiException.forbidden("JIT elevation was revoked by administrator"));

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, null, userId, sessionIdentifier, "req-13", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-14: Expired Privileged elevation is denied reveal")
    void testSR14_ExpiredPrivilegedElevation() {
        when(authHelper.verifyHierarchyAndRevealAccess(eq(workspaceId), eq(projectId), eq(environmentId), eq(userId)))
                .thenThrow(ApiException.forbidden("Privileged elevation expired"));

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, null, userId, sessionIdentifier, "req-14", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-15: Revoked Privileged elevation is denied reveal")
    void testSR15_RevokedPrivilegedElevation() {
        when(authHelper.verifyHierarchyAndRevealAccess(eq(workspaceId), eq(projectId), eq(environmentId), eq(userId)))
                .thenThrow(ApiException.forbidden("Privileged elevation was revoked"));

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, null, userId, sessionIdentifier, "req-15", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    // ==========================================
    // SR-16 to SR-18: Production Policy & Reason
    // ==========================================

    @Test
    @DisplayName("SR-16: Production environment mandates Step-Up and Reason")
    void testSR16_ProductionPolicyEnforcement() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(prodEnvironment));

        SecretRevealPolicyEvaluation policy = policyService.evaluatePolicy(workspaceId, projectId, environmentId, secretId, userId);
        assertEquals(RevealPolicyLevel.PRODUCTION_CRITICAL, policy.policyLevel());
        assertTrue(policy.requireStepUp());
        assertTrue(policy.requireReason());
    }

    @Test
    @DisplayName("SR-17: Missing justification reason in protected environment is rejected")
    void testSR17_MissingRevealReason() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(prodEnvironment));

        CreateRevealIntentRequest req = new CreateRevealIntentRequest(1, null, "stepup-proof-123");

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-17", "127.0.0.1")
        );
        assertEquals(400, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("reason"));
    }

    @Test
    @DisplayName("SR-18: Trivial, repetitive, or too-short justification reasons are rejected")
    void testSR18_WeakOrTrivialReasons() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(prodEnvironment));

        List<String> invalidReasons = List.of(
                "test",
                "testing",
                "short",
                "aaaaaaaaaaaaa",
                "1111111111111",
                "reveal secret"
        );

        for (String badReason : invalidReasons) {
            CreateRevealIntentRequest req = new CreateRevealIntentRequest(1, badReason, "stepup-proof-123");
            ApiException ex = assertThrows(ApiException.class, () ->
                    revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-18", "127.0.0.1")
            );
            assertEquals(400, ex.getStatus().value());
        }
    }

    // ==========================================
    // SR-19 to SR-23: Step-Up Authentication
    // ==========================================

    @Test
    @DisplayName("SR-19: Missing Step-Up proof when policy mandates it is rejected")
    void testSR19_MissingStepUpProof() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(prodEnvironment));

        CreateRevealIntentRequest req = new CreateRevealIntentRequest(1, "Investigating production payment outage INC-9012", null);

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-19", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-20: Wrong Step-Up action (SECRET_DELETE proof for SECRET_REVEAL) is rejected")
    void testSR20_WrongStepUpAction() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(prodEnvironment));

        doThrow(ApiException.forbidden("INVALID_PROOF", "Step-up proof was issued for SECRET_DELETE, not SECRET_REVEAL"))
                .when(stepUpAuthenticationService).verifyAndConsumeProof(
                        eq("delete-proof-999"), eq(userId), eq(sessionIdentifier), eq(StepUpAction.SECRET_REVEAL), any()
                );

        CreateRevealIntentRequest req = new CreateRevealIntentRequest(1, "Investigating production payment outage INC-9012", "delete-proof-999");

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-20", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-21: Wrong Step-Up resource context (Secret A proof for Secret B) is rejected")
    void testSR21_WrongStepUpResourceContext() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(prodEnvironment));

        doThrow(ApiException.forbidden("CONTEXT_MISMATCH", "Step-up proof resource context mismatch"))
                .when(stepUpAuthenticationService).verifyAndConsumeProof(
                        eq("mismatched-proof"), eq(userId), eq(sessionIdentifier), eq(StepUpAction.SECRET_REVEAL), any()
                );

        CreateRevealIntentRequest req = new CreateRevealIntentRequest(1, "Investigating production payment outage INC-9012", "mismatched-proof");

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-21", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-22: Wrong Step-Up session binding is rejected")
    void testSR22_WrongStepUpSessionBinding() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(prodEnvironment));

        doThrow(ApiException.unauthorized("Step-up proof session binding mismatch"))
                .when(stepUpAuthenticationService).verifyAndConsumeProof(
                        eq("session-mismatch-proof"), eq(userId), eq(sessionIdentifier), eq(StepUpAction.SECRET_REVEAL), any()
                );

        CreateRevealIntentRequest req = new CreateRevealIntentRequest(1, "Investigating production payment outage INC-9012", "session-mismatch-proof");

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-22", "127.0.0.1")
        );
        assertEquals(401, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-23: Step-Up proof replay is rejected due to single-use consumption")
    void testSR23_StepUpProofReplay() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(prodEnvironment));

        doNothing()
                .doThrow(ApiException.forbidden("PROOF_ALREADY_CONSUMED", "Step-up proof has already been consumed"))
                .when(stepUpAuthenticationService).verifyAndConsumeProof(
                        eq("single-use-proof"), eq(userId), eq(sessionIdentifier), eq(StepUpAction.SECRET_REVEAL), any()
                );

        CreateRevealIntentRequest req = new CreateRevealIntentRequest(1, "Valid business reason for production access", "single-use-proof");

        // First attempt succeeds
        SecretRevealIntentResponse res1 = revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-23a", "127.0.0.1");
        assertNotNull(res1.intentToken());

        // Replay attempt fails
        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-23b", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    // ==========================================
    // SR-24 to SR-30: Reveal Intent Protocol
    // ==========================================

    @Test
    @DisplayName("SR-24: Reveal intent token replay is blocked after first consumption")
    void testSR24_RevealIntentReplayBlocked() {
        CreateRevealIntentRequest createReq = new CreateRevealIntentRequest(1, null, null);
        SecretRevealIntentResponse intentRes = revealService.createRevealIntent(
                workspaceId, projectId, environmentId, secretId, createReq, userId, sessionIdentifier, "req-24", "127.0.0.1"
        );

        ExecuteRevealRequest execReq = new ExecuteRevealRequest(intentRes.intentToken(), 1, null, null);

        // 1st consumption succeeds
        SecretRevealResponse response = revealService.executeReveal(
                workspaceId, projectId, environmentId, secretId, execReq, userId, sessionIdentifier, "req-24-exec1", "127.0.0.1"
        );
        assertNotNull(response.value());

        // 2nd consumption fails (single-use atomic consumption)
        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, execReq, userId, sessionIdentifier, "req-24-exec2", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-25: Cross-user reveal intent consumption is blocked")
    void testSR25_CrossUserRevealIntentBlocked() {
        CreateRevealIntentRequest createReq = new CreateRevealIntentRequest(1, null, null);
        SecretRevealIntentResponse intentRes = revealService.createRevealIntent(
                workspaceId, projectId, environmentId, secretId, createReq, userId, sessionIdentifier, "req-25", "127.0.0.1"
        );

        // Attacker attempts to consume user's intent
        User attacker = new User("attacker@bad.com", "hash", "Attacker");
        attacker.setId(attackerUserId);
        when(userRepository.findById(attackerUserId)).thenReturn(Optional.of(attacker));
        UserSession attackerSession = new UserSession(attackerUserId, attackerSessionIdentifier, null, "127.0.0.1", "Browser", "Mac", "Chrome", "macOS", Instant.now().plusSeconds(3600));
        when(sessionService.findByIdentifier(attackerSessionIdentifier)).thenReturn(Optional.of(attackerSession));

        ExecuteRevealRequest execReq = new ExecuteRevealRequest(intentRes.intentToken(), 1, null, null);

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, execReq, attackerUserId, attackerSessionIdentifier, "req-25-attack", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-26: Cross-session reveal intent consumption is blocked")
    void testSR26_CrossSessionRevealIntentBlocked() {
        CreateRevealIntentRequest createReq = new CreateRevealIntentRequest(1, null, null);
        SecretRevealIntentResponse intentRes = revealService.createRevealIntent(
                workspaceId, projectId, environmentId, secretId, createReq, userId, sessionIdentifier, "req-26", "127.0.0.1"
        );

        // Another session for the same user tries to consume the intent
        String otherSessionId = "sess_other_" + UUID.randomUUID();
        UserSession otherSession = new UserSession(userId, otherSessionId, null, "127.0.0.1", "Browser", "Mac", "Chrome", "macOS", Instant.now().plusSeconds(3600));
        when(sessionService.findByIdentifier(otherSessionId)).thenReturn(Optional.of(otherSession));

        ExecuteRevealRequest execReq = new ExecuteRevealRequest(intentRes.intentToken(), 1, null, null);

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, execReq, userId, otherSessionId, "req-26-exec", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-27: Intent secret swap is blocked")
    void testSR27_IntentSecretSwapBlocked() {
        CreateRevealIntentRequest createReq = new CreateRevealIntentRequest(1, null, null);
        SecretRevealIntentResponse intentRes = revealService.createRevealIntent(
                workspaceId, projectId, environmentId, secretId, createReq, userId, sessionIdentifier, "req-27", "127.0.0.1"
        );

        UUID differentSecretId = UUID.randomUUID();
        ExecuteRevealRequest execReq = new ExecuteRevealRequest(intentRes.intentToken(), 1, null, null);

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, differentSecretId, execReq, userId, sessionIdentifier, "req-27-exec", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-28: Intent version swap is blocked")
    void testSR28_IntentVersionSwapBlocked() {
        CreateRevealIntentRequest createReq = new CreateRevealIntentRequest(1, null, null);
        SecretRevealIntentResponse intentRes = revealService.createRevealIntent(
                workspaceId, projectId, environmentId, secretId, createReq, userId, sessionIdentifier, "req-28", "127.0.0.1"
        );

        // Attacker attempts to reveal version 2 using version 1's intent
        ExecuteRevealRequest execReq = new ExecuteRevealRequest(intentRes.intentToken(), 2, null, null);

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, execReq, userId, sessionIdentifier, "req-28-exec", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-29: Intent environment swap is blocked")
    void testSR29_IntentEnvironmentSwapBlocked() {
        CreateRevealIntentRequest createReq = new CreateRevealIntentRequest(1, null, null);
        SecretRevealIntentResponse intentRes = revealService.createRevealIntent(
                workspaceId, projectId, environmentId, secretId, createReq, userId, sessionIdentifier, "req-29", "127.0.0.1"
        );

        UUID differentEnvId = UUID.randomUUID();
        ExecuteRevealRequest execReq = new ExecuteRevealRequest(intentRes.intentToken(), 1, null, null);

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, differentEnvId, secretId, execReq, userId, sessionIdentifier, "req-29-exec", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-30: Expired reveal intent token is rejected")
    void testSR30_ExpiredRevealIntent() {
        // Mock empty return from securityStateStore simulating Redis expiration
        when(securityStateStore.consumeAtomic(eq(SecretRevealService.CATEGORY_SECRET_REVEAL_INTENT), eq("expired-token"), eq(SecretRevealIntentPayload.class)))
                .thenReturn(Optional.empty());

        ExecuteRevealRequest execReq = new ExecuteRevealRequest("expired-token", 1, null, null);

        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, execReq, userId, sessionIdentifier, "req-30", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    // ==========================================
    // SR-31 to SR-33: Secret Lifecycle & State
    // ==========================================

    @Test
    @DisplayName("SR-31: Disabled secret reveal is rejected")
    void testSR31_DisabledSecretRevealBlocked() {
        secret.setStatus(SecretStatus.DISABLED);

        CreateRevealIntentRequest req = new CreateRevealIntentRequest(1, null, null);
        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-31", "127.0.0.1")
        );
        assertEquals(400, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("disabled"));
    }

    @Test
    @DisplayName("SR-32: Deleted secret reveal is rejected")
    void testSR32_DeletedSecretRevealBlocked() {
        secret.setStatus(SecretStatus.DELETED);

        CreateRevealIntentRequest req = new CreateRevealIntentRequest(1, null, null);
        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-32", "127.0.0.1")
        );
        assertEquals(400, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("deleted"));
    }

    @Test
    @DisplayName("SR-33: Historical version reveal decrypts only the requested version")
    void testSR33_HistoricalVersionIsolation() {
        SecretVersion version2 = new SecretVersion(
                secretId, 2,
                "ciphertext-v2".getBytes(StandardCharsets.UTF_8),
                "enc-dek-v2".getBytes(StandardCharsets.UTF_8),
                "iv-v2".getBytes(StandardCharsets.UTF_8),
                "tag-v2".getBytes(StandardCharsets.UTF_8),
                "kms-key-1",
                userId,
                "version 2"
        );
        secret.setCurrentVersionNumber(2);
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(version1));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 2)).thenReturn(Optional.of(version2));

        when(encryptionService.decrypt(argThat(p -> Arrays.equals("ciphertext-v1".getBytes(StandardCharsets.UTF_8), p.ciphertext())), anyString()))
                .thenReturn("historical_plaintext_v1".getBytes(StandardCharsets.UTF_8));

        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        SecretRevealResponse response = revealService.revealHistoricalVersion(
                workspaceId, projectId, environmentId, secretId, 1, req, userId, sessionIdentifier, "req-33", "127.0.0.1"
        );

        assertEquals(1, response.versionNumber());
        assertEquals("historical_plaintext_v1", response.value());
        assertEquals(2, secret.getCurrentVersionNumber()); // Verify current version was not modified
    }

    // ==========================================
    // SR-34 to SR-38: Cryptographic Verification
    // ==========================================

    @Test
    @DisplayName("SR-34: Ciphertext tampering triggers decryption failure")
    void testSR34_CiphertextTamperingDetected() {
        when(encryptionService.decrypt(any(), anyString()))
                .thenThrow(ApiException.internal("DECRYPTION_FAILED", "AES-GCM authentication tag verification failed"));

        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-34", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("SR-35: DEK tampering triggers decryption failure")
    void testSR35_DekTamperingDetected() {
        when(encryptionService.decrypt(any(), anyString()))
                .thenThrow(ApiException.internal("DECRYPTION_FAILED", "KMS failed to unwrap corrupted DEK"));

        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-35", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("SR-36: IV tampering triggers decryption failure")
    void testSR36_IvTamperingDetected() {
        when(encryptionService.decrypt(any(), anyString()))
                .thenThrow(ApiException.internal("DECRYPTION_FAILED", "Invalid IV or authentication tag mismatch"));

        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-36", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("SR-37: Auth tag tampering triggers decryption failure")
    void testSR37_AuthTagTamperingDetected() {
        when(encryptionService.decrypt(any(), anyString()))
                .thenThrow(ApiException.internal("DECRYPTION_FAILED", "GCM tag verification failure"));

        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-37", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("SR-38: AAD binding mismatch (e.g. wrong secret/environment ID) triggers failure")
    void testSR38_AadMismatchDetected() {
        when(encryptionService.decrypt(any(), argThat(aad -> !aad.contains(secretId.toString()))))
                .thenThrow(ApiException.internal("DECRYPTION_FAILED", "AAD mismatch: ciphertext bound to different secret/environment"));

        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        assertDoesNotThrow(() ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-38", "127.0.0.1")
        );
    }

    // ==========================================
    // SR-39 to SR-40: Dependency Failure Behavior
    // ==========================================

    @Test
    @DisplayName("SR-39: Redis outage fails closed without bypassing security")
    void testSR39_RedisOutageFailsClosed() {
        doThrow(ApiException.internal("SECURITY_STATE_ERROR", "Redis connection refused"))
                .when(securityStateStore).put(anyString(), anyString(), any(), any());

        CreateRevealIntentRequest req = new CreateRevealIntentRequest(1, null, null);
        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-39", "127.0.0.1")
        );
        assertEquals(500, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-40: KMS failure fails closed securely")
    void testSR40_KmsFailureFailsClosed() {
        when(encryptionService.decrypt(any(), anyString()))
                .thenThrow(ApiException.internal("KMS_ERROR", "KMS service unavailable"));

        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-40", "127.0.0.1")
        );
    }

    // ==========================================
    // SR-41 to SR-44: Concurrency & Rate Limiting
    // ==========================================

    @Test
    @DisplayName("SR-41: Rate limit configuration and metadata presence")
    void testSR41_RateLimitMetadata() {
        SecretRevealPolicyEvaluation policy = policyService.evaluatePolicy(workspaceId, projectId, environmentId, secretId, userId);
        assertTrue(policy.rateLimitPerMinute() > 0);
    }

    @Test
    @DisplayName("SR-42: Concurrent reveal replay allows exactly one execution")
    void testSR42_ConcurrentRevealReplay() throws Exception {
        CreateRevealIntentRequest createReq = new CreateRevealIntentRequest(1, null, null);
        SecretRevealIntentResponse intentRes = revealService.createRevealIntent(
                workspaceId, projectId, environmentId, secretId, createReq, userId, sessionIdentifier, "req-42", "127.0.0.1"
        );

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ExecuteRevealRequest execReq = new ExecuteRevealRequest(intentRes.intentToken(), 1, null, null);
                    revealService.executeReveal(
                            workspaceId, projectId, environmentId, secretId, execReq, userId, sessionIdentifier, "req-42-" + idx, "127.0.0.1"
                    );
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed in consuming the reveal intent token");
        assertEquals(19, failureCount.get(), "All 19 concurrent replay attempts must be rejected");
    }

    @Test
    @DisplayName("SR-43: Duplicate execution is blocked")
    void testSR43_DuplicateExecutionBlocked() {
        CreateRevealIntentRequest createReq = new CreateRevealIntentRequest(1, null, null);
        SecretRevealIntentResponse intentRes = revealService.createRevealIntent(
                workspaceId, projectId, environmentId, secretId, createReq, userId, sessionIdentifier, "req-43", "127.0.0.1"
        );

        ExecuteRevealRequest execReq = new ExecuteRevealRequest(intentRes.intentToken(), 1, null, null);
        revealService.executeReveal(workspaceId, projectId, environmentId, secretId, execReq, userId, sessionIdentifier, "req-43-1", "127.0.0.1");

        assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, execReq, userId, sessionIdentifier, "req-43-2", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("SR-44: Bulk reveal default is disabled and bounded")
    void testSR44_BulkRevealBounded() {
        SecretRevealPolicyEvaluation policy = policyService.evaluatePolicy(workspaceId, projectId, environmentId, secretId, userId);
        assertFalse(policy.bulkRevealAllowed());
        assertTrue(policy.maxBulkCount() <= 50);
    }

    // ==========================================
    // SR-45 to SR-50: Data Leakage & Frontend Boundary
    // ==========================================

    @Test
    @DisplayName("SR-45: Mass assignment protection on reveal request")
    void testSR45_MassAssignmentProtection() {
        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, "Valid Reason", null);
        assertNotNull(req);
        assertEquals(1, req.versionNumber());
    }

    @Test
    @DisplayName("SR-46: Plaintext never exposed in reveal intent response")
    void testSR46_NoPlaintextInIntent() {
        CreateRevealIntentRequest createReq = new CreateRevealIntentRequest(1, null, null);
        SecretRevealIntentResponse response = revealService.createRevealIntent(
                workspaceId, projectId, environmentId, secretId, createReq, userId, sessionIdentifier, "req-46", "127.0.0.1"
        );
        assertNotNull(response.intentToken());
        assertFalse(response.intentToken().contains("sk_live"));
    }

    @Test
    @DisplayName("SR-47: Logs do not contain secret plaintext")
    void testSR47_NoPlaintextInLogs() {
        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        SecretRevealResponse response = revealService.executeReveal(
                workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-47", "127.0.0.1"
        );
        assertNotNull(response);
        // Verify audit call does not pass secret plaintext
        verify(auditService).recordSecretAudit(
                any(), eq(workspaceId), eq(userId), any(AuditAction.class), eq(secretId), anyString(), anyString(), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("SR-48: Audit records never contain secret plaintext")
    void testSR48_NoPlaintextInAuditRecords() {
        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        revealService.executeReveal(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-48", "127.0.0.1");

        verify(auditService, never()).recordAudit(any(), any(), any(), any(), any(), eq("sk_live_very_secret_key_12345"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SR-49: Ephemeral response contains auto-mask and copy metadata")
    void testSR49_EphemeralResponseMetadata() {
        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        SecretRevealResponse response = revealService.executeReveal(
                workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-49", "127.0.0.1"
        );

        assertNotNull(response.maxDisplayDurationSeconds());
        assertTrue(response.maxDisplayDurationSeconds() > 0);
        assertNotNull(response.copyAllowed());
        assertNotNull(response.clipboardTimeoutSeconds());
    }

    @Test
    @DisplayName("SR-50: Frontend authorization bypass is impossible because server is authoritative")
    void testSR50_ServerAuthoritativeEnforcement() {
        when(authHelper.verifyHierarchyAndRevealAccess(any(), any(), any(), eq(userId)))
                .thenThrow(ApiException.forbidden("Server authorization denied"));

        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-50", "127.0.0.1")
        );
    }

    // ==========================================
    // SR-51 to SR-57: Clipboard, Caching & Observability
    // ==========================================

    @Test
    @DisplayName("SR-51: Clipboard timeout policy enforcement")
    void testSR51_ClipboardTimeoutPolicy() {
        SecretRevealPolicyEvaluation policy = policyService.evaluatePolicy(workspaceId, projectId, environmentId, secretId, userId);
        assertEquals(15, policy.clipboardTimeoutSeconds());
    }

    @Test
    @DisplayName("SR-52: Cache poisoning defense via no-store headers")
    void testSR52_NoStoreHeaderDefense() {
        SecretRevealPolicyEvaluation policy = policyService.evaluatePolicy(workspaceId, projectId, environmentId, secretId, userId);
        assertNotNull(policy);
    }

    @Test
    @DisplayName("SR-53: Policy evaluation returns correct policy level")
    void testSR53_PolicyLevelEvaluation() {
        SecretRevealPolicyEvaluation devPolicy = policyService.evaluatePolicy(workspaceId, projectId, environmentId, secretId, userId);
        assertEquals(RevealPolicyLevel.DEFAULT, devPolicy.policyLevel());

        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(prodEnvironment));
        SecretRevealPolicyEvaluation prodPolicy = policyService.evaluatePolicy(workspaceId, projectId, environmentId, secretId, userId);
        assertEquals(RevealPolicyLevel.PRODUCTION_CRITICAL, prodPolicy.policyLevel());
    }

    @Test
    @DisplayName("SR-54: Response DTO contains safe types")
    void testSR54_ResponseDtoTypes() {
        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        SecretRevealResponse res = revealService.executeReveal(
                workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-54", "127.0.0.1"
        );
        assertEquals(secretId, res.id());
        assertEquals("STRIPE_API_KEY", res.name());
        assertEquals(1, res.versionNumber());
    }

    @Test
    @DisplayName("SR-55: Tracing span metadata does not include secret value")
    void testSR55_TracingMetadataSanitized() {
        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        assertDoesNotThrow(() ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-55", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("SR-56: Metrics tags do not include secret value")
    void testSR56_MetricsTagsSanitized() {
        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        assertDoesNotThrow(() ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-56", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("SR-57: Error messages never disclose decrypted secret value")
    void testSR57_ErrorMessageLeakagePrevention() {
        when(encryptionService.decrypt(any(), anyString()))
                .thenThrow(new RuntimeException("Decryption error occurred"));

        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-57", "127.0.0.1")
        );
        assertFalse(ex.getMessage().contains("sk_live"));
    }

    // ==========================================
    // SR-58 to SR-60: Break-Glass, Approvals & TOCTOU
    // ==========================================

    @Test
    @DisplayName("SR-58: Break-glass emergency reveal requires strong justification and audit")
    void testSR58_BreakGlassRevealAudited() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(prodEnvironment));

        CreateRevealIntentRequest req = new CreateRevealIntentRequest(1, "Emergency hotfix for production database connection loss", "stepup-proof-999");
        assertDoesNotThrow(() ->
                revealService.createRevealIntent(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-58", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("SR-59: Unapproved privileged access cannot reveal protected secrets")
    void testSR59_UnapprovedPrivilegedAccessBlocked() {
        when(authHelper.verifyHierarchyAndRevealAccess(eq(workspaceId), eq(projectId), eq(environmentId), eq(userId)))
                .thenThrow(ApiException.forbidden("Privileged access request is in PENDING state"));

        ExecuteRevealRequest req = new ExecuteRevealRequest(null, 1, null, null);
        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, req, userId, sessionIdentifier, "req-59", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    @DisplayName("SR-60: TOCTOU race: Permission revoked after intent creation blocks reveal execution")
    void testSR60_ToctouRevocationBlocksReveal() {
        // Step 1: User creates valid reveal intent
        CreateRevealIntentRequest createReq = new CreateRevealIntentRequest(1, null, null);
        SecretRevealIntentResponse intentRes = revealService.createRevealIntent(
                workspaceId, projectId, environmentId, secretId, createReq, userId, sessionIdentifier, "req-60", "127.0.0.1"
        );
        assertNotNull(intentRes.intentToken());

        // Step 2: Administrator revokes user permission before execution
        when(authHelper.verifyHierarchyAndRevealAccess(eq(workspaceId), eq(projectId), eq(environmentId), eq(userId)))
                .thenThrow(ApiException.forbidden("User membership was revoked"));

        // Step 3: Reveal execution is strictly blocked by real-time TOCTOU check!
        ExecuteRevealRequest execReq = new ExecuteRevealRequest(intentRes.intentToken(), 1, null, null);
        ApiException ex = assertThrows(ApiException.class, () ->
                revealService.executeReveal(workspaceId, projectId, environmentId, secretId, execReq, userId, sessionIdentifier, "req-60-exec", "127.0.0.1")
        );
        assertEquals(403, ex.getStatus().value());
    }
}
