package com.secretvault.machine.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.machine.dto.MachineDtos;
import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.model.MachineStatus;
import com.secretvault.machine.model.MachineType;
import com.secretvault.machine.repository.MachineIdentityRepository;
import com.secretvault.machine.repository.MachineSessionRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MachineIdentityService {

    private static final Logger log = LoggerFactory.getLogger(MachineIdentityService.class);

    private final MachineIdentityRepository machineRepository;
    private final MachineSessionRepository sessionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AuditService auditService;

    public MachineIdentityService(
            MachineIdentityRepository machineRepository,
            MachineSessionRepository sessionRepository,
            WorkspaceRepository workspaceRepository,
            AuditService auditService
    ) {
        this.machineRepository = machineRepository;
        this.sessionRepository = sessionRepository;
        this.workspaceRepository = workspaceRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MachineDtos.MachineIdentityResponse createMachineIdentity(
            UUID workspaceId,
            MachineDtos.CreateMachineIdentityRequest request,
            UUID actorId
    ) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw ApiException.notFound("Workspace not found");
        }

        String normalizedName = request.name().trim();
        if (machineRepository.existsByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(workspaceId, normalizedName)) {
            throw ApiException.conflict("Machine identity with name '" + normalizedName + "' already exists in this workspace");
        }

        if (request.expiresAt() != null && request.expiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Expiration time must be in the future");
        }

        MachineIdentity identity = new MachineIdentity(
                workspaceId,
                normalizedName,
                request.description(),
                request.type() != null ? request.type() : MachineType.CI_CD,
                request.expiresAt(),
                actorId
        );
        identity.setMetadata(request.metadata());

        MachineIdentity saved = machineRepository.save(identity);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.MACHINE_IDENTITY_CREATED,
                "MACHINE_IDENTITY",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.info("Created machine identity [{}] (ID: {}) in workspace [{}]", saved.getName(), saved.getId(), workspaceId);
        return MachineDtos.MachineIdentityResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public MachineDtos.MachineIdentityResponse getMachineIdentity(UUID id, UUID workspaceId) {
        MachineIdentity identity = machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Machine identity not found in this workspace"));

        // Check if identity has expired
        if (identity.getStatus() == MachineStatus.ACTIVE && identity.isExpired(Instant.now())) {
            identity.setStatus(MachineStatus.EXPIRED);
        }

        return MachineDtos.MachineIdentityResponse.fromEntity(identity);
    }

    @Transactional(readOnly = true)
    public List<MachineDtos.MachineIdentityResponse> listMachineIdentities(UUID workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw ApiException.notFound("Workspace not found");
        }

        List<MachineIdentity> list = machineRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId);
        Instant now = Instant.now();
        return list.stream()
                .map(m -> {
                    if (m.getStatus() == MachineStatus.ACTIVE && m.isExpired(now)) {
                        m.setStatus(MachineStatus.EXPIRED);
                    }
                    return MachineDtos.MachineIdentityResponse.fromEntity(m);
                })
                .toList();
    }

    @Transactional
    public MachineDtos.MachineIdentityResponse updateMachineIdentity(
            UUID id,
            UUID workspaceId,
            MachineDtos.UpdateMachineIdentityRequest request,
            UUID actorId
    ) {
        MachineIdentity identity = machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Machine identity not found in this workspace"));

        if (identity.getStatus() == MachineStatus.REVOKED) {
            throw ApiException.badRequest("Cannot modify a revoked machine identity");
        }

        if (request.name() != null && !request.name().isBlank()) {
            String newName = request.name().trim();
            if (!newName.equalsIgnoreCase(identity.getName()) &&
                    machineRepository.existsByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(workspaceId, newName)) {
                throw ApiException.conflict("Machine identity with name '" + newName + "' already exists in this workspace");
            }
            identity.setName(newName);
        }

        if (request.description() != null) {
            identity.setDescription(request.description());
        }
        if (request.type() != null) {
            identity.setType(request.type());
        }
        if (request.expiresAt() != null) {
            if (request.expiresAt().isBefore(Instant.now())) {
                throw ApiException.badRequest("Expiration time must be in the future");
            }
            identity.setExpiresAt(request.expiresAt());
            if (identity.getStatus() == MachineStatus.EXPIRED) {
                identity.setStatus(MachineStatus.ACTIVE);
            }
        }
        if (request.metadata() != null) {
            identity.setMetadata(request.metadata());
        }

        identity.setUpdatedAt(Instant.now());
        MachineIdentity saved = machineRepository.save(identity);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.MACHINE_IDENTITY_UPDATED,
                "MACHINE_IDENTITY",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        return MachineDtos.MachineIdentityResponse.fromEntity(saved);
    }

    @Transactional
    public MachineDtos.MachineIdentityResponse disableMachineIdentity(UUID id, UUID workspaceId, UUID actorId) {
        MachineIdentity identity = machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Machine identity not found in this workspace"));

        if (identity.getStatus() == MachineStatus.REVOKED) {
            throw ApiException.badRequest("Cannot disable a revoked machine identity");
        }

        identity.setStatus(MachineStatus.DISABLED);
        identity.setDisabledAt(Instant.now());
        identity.setUpdatedAt(Instant.now());

        // Revoke all active sessions immediately
        sessionRepository.revokeAllByWorkspaceAndMachine(workspaceId, id, Instant.now());

        MachineIdentity saved = machineRepository.save(identity);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.MACHINE_IDENTITY_DISABLED,
                "MACHINE_IDENTITY",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.info("Disabled machine identity [{}] (ID: {}) in workspace [{}]", saved.getName(), saved.getId(), workspaceId);
        return MachineDtos.MachineIdentityResponse.fromEntity(saved);
    }

    @Transactional
    public MachineDtos.MachineIdentityResponse enableMachineIdentity(UUID id, UUID workspaceId, UUID actorId) {
        MachineIdentity identity = machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Machine identity not found in this workspace"));

        if (identity.getStatus() == MachineStatus.REVOKED) {
            throw ApiException.badRequest("Revoked machine identities cannot be re-enabled");
        }

        if (identity.isExpired(Instant.now())) {
            throw ApiException.badRequest("Cannot enable an expired machine identity without extending expiration date");
        }

        identity.setStatus(MachineStatus.ACTIVE);
        identity.setDisabledAt(null);
        identity.setUpdatedAt(Instant.now());

        MachineIdentity saved = machineRepository.save(identity);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.MACHINE_IDENTITY_ENABLED,
                "MACHINE_IDENTITY",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.info("Enabled machine identity [{}] (ID: {}) in workspace [{}]", saved.getName(), saved.getId(), workspaceId);
        return MachineDtos.MachineIdentityResponse.fromEntity(saved);
    }

    @Transactional
    public MachineDtos.MachineIdentityResponse revokeMachineIdentity(UUID id, UUID workspaceId, UUID actorId) {
        MachineIdentity identity = machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Machine identity not found in this workspace"));

        identity.setStatus(MachineStatus.REVOKED);
        identity.setRevokedAt(Instant.now());
        identity.setUpdatedAt(Instant.now());

        // Revoke all active sessions
        sessionRepository.revokeAllByWorkspaceAndMachine(workspaceId, id, Instant.now());

        MachineIdentity saved = machineRepository.save(identity);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.MACHINE_IDENTITY_REVOKED,
                "MACHINE_IDENTITY",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.warn("Revoked machine identity [{}] (ID: {}) in workspace [{}]", saved.getName(), saved.getId(), workspaceId);
        return MachineDtos.MachineIdentityResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteMachineIdentity(UUID id, UUID workspaceId, UUID actorId) {
        MachineIdentity identity = machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Machine identity not found in this workspace"));

        // Soft delete to preserve audit history and governance trails
        identity.setDeletedAt(Instant.now());
        identity.setStatus(MachineStatus.REVOKED);
        identity.setUpdatedAt(Instant.now());

        sessionRepository.revokeAllByWorkspaceAndMachine(workspaceId, id, Instant.now());
        machineRepository.save(identity);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.MACHINE_IDENTITY_DELETED,
                "MACHINE_IDENTITY",
                id,
                null,
                null,
                "SUCCESS"
        );

        log.info("Soft-deleted machine identity [{}] (ID: {}) in workspace [{}]", identity.getName(), id, workspaceId);
    }
}
