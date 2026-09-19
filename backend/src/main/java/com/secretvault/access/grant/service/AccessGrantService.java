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
import com.secretvault.common.dto.PageResponse;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enterprise service managing Granular Resource-Level Access Grants.
 * Enforces strict scope invariants, permission compatibility policies,
 * anti-privilege escalation checks, and audit logging.
 */
@Service
public class AccessGrantService {

    private static final Logger log = LoggerFactory.getLogger(AccessGrantService.class);

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "updatedAt", "permission", "scopeType");
    private static final int MAX_PAGE_SIZE = 100;

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
        // 1. Authorize Granter
        effectiveAccessService.checkPermission(
                workspaceId, request.projectId(), request.environmentId(), request.secretId(),
                AccessPermission.ACCESS_MANAGE, actorUserId
        );

        // 2. Validate Target User Workspace Membership & Identity
        WorkspaceMembership targetMember = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, request.userId())
                .orElseThrow(() -> ApiException.badRequest("Target user is not an active member of this workspace"));

        User targetUser = userRepository.findById(request.userId())
                .orElseThrow(() -> ApiException.notFound("Target user not found"));

        // 3. Validate Scope Hierarchy and Fetch Context
        Environment targetEnv = validateScopeHierarchy(workspaceId, request.scopeType(), request.projectId(), request.environmentId(), request.secretId());

        // 4. Validate Permission / Scope Compatibility Matrix
        validatePermissionScopeCompatibility(request.permission(), request.scopeType(), targetEnv);

        // 5. Anti-Privilege Escalation Guard:
        // Granter cannot grant administrative privileges higher than what they possess.
        if (request.permission() == AccessPermission.ACCESS_MANAGE ||
            request.permission() == AccessPermission.ACCESS_REVIEW_MANAGE ||
            request.permission() == AccessPermission.JIT_APPROVE) {
            effectiveAccessService.checkPermission(
                    workspaceId, request.projectId(), request.environmentId(), request.secretId(),
                    request.permission(), actorUserId
            );
        }

        // 6. Duplicate Grant Check
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

        AccessGrant saved;
        try {
            saved = accessGrantRepository.saveAndFlush(grant);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("An identical access grant already exists for this user and scope");
        }

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

        effectiveAccessService.checkPermission(
                workspaceId, grant.getProjectId(), grant.getEnvironmentId(), grant.getSecretId(),
                AccessPermission.ACCESS_MANAGE, actorUserId
        );

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
    public AccessGrantResponse getGrantById(UUID workspaceId, UUID grantId, UUID actorUserId) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not a member of this workspace");
        }

        AccessGrant grant = accessGrantRepository.findByIdAndWorkspaceId(grantId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Access grant not found in this workspace"));

        return mapToResponse(grant);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccessGrantResponse> listGrants(
            UUID workspaceId,
            UUID filterUserId,
            AccessScope filterScope,
            AccessPermission filterPermission,
            UUID filterProjectId,
            UUID filterEnvironmentId,
            UUID filterSecretId,
            Pageable pageable,
            UUID actorUserId
    ) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not a member of this workspace");
        }

        Pageable safePageable = sanitizePageable(pageable);

        Page<AccessGrant> grantPage = accessGrantRepository.findFilteredGrants(
                workspaceId,
                filterUserId,
                filterScope,
                filterPermission,
                filterProjectId,
                filterEnvironmentId,
                filterSecretId,
                safePageable
        );

        List<AccessGrantResponse> content = grantPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return new PageResponse<>(
                content,
                grantPage.getNumber(),
                grantPage.getSize(),
                grantPage.getTotalElements(),
                grantPage.getTotalPages(),
                grantPage.isFirst(),
                grantPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public List<AccessGrantResponse> listAllGrantsForWorkspace(UUID workspaceId, UUID actorUserId) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not a member of this workspace");
        }

        List<AccessGrant> grants = accessGrantRepository.findByWorkspaceId(workspaceId);
        return grants.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private Environment validateScopeHierarchy(UUID workspaceId, AccessScope scope, UUID projectId, UUID environmentId, UUID secretId) {
        if (scope == null) {
            throw ApiException.badRequest("scopeType is required");
        }

        switch (scope) {
            case WORKSPACE -> {
                if (projectId != null || environmentId != null || secretId != null) {
                    throw ApiException.badRequest("WORKSPACE scope grants must have null projectId, environmentId, and secretId");
                }
                return null;
            }
            case PROJECT -> {
                if (projectId == null || environmentId != null || secretId != null) {
                    throw ApiException.badRequest("PROJECT scope grants require projectId and null environmentId/secretId");
                }
                projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                        .orElseThrow(() -> ApiException.badRequest("Project not found in this workspace"));
                return null;
            }
            case ENVIRONMENT -> {
                if (projectId == null || environmentId == null || secretId != null) {
                    throw ApiException.badRequest("ENVIRONMENT scope grants require projectId and environmentId, with null secretId");
                }
                Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                        .orElseThrow(() -> ApiException.badRequest("Project not found in this workspace"));

                Environment env = environmentRepository.findByIdAndProjectId(environmentId, projectId)
                        .orElseThrow(() -> ApiException.badRequest("Environment not found in specified project"));

                return env;
            }
            case SECRET -> {
                if (projectId == null || environmentId == null || secretId == null) {
                    throw ApiException.badRequest("SECRET scope grants require projectId, environmentId, and secretId");
                }
                Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                        .orElseThrow(() -> ApiException.badRequest("Project not found in this workspace"));

                Environment env = environmentRepository.findByIdAndProjectId(environmentId, projectId)
                        .orElseThrow(() -> ApiException.badRequest("Environment not found in specified project"));

                Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                        .orElseThrow(() -> ApiException.badRequest("Secret not found in specified environment"));

                if (secret.getStatus() == SecretStatus.DELETED) {
                    throw ApiException.badRequest("Cannot create access grants on a deleted secret");
                }

                return env;
            }
            default -> throw ApiException.badRequest("Unknown scope type: " + scope);
        }
    }

    private void validatePermissionScopeCompatibility(AccessPermission permission, AccessScope scope, Environment env) {
        if (permission == null || scope == null) {
            throw ApiException.badRequest("Permission and scopeType must not be null");
        }

        switch (permission) {
            case SECRET_BRANCH -> {
                if (env != null && env.getEnvType() != EnvType.DEVELOPMENT) {
                    throw ApiException.badRequest(
                            "Feature branch permission (secret.branch) cannot be granted on " + env.getEnvType() + " environments. Feature branches are only permitted in DEVELOPMENT."
                    );
                }
            }
            case ENVIRONMENT_PROMOTE, ENVIRONMENT_MANAGE -> {
                if (scope == AccessScope.SECRET) {
                    throw ApiException.badRequest(
                            "Permission [" + permission.getCode() + "] is an environment-level operation and cannot be granted at SECRET scope"
                    );
                }
            }
            case ACCESS_MANAGE, JIT_APPROVE, ACCESS_REVIEW_MANAGE -> {
                if (scope == AccessScope.SECRET) {
                    throw ApiException.badRequest(
                            "Administrative permission [" + permission.getCode() + "] cannot be granted at SECRET scope. Target WORKSPACE, PROJECT, or ENVIRONMENT."
                    );
                }
            }
            default -> {
                // secret.read, secret.create, secret.update, secret.delete, secret.reveal, secret.rollback, jit.request are valid across appropriate scopes
            }
        }
    }

    private Pageable sanitizePageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        if (pageSize <= 0) pageSize = 20;

        int pageNumber = Math.max(pageable.getPageNumber(), 0);

        Sort safeSort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (pageable.getSort().isSorted()) {
            List<Sort.Order> safeOrders = new ArrayList<>();
            for (Sort.Order order : pageable.getSort()) {
                if (ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    safeOrders.add(order);
                }
            }
            if (!safeOrders.isEmpty()) {
                safeSort = Sort.by(safeOrders);
            }
        }

        return PageRequest.of(pageNumber, pageSize, safeSort);
    }

    private AccessGrantResponse mapToResponse(AccessGrant grant) {
        User user = userRepository.findById(grant.getUserId()).orElse(null);
        String userEmail = user != null ? user.getEmail() : "unknown";
        String userFullName = user != null ? user.getFullName() : "Unknown User";

        String projectName = grant.getProjectId() != null ? projectRepository.findById(grant.getProjectId()).map(Project::getName).orElse(null) : null;
        String envName = grant.getEnvironmentId() != null ? environmentRepository.findById(grant.getEnvironmentId()).map(Environment::getName).orElse(null) : null;
        String secretName = grant.getSecretId() != null ? secretRepository.findById(grant.getSecretId()).map(Secret::getName).orElse(null) : null;

        return AccessGrantResponse.fromEntity(grant, userEmail, userFullName, projectName, envName, secretName);
    }
}
