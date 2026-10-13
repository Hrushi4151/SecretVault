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
import com.secretvault.events.model.EventSeverity;
import com.secretvault.events.model.EventType;
import com.secretvault.events.publisher.EventPublisher;
import com.secretvault.machine.repository.MachineAccessGrantRepository;
import com.secretvault.oidc.repository.OidcTrustPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MachineIdentityService {

    private static final Logger log = LoggerFactory.getLogger(MachineIdentityService.class);

    private final MachineIdentityRepository machineRepository;
    private final MachineSessionRepository sessionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AuditService auditService;
    private final MachineSessionService sessionService;
    private final MachineAccessGrantRepository grantRepository;
    private final OidcTrustPolicyRepository policyRepository;

    @Autowired(required = false)
    private EventPublisher eventPublisher;

    public void setEventPublisher(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public MachineIdentityService(
            MachineIdentityRepository machineRepository,
            MachineSessionRepository sessionRepository,
            WorkspaceRepository workspaceRepository,
            AuditService auditService
    ) {
        this(machineRepository, sessionRepository, workspaceRepository, auditService, null, null, null);
    }

    @Autowired
    public MachineIdentityService(
            MachineIdentityRepository machineRepository,
            MachineSessionRepository sessionRepository,
            WorkspaceRepository workspaceRepository,
            AuditService auditService,
            @Autowired(required = false) MachineSessionService sessionService,
            @Autowired(required = false) MachineAccessGrantRepository grantRepository,
            @Autowired(required = false) OidcTrustPolicyRepository policyRepository
    ) {
        this.machineRepository = machineRepository;
        this.sessionRepository = sessionRepository;
        this.workspaceRepository = workspaceRepository;
        this.auditService = auditService;
        this.sessionService = sessionService;
        this.grantRepository = grantRepository;
        this.policyRepository = policyRepository;
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

        if (eventPublisher != null) {
            eventPublisher.publish(
                    EventType.MACHINE_CREATED,
                    workspaceId,
                    "MACHINE_IDENTITY",
                    saved.getId().toString(),
                    EventSeverity.INFO,
                    Map.of(
                            "machineId", saved.getId().toString(),
                            "machineName", saved.getName(),
                            "role", saved.getType() != null ? saved.getType().name() : "CI_CD",
                            "status", saved.getStatus().name()
                    )
            );
        }

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

        if (eventPublisher != null) {
            eventPublisher.publish(
                    EventType.MACHINE_DISABLED,
                    workspaceId,
                    "MACHINE_IDENTITY",
                    saved.getId().toString(),
                    EventSeverity.MEDIUM,
                    Map.of(
                            "machineId", saved.getId().toString(),
                            "machineName", saved.getName(),
                            "status", saved.getStatus().name()
                    )
            );
        }

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

        if (eventPublisher != null) {
            eventPublisher.publish(
                    EventType.MACHINE_REVOKED,
                    workspaceId,
                    "MACHINE_IDENTITY",
                    saved.getId().toString(),
                    EventSeverity.HIGH,
                    Map.of(
                            "machineId", saved.getId().toString(),
                            "machineName", saved.getName(),
                            "status", saved.getStatus().name()
                    )
            );
        }

        return MachineDtos.MachineIdentityResponse.fromEntity(saved);
    }

    @Transactional
    public MachineDtos.GenerateMachineTokenResponse generateDirectToken(
            UUID workspaceId,
            UUID machineId,
            MachineDtos.GenerateMachineTokenRequest request,
            String sourceIp,
            String userAgent,
            UUID actorId
    ) {
        if (sessionService == null) {
            throw ApiException.internalServerError("Machine session service is unavailable");
        }

        MachineIdentity machine = machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(machineId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Machine identity not found in this workspace"));

        if (!machine.isUsable(Instant.now())) {
            throw ApiException.forbidden("Cannot generate token: machine identity is " + machine.getStatus());
        }

        long ttlSeconds = (request != null && request.ttlSeconds() != null && request.ttlSeconds() > 0)
                ? request.ttlSeconds()
                : 2592000L; // default 30 days for service / direct token

        MachineSessionService.MachineTokenResult result = sessionService.createSession(
                workspaceId,
                machineId,
                null,
                sourceIp,
                userAgent,
                Map.of(
                        "description", request != null && request.description() != null ? request.description() : "Direct token generation",
                        "generatedBy", actorId != null ? actorId.toString() : "SYSTEM"
                ),
                ttlSeconds
        );

        return new MachineDtos.GenerateMachineTokenResponse(
                result.rawToken(),
                result.tokenPrefix(),
                result.expiresAt(),
                result.expiresInSeconds(),
                MachineDtos.MachineIdentityResponse.fromEntity(machine)
        );
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

        if (policyRepository != null) {
            try {
                policyRepository.deleteByMachineIdentityId(id);
            } catch (Exception e) {
                log.warn("Could not delete trust policies for machine identity [{}]: {}", id, e.getMessage());
            }
        }

        if (grantRepository != null) {
            try {
                grantRepository.deleteByMachineIdentityId(id);
            } catch (Exception e) {
                log.warn("Could not delete grants for machine identity [{}]: {}", id, e.getMessage());
            }
        }

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
