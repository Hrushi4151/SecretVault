package com.secretvault.access.grant.service;

import com.secretvault.access.grant.dto.AccessGrantResponse;
import com.secretvault.access.grant.dto.CreateAccessGrantRequest;
import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AccessGrantService {

    private static final Logger log = LoggerFactory.getLogger(AccessGrantService.class);

    private final AccessGrantRepository accessGrantRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final SecretRepository secretRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final EffectiveAccessService effectiveAccessService;

    public AccessGrantService(
            AccessGrantRepository accessGrantRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            SecretRepository secretRepository,
            UserRepository userRepository,
            AuditService auditService,
            EffectiveAccessService effectiveAccessService
    ) {
        this.accessGrantRepository = accessGrantRepository;
        this.membershipRepository = membershipRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.secretRepository = secretRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.effectiveAccessService = effectiveAccessService;
    }

    @Transactional
    public AccessGrantResponse createGrant(UUID workspaceId, CreateAccessGrantRequest request, UUID actorUserId) {
        // 1. Authorize Actor
        effectiveAccessService.checkPermission(workspaceId, request.projectId(), request.environmentId(), request.secretId(), AccessPermission.ACCESS_MANAGE, actorUserId);

        // 2. Validate Target User Workspace Membership
        WorkspaceMembership targetMember = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, request.userId())
                .orElseThrow(() -> ApiException.badRequest("Target user is not a member of this workspace"));

        // 3. Validate Scope Integrity Constraints
        validateScopeHierarchy(workspaceId, request.scopeType(), request.projectId(), request.environmentId(), request.secretId());

        // 4. Duplicate Grant Check
        boolean exists = accessGrantRepository.existsByWorkspaceIdAndUserIdAndScopeTypeAndProjectIdAndEnvironmentIdAndSecretIdAndPermission(
                workspaceId,
                request.userId(),
                request.scopeType(),
                request.projectId(),
                request.environmentId(),
                request.secretId(),
                request.permission()
        );
        if (exists) {
            throw ApiException.conflict("An identical access grant already exists for this user and scope");
        }

        AccessGrant grant = new AccessGrant(
                workspaceId,
                request.userId(),
                request.scopeType(),
                request.projectId(),
                request.environmentId(),
                request.secretId(),
                request.permission(),
                actorUserId
        );
        AccessGrant saved = accessGrantRepository.save(grant);

        auditService.logSuccess(
                AuditAction.ACCESS_GRANT_CREATED,
                "ACCESS_GRANT",
                saved.getId(),
                actorUserId,
                workspaceId,
                "Granted permission [" + request.permission().getCode() + "] to user [" + targetMember.getUserId() + "] on scope [" + request.scopeType() + "]"
        );

        log.info("Access grant [{}] created for user [{}] with permission [{}] by actor [{}]",
                saved.getId(), request.userId(), request.permission(), actorUserId);

        return mapToResponse(saved);
    }

    @Transactional
    public void revokeGrant(UUID workspaceId, UUID grantId, UUID actorUserId) {
        AccessGrant grant = accessGrantRepository.findByIdAndWorkspaceId(grantId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Access grant not found in this workspace"));

        effectiveAccessService.checkPermission(workspaceId, grant.getProjectId(), grant.getEnvironmentId(), grant.getSecretId(), AccessPermission.ACCESS_MANAGE, actorUserId);

        accessGrantRepository.delete(grant);

        auditService.logSuccess(
                AuditAction.ACCESS_GRANT_REVOKED,
                "ACCESS_GRANT",
                grant.getId(),
                actorUserId,
                workspaceId,
                "Revoked permission [" + grant.getPermission().getCode() + "] from user [" + grant.getUserId() + "]"
        );

        log.info("Access grant [{}] revoked by actor [{}]", grantId, actorUserId);
    }

    @Transactional(readOnly = true)
    public List<AccessGrantResponse> listGrants(UUID workspaceId, UUID actorUserId) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not a member of this workspace");
        }

        List<AccessGrant> grants = accessGrantRepository.findByWorkspaceId(workspaceId);
        return grants.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private void validateScopeHierarchy(UUID workspaceId, AccessScope scope, UUID projectId, UUID environmentId, UUID secretId) {
        switch (scope) {
            case WORKSPACE -> {
                if (projectId != null || environmentId != null || secretId != null) {
                    throw ApiException.badRequest("WORKSPACE scope grants must have null projectId, environmentId, and secretId");
                }
            }
            case PROJECT -> {
                if (projectId == null || environmentId != null || secretId != null) {
                    throw ApiException.badRequest("PROJECT scope grants require projectId and null environmentId/secretId");
                }
                Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                        .orElseThrow(() -> ApiException.badRequest("Project not found in this workspace"));
            }
            case ENVIRONMENT -> {
                if (projectId == null || environmentId == null || secretId != null) {
                    throw ApiException.badRequest("ENVIRONMENT scope grants require projectId and environmentId, with null secretId");
                }
                Environment env = environmentRepository.findByIdAndProjectId(environmentId, projectId)
                        .orElseThrow(() -> ApiException.badRequest("Environment not found in specified project"));
            }
            case SECRET -> {
                if (projectId == null || environmentId == null || secretId == null) {
                    throw ApiException.badRequest("SECRET scope grants require projectId, environmentId, and secretId");
                }
                Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                        .orElseThrow(() -> ApiException.badRequest("Secret not found in specified environment"));
            }
        }
    }

    private AccessGrantResponse mapToResponse(AccessGrant grant) {
        User user = userRepository.findById(grant.getUserId()).orElse(null);
        String userEmail = user != null ? user.getEmail() : "unknown";
        String userFullName = user != null ? user.getFullName() : "Unknown User";

        String projectName = grant.getProjectId() != null ? projectRepository.findById(grant.getProjectId()).map(Project::getName).orElse(null) : null;
        String envName = grant.getEnvironmentId() != null ? environmentRepository.findById(grant.getEnvironmentId()).map(Environment::getName).orElse(null) : null;
        String secretKey = grant.getSecretId() != null ? secretRepository.findById(grant.getSecretId()).map(Secret::getName).orElse(null) : null;

        return AccessGrantResponse.fromEntity(grant, userEmail, userFullName, projectName, envName, secretKey);
    }
}
