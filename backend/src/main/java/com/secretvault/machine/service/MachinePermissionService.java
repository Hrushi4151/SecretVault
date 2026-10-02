package com.secretvault.machine.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.machine.dto.MachineDtos;
import com.secretvault.machine.entity.MachineAccessGrant;
import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.repository.MachineAccessGrantRepository;
import com.secretvault.machine.repository.MachineIdentityRepository;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.repository.SecretRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MachinePermissionService {

    private static final Logger log = LoggerFactory.getLogger(MachinePermissionService.class);

    private final MachineAccessGrantRepository grantRepository;
    private final MachineIdentityRepository machineRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final SecretRepository secretRepository;
    private final AuditService auditService;

    public MachinePermissionService(
            MachineAccessGrantRepository grantRepository,
            MachineIdentityRepository machineRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            SecretRepository secretRepository,
            AuditService auditService
    ) {
        this.grantRepository = grantRepository;
        this.machineRepository = machineRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.secretRepository = secretRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MachineDtos.MachineGrantResponse addGrant(
            UUID workspaceId,
            UUID machineIdentityId,
            MachineDtos.CreateMachineGrantRequest request,
            UUID actorId
    ) {
        MachineIdentity machine = machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(machineIdentityId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Machine identity not found in this workspace"));

        AccessScope scopeType = request.scopeType();
        UUID projectId = request.projectId();
        UUID environmentId = request.environmentId();
        UUID secretId = request.secretId();
        String secretPattern = request.secretPattern();

        // Validate Scope Invariants
        if (scopeType == AccessScope.PROJECT) {
            if (projectId == null) throw ApiException.badRequest("Project ID is required for PROJECT scoped grant");
            if (projectRepository.findByIdAndWorkspaceId(projectId, workspaceId).isEmpty()) {
                throw ApiException.notFound("Project not found in this workspace");
            }
        } else if (scopeType == AccessScope.ENVIRONMENT) {
            if (projectId == null || environmentId == null) {
                throw ApiException.badRequest("Project ID and Environment ID are required for ENVIRONMENT scoped grant");
            }
            if (environmentRepository.findByIdAndProjectId(environmentId, projectId).isEmpty()) {
                throw ApiException.notFound("Environment not found in this project");
            }
        } else if (scopeType == AccessScope.SECRET) {
            if (projectId == null || environmentId == null) {
                throw ApiException.badRequest("Project ID and Environment ID are required for SECRET scoped grant");
            }
            if (secretId != null) {
                var secretOpt = secretRepository.findById(secretId);
                if (secretOpt.isEmpty() || !secretOpt.get().getEnvironmentId().equals(environmentId)) {
                    throw ApiException.notFound("Secret not found in this environment");
                }
            }
        }

        String effect = request.effect() != null ? request.effect().toUpperCase() : "ALLOW";
        if (!"ALLOW".equals(effect) && !"DENY".equals(effect)) {
            effect = "ALLOW";
        }

        String permissionCode = request.permission().trim().toLowerCase();

        MachineAccessGrant grant = new MachineAccessGrant(
                workspaceId,
                machineIdentityId,
                scopeType,
                projectId,
                environmentId,
                secretId,
                secretPattern,
                permissionCode,
                effect,
                actorId
        );

        MachineAccessGrant saved = grantRepository.save(grant);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.ACCESS_GRANT_CREATED,
                "MACHINE_ACCESS_GRANT",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.info("Granted machine permission [{}] (effect: {}) to machine [{}] at scope [{}]",
                permissionCode, effect, machine.getName(), scopeType);

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<MachineDtos.MachineGrantResponse> listGrants(UUID workspaceId, UUID machineIdentityId) {
        if (!machineRepository.existsByIdAndWorkspaceIdAndDeletedAtIsNull(machineIdentityId, workspaceId)) {
            throw ApiException.notFound("Machine identity not found in this workspace");
        }

        return grantRepository.findByWorkspaceIdAndMachineIdentityId(workspaceId, machineIdentityId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void removeGrant(UUID workspaceId, UUID machineIdentityId, UUID grantId, UUID actorId) {
        MachineAccessGrant grant = grantRepository.findByIdAndWorkspaceId(grantId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Machine access grant not found"));

        if (!grant.getMachineIdentityId().equals(machineIdentityId)) {
            throw ApiException.badRequest("Grant does not belong to specified machine identity");
        }

        grantRepository.delete(grant);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.ACCESS_GRANT_REVOKED,
                "MACHINE_ACCESS_GRANT",
                grantId,
                null,
                null,
                "SUCCESS"
        );

        log.info("Revoked machine access grant [{}] from machine ID [{}]", grantId, machineIdentityId);
    }

    private MachineDtos.MachineGrantResponse toDto(MachineAccessGrant g) {
        return new MachineDtos.MachineGrantResponse(
                g.getId(),
                g.getWorkspaceId(),
                g.getMachineIdentityId(),
                g.getScopeType(),
                g.getProjectId(),
                g.getEnvironmentId(),
                g.getSecretId(),
                g.getSecretPattern(),
                g.getPermission(),
                g.getEffect(),
                g.getGrantedBy(),
                g.getCreatedAt(),
                g.getUpdatedAt()
        );
    }
}
