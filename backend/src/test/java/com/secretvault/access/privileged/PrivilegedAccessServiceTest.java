package com.secretvault.access.privileged;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.privileged.dto.*;
import com.secretvault.access.privileged.entity.*;
import com.secretvault.access.privileged.model.*;
import com.secretvault.access.privileged.repository.*;
import com.secretvault.access.privileged.service.DefaultPrivilegedAccessService;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.entity.UserStatus;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.stepup.model.StepUpAction;
import com.secretvault.auth.stepup.model.StepUpContext;
import com.secretvault.auth.stepup.service.StepUpAuthenticationService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrivilegedAccessServiceTest {

    @Mock
    private PrivilegedAccessPolicyRepository policyRepository;
    @Mock
    private PrivilegedAccessRequestRepository requestRepository;
    @Mock
    private PrivilegedAccessApprovalRepository approvalRepository;
    @Mock
    private PrivilegedAccessElevationRepository elevationRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private WorkspaceMembershipRepository membershipRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private EnvironmentRepository environmentRepository;
    @Mock
    private SecretRepository secretRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private StepUpAuthenticationService stepUpService;
    @Mock
    private EffectiveAccessService effectiveAccessService;

    private Clock fixedClock;
    private Instant baseInstant;
    private DefaultPrivilegedAccessService privilegedService;

    private UUID workspaceId;
    private UUID requesterId;
    private UUID approver1Id;
    private UUID approver2Id;
    private UUID projectId;
    private UUID environmentId;

    private Workspace workspace;
    private WorkspaceMembership requesterMembership;
    private WorkspaceMembership approver1Membership;
    private WorkspaceMembership approver2Membership;
    private Project project;
    private Environment environment;
    private User requester;
    private User approver1;
    private User approver2;

    @BeforeEach
    void setUp() {
        baseInstant = Instant.parse("2026-10-03T12:00:00Z");
        fixedClock = Clock.fixed(baseInstant, ZoneOffset.UTC);

        workspaceId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        approver1Id = UUID.randomUUID();
        approver2Id = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();

        workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setName("Production Workspace");

        requester = new User();
        requester.setId(requesterId);
        requester.setEmail("requester@example.com");
        requester.setFullName("Requesting Developer");
        requester.setStatus(UserStatus.ACTIVE);

        approver1 = new User();
        approver1.setId(approver1Id);
        approver1.setEmail("approver1@example.com");
        approver1.setFullName("Security Lead");
        approver1.setStatus(UserStatus.ACTIVE);

        approver2 = new User();
        approver2.setId(approver2Id);
        approver2.setEmail("approver2@example.com");
        approver2.setFullName("Operations Lead");
        approver2.setStatus(UserStatus.ACTIVE);

        requesterMembership = new WorkspaceMembership(workspaceId, requesterId, WorkspaceRole.DEVELOPER);
        approver1Membership = new WorkspaceMembership(workspaceId, approver1Id, WorkspaceRole.ADMIN);
        approver2Membership = new WorkspaceMembership(workspaceId, approver2Id, WorkspaceRole.ADMIN);

        project = new Project(workspaceId, "Payment Service", "payment-service", "Payment microservice", requesterId);
        project.setId(projectId);

        environment = new Environment(projectId, "Production", "production", EnvType.PRODUCTION, "Prod env", true, requesterId);
        environment.setId(environmentId);

        privilegedService = new DefaultPrivilegedAccessService(
                policyRepository,
                requestRepository,
                approvalRepository,
                elevationRepository,
                workspaceRepository,
                membershipRepository,
                projectRepository,
                environmentRepository,
                secretRepository,
                userRepository,
                auditService,
                stepUpService,
                effectiveAccessService,
                fixedClock
        );
    }

    @Nested
    @DisplayName("Privileged Access Request Submission")
    class RequestSubmissionTests {

        @Test
        @DisplayName("Successfully creates pending request with dual approval quorum")
        void createsPendingRequestWithDualQuorum() {
            when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
            when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, requesterId)).thenReturn(Optional.of(requesterMembership));
            when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
            when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

            PrivilegedAccessPolicy policy = new PrivilegedAccessPolicy(
                    workspaceId, PrivilegedPolicyScope.ENVIRONMENT, projectId, environmentId, null,
                    PrivilegedAction.SECRET_REVEAL, true, 2, true, false, 60
            );
            when(policyRepository.findApplicablePolicies(eq(workspaceId), eq(PrivilegedAction.SECRET_REVEAL)))
                    .thenReturn(List.of(policy));

            when(requestRepository.save(any(PrivilegedAccessRequest.class))).thenAnswer(inv -> {
                PrivilegedAccessRequest req = inv.getArgument(0);
                req.setId(UUID.randomUUID());
                return req;
            });

            CreatePrivilegedAccessRequest createReq = new CreatePrivilegedAccessRequest(
                    PrivilegedAction.SECRET_REVEAL,
                    PrivilegedPolicyScope.ENVIRONMENT,
                    projectId,
                    environmentId,
                    null,
                    "secret.reveal",
                    30,
                    "Emergency database incident investigation",
                    requesterId,
                    null
            );

            PrivilegedAccessRequestResponse response = privilegedService.createRequest(workspaceId, requesterId, "session-1", createReq);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PrivilegedRequestStatus.PENDING);
            assertThat(response.requiredQuorum()).isEqualTo(2);
            assertThat(response.currentApprovalsCount()).isEqualTo(0);
            assertThat(response.durationMinutes()).isEqualTo(30);

            verify(auditService).recordAudit(
                    isNull(), eq(workspaceId), eq(requesterId), eq("USER"),
                    eq(AuditAction.PRIVILEGED_ACCESS_REQUESTED), eq("PRIVILEGED_REQUEST"),
                    any(UUID.class), anyString(), isNull(), eq("SUCCESS")
            );
        }

        @Test
        @DisplayName("Rejects request if duration exceeds policy maximum")
        void rejectsExceededDuration() {
            when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
            when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, requesterId)).thenReturn(Optional.of(requesterMembership));

            PrivilegedAccessPolicy policy = new PrivilegedAccessPolicy(
                    workspaceId, PrivilegedPolicyScope.WORKSPACE, null, null, null,
                    PrivilegedAction.ROLE_CHANGE, true, 1, true, false, 30
            );
            when(policyRepository.findApplicablePolicies(eq(workspaceId), eq(PrivilegedAction.ROLE_CHANGE)))
                    .thenReturn(List.of(policy));

            CreatePrivilegedAccessRequest createReq = new CreatePrivilegedAccessRequest(
                    PrivilegedAction.ROLE_CHANGE,
                    PrivilegedPolicyScope.WORKSPACE,
                    null, null, null,
                    null,
                    60,
                    "Elevating role for maintenance",
                    requesterId,
                    null
            );

            assertThatThrownBy(() -> privilegedService.createRequest(workspaceId, requesterId, "session-1", createReq))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("exceeds maximum policy limit");
        }
    }

    @Nested
    @DisplayName("Dual Approval & Quorum Enforcement")
    class ApprovalQuorumTests {

        @Test
        @DisplayName("Anti-Self-Approval: Requester cannot approve their own request")
        void antiSelfApprovalEnforced() {
            UUID reqId = UUID.randomUUID();
            PrivilegedAccessRequest req = new PrivilegedAccessRequest(
                    workspaceId, requesterId, requesterId,
                    PrivilegedAction.SECRET_REVEAL, PrivilegedPolicyScope.ENVIRONMENT,
                    projectId, environmentId, null, "secret.reveal", "Incident", 30, false, 2
            );
            req.setId(reqId);

            when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
            when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, requesterId)).thenReturn(Optional.of(requesterMembership));
            when(requestRepository.findByIdAndWorkspaceId(reqId, workspaceId)).thenReturn(Optional.of(req));

            PrivilegedAccessPolicy policy = new PrivilegedAccessPolicy(
                    workspaceId, PrivilegedPolicyScope.WORKSPACE, null, null, null,
                    PrivilegedAction.SECRET_REVEAL, true, 2, true, false, 60
            );
            when(policyRepository.findApplicablePolicies(eq(workspaceId), eq(PrivilegedAction.SECRET_REVEAL)))
                    .thenReturn(List.of(policy));

            ApprovePrivilegedRequest approveReq = new ApprovePrivilegedRequest(ApprovalDecision.APPROVED, "Self approval attempt", null);

            assertThatThrownBy(() -> privilegedService.approveRequest(workspaceId, reqId, requesterId, "session-1", approveReq))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Anti-self-approval rule");
        }

        @Test
        @DisplayName("Quorum requires distinct approvers before activating elevation")
        void multiApproverQuorumActivatesElevation() {
            UUID reqId = UUID.randomUUID();
            PrivilegedAccessRequest req = new PrivilegedAccessRequest(
                    workspaceId, requesterId, requesterId,
                    PrivilegedAction.SECRET_REVEAL, PrivilegedPolicyScope.ENVIRONMENT,
                    projectId, environmentId, null, "secret.reveal", "Incident", 30, false, 2
            );
            req.setId(reqId);
            req.setExpiresAt(baseInstant.plus(Duration.ofHours(24)));

            when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
            when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, approver1Id)).thenReturn(Optional.of(approver1Membership));
            when(requestRepository.findByIdAndWorkspaceId(reqId, workspaceId)).thenReturn(Optional.of(req));
            when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // First approval (count = 1, required = 2)
            when(approvalRepository.findByRequestIdAndApproverId(reqId, approver1Id)).thenReturn(Optional.empty());
            when(approvalRepository.countDistinctApproversByDecision(reqId, ApprovalDecision.APPROVED)).thenReturn(1L);

            ApprovePrivilegedRequest approve1 = new ApprovePrivilegedRequest(ApprovalDecision.APPROVED, "Approver 1 OK", null);
            PrivilegedAccessRequestResponse res1 = privilegedService.approveRequest(workspaceId, reqId, approver1Id, "session-2", approve1);

            assertThat(res1.status()).isEqualTo(PrivilegedRequestStatus.PENDING);
            assertThat(res1.currentApprovalsCount()).isEqualTo(1);
            verify(elevationRepository, never()).save(any());

            // Second approval from distinct approver (count = 2, required = 2)
            when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, approver2Id)).thenReturn(Optional.of(approver2Membership));
            when(approvalRepository.findByRequestIdAndApproverId(reqId, approver2Id)).thenReturn(Optional.empty());
            when(approvalRepository.countDistinctApproversByDecision(reqId, ApprovalDecision.APPROVED)).thenReturn(2L);

            ApprovePrivilegedRequest approve2 = new ApprovePrivilegedRequest(ApprovalDecision.APPROVED, "Approver 2 OK", null);
            PrivilegedAccessRequestResponse res2 = privilegedService.approveRequest(workspaceId, reqId, approver2Id, "session-3", approve2);

            assertThat(res2.status()).isEqualTo(PrivilegedRequestStatus.APPROVED);
            assertThat(res2.currentApprovalsCount()).isEqualTo(2);
            verify(elevationRepository).save(any(PrivilegedAccessElevation.class));
            verify(auditService).recordAudit(
                    isNull(), eq(workspaceId), eq(approver2Id), eq("USER"),
                    eq(AuditAction.APPROVAL_QUORUM_REACHED), eq("PRIVILEGED_REQUEST"),
                    eq(reqId), anyString(), isNull(), eq("SUCCESS")
            );
        }

        @Test
        @DisplayName("Duplicate approval attempt by the same approver is rejected")
        void rejectsDuplicateApproval() {
            UUID reqId = UUID.randomUUID();
            PrivilegedAccessRequest req = new PrivilegedAccessRequest(
                    workspaceId, requesterId, requesterId,
                    PrivilegedAction.SECRET_REVEAL, PrivilegedPolicyScope.ENVIRONMENT,
                    projectId, environmentId, null, "secret.reveal", "Incident", 30, false, 2
            );
            req.setId(reqId);
            req.setExpiresAt(baseInstant.plus(Duration.ofHours(24)));

            when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
            when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, approver1Id)).thenReturn(Optional.of(approver1Membership));
            when(requestRepository.findByIdAndWorkspaceId(reqId, workspaceId)).thenReturn(Optional.of(req));

            PrivilegedAccessApproval existing = new PrivilegedAccessApproval(reqId, approver1Id, ApprovalDecision.APPROVED, "First time", null, null);
            when(approvalRepository.findByRequestIdAndApproverId(reqId, approver1Id)).thenReturn(Optional.of(existing));

            ApprovePrivilegedRequest duplicateReq = new ApprovePrivilegedRequest(ApprovalDecision.APPROVED, "Second attempt", null);

            assertThatThrownBy(() -> privilegedService.approveRequest(workspaceId, reqId, approver1Id, "session-2", duplicateReq))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Duplicate approval");
        }
    }

    @Nested
    @DisplayName("Break-Glass Emergency Access")
    class BreakGlassTests {

        @Test
        @DisplayName("Successfully activates strongly-authenticated, time-bounded emergency elevation")
        void breakGlassEmergencySuccess() {
            when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
            when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, requesterId)).thenReturn(Optional.of(requesterMembership));
            when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
            when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

            PrivilegedAccessPolicy policy = new PrivilegedAccessPolicy(
                    workspaceId, PrivilegedPolicyScope.WORKSPACE, null, null, null,
                    PrivilegedAction.BREAK_GLASS_REQUEST, true, 1, true, true, 30
            );
            policy.setBreakGlassAllowed(true);
            policy.setEmergencyDurationLimitMinutes(30);
            when(policyRepository.findApplicablePolicies(eq(workspaceId), eq(PrivilegedAction.BREAK_GLASS_REQUEST)))
                    .thenReturn(List.of(policy));

            when(requestRepository.save(any(PrivilegedAccessRequest.class))).thenAnswer(inv -> {
                PrivilegedAccessRequest r = inv.getArgument(0);
                r.setId(UUID.randomUUID());
                return r;
            });

            BreakGlassRequest bgReq = new BreakGlassRequest(
                    projectId,
                    environmentId,
                    null,
                    PrivilegedAction.BREAK_GLASS_REQUEST,
                    "secret.reveal",
                    30,
                    "Critical outage: payment gateway downtime in production requiring emergency remediation",
                    "stup_mockproof123"
            );

            PrivilegedAccessRequestResponse res = privilegedService.breakGlass(workspaceId, requesterId, "session-1", bgReq);

            assertThat(res).isNotNull();
            assertThat(res.isBreakGlass()).isTrue();
            assertThat(res.status()).isEqualTo(PrivilegedRequestStatus.EXECUTED);

            // Verified step-up proof consumed
            verify(stepUpService).verifyAndConsumeProof(
                    eq("stup_mockproof123"), eq(requesterId), eq("session-1"),
                    eq(StepUpAction.BREAK_GLASS_REQUEST), any(StepUpContext.class)
            );

            // Verified active elevation created
            verify(elevationRepository).save(argThat(elev ->
                    elev.isBreakGlass() &&
                    elev.getUserId().equals(requesterId) &&
                    elev.getProjectId().equals(projectId) &&
                    elev.getEnvironmentId().equals(environmentId) &&
                    elev.getStatus() == ElevationStatus.ACTIVE
            ));

            // Verified audit records
            verify(auditService).recordAudit(
                    isNull(), eq(workspaceId), eq(requesterId), eq("USER"),
                    eq(AuditAction.BREAK_GLASS_REQUESTED), eq("BREAK_GLASS"),
                    any(UUID.class), anyString(), isNull(), eq("SUCCESS")
            );
            verify(auditService).recordAudit(
                    isNull(), eq(workspaceId), eq(requesterId), eq("USER"),
                    eq(AuditAction.BREAK_GLASS_EXECUTED), eq("BREAK_GLASS"),
                    any(UUID.class), anyString(), isNull(), eq("SUCCESS")
            );
        }

        @Test
        @DisplayName("Rejects break-glass if justification is insufficient")
        void rejectsShortJustification() {
            when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
            when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, requesterId)).thenReturn(Optional.of(requesterMembership));

            BreakGlassRequest bgReq = new BreakGlassRequest(
                    projectId, environmentId, null,
                    PrivilegedAction.BREAK_GLASS_REQUEST, "secret.reveal",
                    30, "Too short", "stup_proof"
            );

            assertThatThrownBy(() -> privilegedService.breakGlass(workspaceId, requesterId, "session-1", bgReq))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("justification");
        }
    }
}
