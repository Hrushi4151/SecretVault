package com.secretvault.incident.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventSeverity;
import com.secretvault.events.model.EventType;
import com.secretvault.events.publisher.EventPublisher;
import com.secretvault.incident.dto.IncidentDtos.IncidentRemediationRequest;
import com.secretvault.incident.dto.IncidentDtos.IncidentRemediationResult;
import com.secretvault.incident.dto.IncidentDtos.RemediationPlaybookAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.incident.dto.IncidentDtos.*;
import com.secretvault.incident.entity.IncidentApprovalStatus;
import com.secretvault.incident.entity.IncidentRelationshipType;
import com.secretvault.incident.entity.IncidentRemediationStatus;
import com.secretvault.incident.entity.IncidentSeverity;
import com.secretvault.incident.entity.IncidentStatus;
import com.secretvault.incident.entity.SecurityIncident;
import com.secretvault.incident.entity.SecurityIncidentApproval;
import com.secretvault.incident.entity.SecurityIncidentEvent;
import com.secretvault.incident.entity.SecurityIncidentRemediation;
import com.secretvault.incident.repository.SecurityIncidentApprovalRepository;
import com.secretvault.incident.repository.SecurityIncidentEventRepository;
import com.secretvault.incident.repository.SecurityIncidentRemediationRepository;
import com.secretvault.incident.repository.SecurityIncidentRepository;
import com.secretvault.machine.service.MachineIdentityService;
import com.secretvault.events.model.BaseDomainEvent;
import com.secretvault.notification.entity.NotificationSeverity;
import com.secretvault.notification.service.NotificationService;
import com.secretvault.rotation.dto.RotationDtos.RotationJobResponse;
import com.secretvault.rotation.dto.RotationDtos.SecretLeaseResponse;
import com.secretvault.rotation.dto.RotationDtos.TriggerRotationRequest;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.service.RotationService;
import com.secretvault.rotation.service.SecretLeaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

import com.secretvault.incident.event.IncidentRemediationQueuedEvent;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class SecurityIncidentService {

    private static final Logger log = LoggerFactory.getLogger(SecurityIncidentService.class);

    private final SecurityIncidentRepository incidentRepository;
    private final SecurityIncidentEventRepository incidentEventRepository;
    private final EventPublisher eventPublisher;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;
    private final RotationService rotationService;
    private final SecretLeaseService leaseService;
    private final MachineIdentityService machineService;
    private final NotificationService notificationService;
    private final SecurityIncidentApprovalRepository approvalRepository;
    private final SecurityIncidentRemediationRepository remediationRepository;
    private final ObjectMapper objectMapper;
    private ApplicationEventPublisher applicationEventPublisher;
    private boolean enforceTwoPersonApprovalDefault = false;

    @Autowired(required = false)
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void setEnforceTwoPersonApprovalDefault(boolean enforce) {
        this.enforceTwoPersonApprovalDefault = enforce;
    }

    public SecurityIncidentService(
            SecurityIncidentRepository incidentRepository,
            SecurityIncidentEventRepository incidentEventRepository,
            EventPublisher eventPublisher,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService
    ) {
        this(
                incidentRepository,
                incidentEventRepository,
                eventPublisher,
                effectiveAccessService,
                auditService,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public SecurityIncidentService(
            SecurityIncidentRepository incidentRepository,
            SecurityIncidentEventRepository incidentEventRepository,
            EventPublisher eventPublisher,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService,
            RotationService rotationService,
            SecretLeaseService leaseService,
            MachineIdentityService machineService,
            NotificationService notificationService
    ) {
        this(
                incidentRepository,
                incidentEventRepository,
                eventPublisher,
                effectiveAccessService,
                auditService,
                rotationService,
                leaseService,
                machineService,
                notificationService,
                null,
                null,
                null
        );
    }

    @Autowired
    public SecurityIncidentService(
            SecurityIncidentRepository incidentRepository,
            SecurityIncidentEventRepository incidentEventRepository,
            EventPublisher eventPublisher,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService,
            @Autowired(required = false) RotationService rotationService,
            @Autowired(required = false) SecretLeaseService leaseService,
            @Autowired(required = false) MachineIdentityService machineService,
            @Autowired(required = false) NotificationService notificationService,
            @Autowired(required = false) SecurityIncidentApprovalRepository approvalRepository,
            @Autowired(required = false) SecurityIncidentRemediationRepository remediationRepository,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.incidentRepository = incidentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.eventPublisher = eventPublisher;
        this.effectiveAccessService = effectiveAccessService;
        this.auditService = auditService;
        this.rotationService = rotationService;
        this.leaseService = leaseService;
        this.machineService = machineService;
        this.notificationService = notificationService;
        this.approvalRepository = approvalRepository;
        this.remediationRepository = remediationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SecurityIncident createIncident(
            UUID workspaceId,
            String title,
            String description,
            IncidentSeverity severity,
            String category,
            UUID sourceEventId,
            UUID actorId
    ) {
        if (workspaceId == null) {
            throw ApiException.badRequest("Workspace ID is required for incident creation");
        }

        verifySecurityManageAccess(workspaceId, actorId);

        long count = incidentRepository.countByWorkspaceId(workspaceId);
        String incidentNumber = String.format("INC-%04d", count + 1);
        int suffix = 1;
        while (incidentRepository.findByWorkspaceIdAndIncidentNumber(workspaceId, incidentNumber).isPresent()) {
            incidentNumber = String.format("INC-%04d", count + 1 + suffix);
            suffix++;
        }

        SecurityIncident incident = new SecurityIncident();
        incident.setWorkspaceId(workspaceId);
        incident.setIncidentNumber(incidentNumber);
        incident.setTitle(title != null ? title : "Security Incident " + incidentNumber);
        incident.setDescription(description != null ? description : "Automated security incident created from event");
        incident.setSeverity(severity != null ? severity : IncidentSeverity.HIGH);
        incident.setCategory(category != null ? category : "SECURITY_ANOMALY");
        incident.setStatus(IncidentStatus.OPEN);
        incident.setSourceEventId(sourceEventId);
        incident.setCreatedBy(actorId);

        SecurityIncident saved = incidentRepository.save(incident);

        if (sourceEventId != null) {
            correlateEvent(saved.getId(), sourceEventId, IncidentRelationshipType.ROOT_CAUSE, "Initial triggering event");
        }

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                actorId != null ? "USER" : "SYSTEM",
                AuditAction.SECURITY_INCIDENT_CREATED,
                "SECURITY_INCIDENT",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        // Publish domain event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("incidentNumber", saved.getIncidentNumber());
        metadata.put("severity", saved.getSeverity().name());
        metadata.put("category", saved.getCategory());

        eventPublisher.publish(
                EventType.SECURITY_INCIDENT_CREATED,
                workspaceId,
                "SECURITY_INCIDENT",
                saved.getId().toString(),
                EventSeverity.CRITICAL,
                metadata
        );

        log.info("Created Security Incident [{}] (ID: {}) in workspace [{}]", saved.getIncidentNumber(), saved.getId(), workspaceId);
        return saved;
    }

    @Transactional
    public void correlateEvent(UUID incidentId, UUID eventId, IncidentRelationshipType relType, String notes) {
        if (incidentId == null || eventId == null) return;
        if (incidentEventRepository.existsByIncidentIdAndEventId(incidentId, eventId)) {
            return;
        }

        SecurityIncidentEvent incidentEvent = new SecurityIncidentEvent(
                incidentId,
                eventId,
                relType != null ? relType : IncidentRelationshipType.CORRELATED,
                notes
        );
        incidentEventRepository.save(incidentEvent);
    }

    @Transactional
    public SecurityIncident updateIncidentStatus(
            UUID workspaceId,
            UUID incidentId,
            IncidentStatus newStatus,
            String resolutionSummary,
            UUID actorId
    ) {
        verifySecurityManageAccess(workspaceId, actorId);

        SecurityIncident incident = incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Security incident not found in this workspace"));

        IncidentStatus oldStatus = incident.getStatus();
        validateStatusTransition(oldStatus, newStatus);

        if ((newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.CLOSED)
                && (resolutionSummary == null || resolutionSummary.trim().isEmpty())) {
            throw ApiException.badRequest("Resolution summary is required when resolving or closing an incident");
        }

        incident.setStatus(newStatus);
        if (newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.CLOSED) {
            incident.setResolvedAt(Instant.now());
            incident.setResolvedBy(actorId);
            incident.setResolutionSummary(resolutionSummary.trim());
        } else if (oldStatus == IncidentStatus.RESOLVED || oldStatus == IncidentStatus.CLOSED) {
            // Reopening incident
            incident.setResolvedAt(null);
            incident.setResolvedBy(null);
            if (resolutionSummary != null && !resolutionSummary.isBlank()) {
                incident.setResolutionSummary("[Reopened] " + resolutionSummary.trim());
            }
        }

        SecurityIncident saved = incidentRepository.save(incident);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                actorId != null ? "USER" : "SYSTEM",
                newStatus == IncidentStatus.RESOLVED ? AuditAction.SECURITY_INCIDENT_RESOLVED : AuditAction.SECURITY_INCIDENT_UPDATED,
                "SECURITY_INCIDENT",
                saved.getId(),
                null,
                null,
                "Status changed from " + oldStatus + " to " + newStatus
        );

        // Publish domain event
        EventType evtType = (newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.CLOSED)
                ? EventType.SECURITY_INCIDENT_RESOLVED
                : EventType.SECURITY_INCIDENT_UPDATED;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("incidentNumber", saved.getIncidentNumber());
        metadata.put("oldStatus", oldStatus.name());
        metadata.put("newStatus", newStatus.name());
        if (resolutionSummary != null) metadata.put("resolutionSummary", resolutionSummary);

        eventPublisher.publish(
                evtType,
                workspaceId,
                "SECURITY_INCIDENT",
                saved.getId().toString(),
                EventSeverity.HIGH,
                metadata
        );

        return saved;
    }

    @Transactional
    public IncidentRemediationResult remediateIncident(
            UUID workspaceId,
            UUID incidentId,
            IncidentRemediationRequest request,
            UUID actorId
    ) {
        verifySecurityManageAccess(workspaceId, actorId);

        SecurityIncident incident = incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Security incident not found in this workspace"));

        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw ApiException.badRequest("Cannot execute remediation on a CLOSED incident");
        }

        if (request == null || request.action() == null) {
            throw ApiException.badRequest("Remediation action is required");
        }

        RemediationPlaybookAction action = request.action();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("incidentNumber", incident.getIncidentNumber());
        metadata.put("action", action.name());

        // Validate target parameters and resource boundaries for BOTH dry-run and live execution
        switch (action) {
            case ROTATE_SECRET -> {
                UUID secretId = request.targetSecretId();
                if (secretId == null) {
                    throw ApiException.badRequest("targetSecretId is required for ROTATE_SECRET action");
                }
                verifySecretScopeAccess(workspaceId, secretId, actorId, AccessPermission.SECRET_ROTATION_EMERGENCY);
                metadata.put("secretId", secretId.toString());
            }
            case REVOKE_SECRET_LEASES -> {
                UUID secretId = request.targetSecretId();
                if (secretId == null) {
                    throw ApiException.badRequest("targetSecretId is required for REVOKE_SECRET_LEASES action");
                }
                verifySecretScopeAccess(workspaceId, secretId, actorId, AccessPermission.SECRET_LEASE_MANAGE);
                metadata.put("secretId", secretId.toString());
            }
            case DISABLE_MACHINE_IDENTITY -> {
                UUID machineId = request.targetMachineIdentityId();
                if (machineId == null) {
                    throw ApiException.badRequest("targetMachineIdentityId is required for DISABLE_MACHINE_IDENTITY action");
                }
                if (machineService != null) {
                    var machine = machineService.getMachineIdentity(machineId, workspaceId);
                    if (machine != null && machine.name() != null) {
                        metadata.put("machineName", machine.name());
                    }
                }
                metadata.put("machineIdentityId", machineId.toString());
            }
            case CONTAIN_INCIDENT, NOTIFY_RESPONDERS -> {
                // No target resource validation required
            }
        }

        if (request.dryRun()) {
            boolean requiresApproval = request.requireApproval() || (isHighImpactAction(action) && enforceTwoPersonApprovalDefault);
            metadata.put("requiresApproval", requiresApproval);
            return new IncidentRemediationResult(
                    action,
                    true,
                    false,
                    "DRY_RUN_PREVIEW",
                    "Dry-run preview: Playbook action " + action + " validated successfully without side effects." +
                            (requiresApproval ? " Note: Live execution will require two-person approval." : ""),
                    incident.getStatus(),
                    metadata
            );
        }

        String sanitizedNotes = BaseDomainEvent.sanitizeHexTokensInText(request.notes());

        // 1. Two-Person Approval Check
        boolean requiresApproval = request.requireApproval() || (isHighImpactAction(action) && enforceTwoPersonApprovalDefault);
        if (requiresApproval && request.approvalId() == null) {
            String payloadHash = computeActionPayloadHash(action, request.targetSecretId(), request.targetMachineIdentityId(), request.notes());
            SecurityIncidentApproval approval = new SecurityIncidentApproval();
            approval.setWorkspaceId(workspaceId);
            approval.setIncidentId(incidentId);
            approval.setAction(action);
            approval.setTargetSecretId(request.targetSecretId());
            approval.setTargetMachineIdentityId(request.targetMachineIdentityId());
            approval.setNotes(sanitizedNotes);
            approval.setActionPayloadHash(payloadHash);
            approval.setStatus(IncidentApprovalStatus.PENDING);
            approval.setRequestedBy(actorId);
            approval.setExpiresAt(Instant.now().plus(java.time.Duration.ofHours(24)));

            SecurityIncidentApproval savedApproval = approval;
            if (approvalRepository != null) {
                savedApproval = approvalRepository.save(approval);
            }

            metadata.put("approvalId", savedApproval.getId().toString());
            metadata.put("status", "PENDING_APPROVAL");

            auditService.recordAudit(
                    null,
                    workspaceId,
                    actorId,
                    actorId != null ? "USER" : "SYSTEM",
                    AuditAction.SECURITY_INCIDENT_UPDATED,
                    "SECURITY_INCIDENT_APPROVAL",
                    savedApproval.getId(),
                    null,
                    null,
                    "Remediation approval requested for high-impact action [" + action + "]"
            );

            correlateEvent(
                    incident.getId(),
                    UUID.randomUUID(),
                    IncidentRelationshipType.REMEDIATION,
                    "Remediation approval requested for action [" + action + "] (Approval ID: " + savedApproval.getId() + ")"
            );

            eventPublisher.publish(
                    EventType.SECURITY_INCIDENT_UPDATED,
                    workspaceId,
                    "SECURITY_INCIDENT",
                    incident.getId().toString(),
                    EventSeverity.HIGH,
                    metadata
            );

            return new IncidentRemediationResult(
                    action,
                    false,
                    false,
                    "PENDING_APPROVAL",
                    "Remediation action " + action + " requires two-person approval before execution. Approval request created with ID " + savedApproval.getId(),
                    incident.getStatus(),
                    metadata,
                    savedApproval.getId(),
                    null
            );
        }

        SecurityIncidentApproval validatedApproval = null;
        if (request.approvalId() != null) {
            if (approvalRepository == null) {
                throw ApiException.badRequest("Approval repository unavailable");
            }
            validatedApproval = approvalRepository.findByIdAndIncidentIdAndWorkspaceId(request.approvalId(), incidentId, workspaceId)
                    .orElseThrow(() -> ApiException.notFound("Remediation approval not found in this workspace"));

            if (validatedApproval.getStatus() != IncidentApprovalStatus.APPROVED) {
                throw ApiException.badRequest("Remediation approval is not approved: current status is " + validatedApproval.getStatus());
            }

            if (validatedApproval.getExpiresAt().isBefore(Instant.now())) {
                validatedApproval.setStatus(IncidentApprovalStatus.EXPIRED);
                approvalRepository.save(validatedApproval);
                throw ApiException.badRequest("Remediation approval request has expired");
            }

            // Bind approval to exact action and target
            String currentHash = computeActionPayloadHash(action, request.targetSecretId(), request.targetMachineIdentityId(), request.notes());
            if (!currentHash.equals(validatedApproval.getActionPayloadHash())) {
                throw ApiException.badRequest("Remediation request parameters do not match approved action payload (payload hash mismatch)");
            }
            if (validatedApproval.getAction() != action ||
                    !Objects.equals(validatedApproval.getTargetSecretId(), request.targetSecretId()) ||
                    !Objects.equals(validatedApproval.getTargetMachineIdentityId(), request.targetMachineIdentityId())) {
                throw ApiException.badRequest("Remediation action or target resources do not match approved request");
            }

            // Re-validate authorization immediately before execution
            verifySecurityManageAccess(workspaceId, actorId);
            if (action == RemediationPlaybookAction.ROTATE_SECRET) {
                verifySecretScopeAccess(workspaceId, request.targetSecretId(), actorId, AccessPermission.SECRET_ROTATION_EMERGENCY);
            } else if (action == RemediationPlaybookAction.REVOKE_SECRET_LEASES) {
                verifySecretScopeAccess(workspaceId, request.targetSecretId(), actorId, AccessPermission.SECRET_LEASE_MANAGE);
            }
        }

        // 2. Asynchronous Execution Check
        if (request.async()) {
            SecurityIncidentRemediation remediation = new SecurityIncidentRemediation();
            remediation.setWorkspaceId(workspaceId);
            remediation.setIncidentId(incidentId);
            remediation.setApprovalId(request.approvalId());
            remediation.setAction(action);
            remediation.setTargetSecretId(request.targetSecretId());
            remediation.setTargetMachineIdentityId(request.targetMachineIdentityId());
            remediation.setNotes(sanitizedNotes);
            remediation.setActorId(actorId);
            remediation.setStatus(IncidentRemediationStatus.QUEUED);
            remediation.setAttemptCount(0);
            remediation.setMaxAttempts(3);

            Map<String, Object> requestedEffects = buildRequestedEffects(action, request, workspaceId, actorId);
            remediation.setRequestedEffectsJson(serializeToJson(requestedEffects));

            SecurityIncidentRemediation savedRemediation = remediation;
            if (remediationRepository != null) {
                savedRemediation = remediationRepository.save(remediation);
            }

            if (incident.getStatus() == IncidentStatus.OPEN || incident.getStatus() == IncidentStatus.INVESTIGATING) {
                incident.setStatus(IncidentStatus.REMEDIATION);
            }
            SecurityIncident savedIncident = incidentRepository.save(incident);

            metadata.put("executionId", savedRemediation.getId().toString());
            metadata.put("status", "QUEUED");

            correlateEvent(
                    savedIncident.getId(),
                    UUID.randomUUID(),
                    IncidentRelationshipType.REMEDIATION,
                    "Remediation action [" + action + "] queued for asynchronous worker execution (Task ID: " + savedRemediation.getId() + ")"
            );

            auditService.recordAudit(
                    null,
                    workspaceId,
                    actorId,
                    actorId != null ? "USER" : "SYSTEM",
                    AuditAction.SECURITY_INCIDENT_UPDATED,
                    "SECURITY_INCIDENT",
                    savedIncident.getId(),
                    null,
                    null,
                    "Remediation action [" + action + "] queued for asynchronous execution (Task ID: " + savedRemediation.getId() + ")"
            );

            eventPublisher.publish(
                    EventType.SECURITY_INCIDENT_UPDATED,
                    workspaceId,
                    "SECURITY_INCIDENT",
                    savedIncident.getId().toString(),
                    EventSeverity.HIGH,
                    metadata
            );

            if (applicationEventPublisher != null) {
                applicationEventPublisher.publishEvent(new IncidentRemediationQueuedEvent(
                        savedRemediation.getId(),
                        workspaceId,
                        savedIncident.getId()
                ));
            }

            return new IncidentRemediationResult(
                    action,
                    false,
                    false,
                    "QUEUED",
                    "Remediation action " + action + " queued for asynchronous execution with task ID " + savedRemediation.getId(),
                    savedIncident.getStatus(),
                    metadata,
                    request.approvalId(),
                    savedRemediation.getId()
            );
        }

        // 3. Synchronous Live Execution
        ExecutionOutcome outcome = executePlaybookAction(incident, action, request, actorId, workspaceId, metadata, sanitizedNotes);

        if (validatedApproval != null && approvalRepository != null) {
            validatedApproval.setStatus(IncidentApprovalStatus.EXECUTED);
            approvalRepository.save(validatedApproval);
        }

        // Advance status to REMEDIATION if currently OPEN or INVESTIGATING
        if (incident.getStatus() == IncidentStatus.OPEN || incident.getStatus() == IncidentStatus.INVESTIGATING) {
            incident.setStatus(IncidentStatus.REMEDIATION);
        }
        SecurityIncident savedIncident = incidentRepository.save(incident);

        // Record remediation event
        UUID remediationEventId = UUID.randomUUID();
        correlateEvent(
                savedIncident.getId(),
                remediationEventId,
                IncidentRelationshipType.REMEDIATION,
                "Remediation playbook action [" + action + "]: " + outcome.executionDetails()
        );

        // Record audit
        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                actorId != null ? "USER" : "SYSTEM",
                AuditAction.SECURITY_INCIDENT_UPDATED,
                "SECURITY_INCIDENT",
                savedIncident.getId(),
                null,
                null,
                "Remediation executed: " + action + ". " + outcome.executionDetails()
        );

        // Publish domain event
        eventPublisher.publish(
                EventType.SECURITY_INCIDENT_UPDATED,
                workspaceId,
                "SECURITY_INCIDENT",
                savedIncident.getId().toString(),
                EventSeverity.HIGH,
                metadata
        );

        String resultStatus = outcome.status() == IncidentRemediationStatus.COMPLETED ? "EXECUTED" : outcome.status().name();
        return new IncidentRemediationResult(
                action,
                false,
                outcome.status() != IncidentRemediationStatus.FAILED,
                resultStatus,
                outcome.executionDetails(),
                savedIncident.getStatus(),
                metadata,
                request.approvalId(),
                null
        );
    }

    private record ExecutionOutcome(
            IncidentRemediationStatus status,
            String executionDetails,
            String requestedEffectsJson,
            String verifiedEffectsJson
    ) {}

    private ExecutionOutcome executePlaybookAction(
            SecurityIncident incident,
            RemediationPlaybookAction action,
            IncidentRemediationRequest request,
            UUID actorId,
            UUID workspaceId,
            Map<String, Object> metadata,
            String sanitizedNotes
    ) {
        String executionDetails;
        IncidentRemediationStatus outcomeStatus;
        String requestedEffectsJson = serializeToJson(buildRequestedEffects(action, request, workspaceId, actorId));
        String verifiedEffectsJson = null;

        switch (action) {
            case ROTATE_SECRET -> {
                UUID secretId = request.targetSecretId();
                if (rotationService != null) {
                    TriggerRotationRequest rotReq = new TriggerRotationRequest(
                            RotationStrategy.EMERGENCY,
                            "Emergency incident remediation for " + incident.getIncidentNumber() + ": " + (sanitizedNotes != null ? sanitizedNotes : "Compromise contained"),
                            true,
                            true
                    );
                    String idempotencyKey = "INC-ROT-" + incident.getId() + "-" + secretId;
                    try {
                        RotationJobResponse jobResponse = rotationService.triggerRotation(workspaceId, secretId, rotReq, actorId, idempotencyKey);
                        if (jobResponse != null) {
                            if (jobResponse.id() != null) metadata.put("rotationJobId", jobResponse.id().toString());
                            if (jobResponse.status() != null) metadata.put("rotationStatus", jobResponse.status().name());
                            executionDetails = "Triggered EMERGENCY secret rotation for secret " + secretId +
                                    (jobResponse.id() != null ? " (Job ID: " + jobResponse.id() + ", Status: " + jobResponse.status() + ")" : "");
                            Map<String, Object> verified = new LinkedHashMap<>();
                            verified.put("rotationJobId", jobResponse.id() != null ? jobResponse.id().toString() : null);
                            verified.put("rotationStatus", jobResponse.status() != null ? jobResponse.status().name() : null);
                            verifiedEffectsJson = serializeToJson(verified);
                        } else {
                            executionDetails = "Triggered EMERGENCY secret rotation for secret " + secretId;
                            verifiedEffectsJson = "{\"status\":\"TRIGGERED\"}";
                        }
                        outcomeStatus = IncidentRemediationStatus.COMPLETED;
                    } catch (Exception ex) {
                        executionDetails = "Failed to trigger EMERGENCY rotation for secret " + secretId + ": " + ex.getMessage();
                        verifiedEffectsJson = "{\"error\":\"" + (ex.getMessage() != null ? ex.getMessage().replace("\"", "\\\"") : "error") + "\"}";
                        outcomeStatus = IncidentRemediationStatus.FAILED;
                    }
                } else {
                    executionDetails = "RotationService simulated: EMERGENCY rotation scheduled for secret " + secretId;
                    verifiedEffectsJson = "{\"simulated\":true}";
                    outcomeStatus = IncidentRemediationStatus.COMPLETED;
                }
            }
            case REVOKE_SECRET_LEASES -> {
                UUID secretId = request.targetSecretId();
                List<String> revokedLeaseIds = new ArrayList<>();
                Map<String, String> failedLeaseIds = new LinkedHashMap<>();
                int totalAttempted = 0;
                if (leaseService != null) {
                    var leasesPage = leaseService.listLeases(workspaceId, secretId, null, Pageable.unpaged(), actorId);
                    List<SecretLeaseResponse> leases = leasesPage != null ? leasesPage.getContent() : Collections.emptyList();
                    totalAttempted = leases.size();
                    for (SecretLeaseResponse lease : leases) {
                        try {
                            leaseService.revokeLease(workspaceId, lease.id(), actorId);
                            revokedLeaseIds.add(lease.id().toString());
                        } catch (Exception ex) {
                            log.error("Failed to revoke lease {} for secret {}: {}", lease.id(), secretId, ex.getMessage());
                            failedLeaseIds.put(lease.id().toString(), ex.getMessage());
                        }
                    }
                    metadata.put("revokedLeaseCount", revokedLeaseIds.size());
                    metadata.put("failedLeaseCount", failedLeaseIds.size());
                }

                Map<String, Object> verifiedEffects = new LinkedHashMap<>();
                verifiedEffects.put("totalAttempted", totalAttempted);
                verifiedEffects.put("revokedCount", revokedLeaseIds.size());
                verifiedEffects.put("revokedLeaseIds", revokedLeaseIds);
                verifiedEffects.put("failedCount", failedLeaseIds.size());
                verifiedEffects.put("failedLeaseIds", failedLeaseIds);
                verifiedEffectsJson = serializeToJson(verifiedEffects);

                if (failedLeaseIds.isEmpty()) {
                    outcomeStatus = IncidentRemediationStatus.COMPLETED;
                    executionDetails = revokedLeaseIds.isEmpty()
                            ? "No active leases found to revoke for secret " + secretId
                            : "Revoked " + revokedLeaseIds.size() + " active lease(s) for secret " + secretId;
                } else if (!revokedLeaseIds.isEmpty()) {
                    outcomeStatus = IncidentRemediationStatus.PARTIAL_FAILURE;
                    executionDetails = "Partially revoked " + revokedLeaseIds.size() + " of " + totalAttempted + " leases for secret " + secretId +
                            ". Failed to revoke " + failedLeaseIds.size() + " lease(s).";
                } else {
                    outcomeStatus = IncidentRemediationStatus.FAILED;
                    executionDetails = "Failed to revoke any of " + totalAttempted + " leases for secret " + secretId + ": " + failedLeaseIds.values();
                }
            }
            case DISABLE_MACHINE_IDENTITY -> {
                UUID machineId = request.targetMachineIdentityId();
                if (machineService != null) {
                    try {
                        var disabledResp = machineService.disableMachineIdentity(machineId, workspaceId, actorId);
                        if (disabledResp != null) {
                            if (disabledResp.name() != null) metadata.put("machineName", disabledResp.name());
                            if (disabledResp.status() != null) metadata.put("machineStatus", disabledResp.status().name());
                            executionDetails = "Disabled compromised machine identity " + (disabledResp.name() != null ? disabledResp.name() : machineId);
                            Map<String, Object> verified = new LinkedHashMap<>();
                            verified.put("machineIdentityId", machineId.toString());
                            verified.put("machineName", disabledResp.name());
                            verified.put("machineStatus", disabledResp.status() != null ? disabledResp.status().name() : null);
                            verifiedEffectsJson = serializeToJson(verified);
                        } else {
                            executionDetails = "Disabled compromised machine identity " + machineId;
                            verifiedEffectsJson = "{\"machineIdentityId\":\"" + machineId + "\",\"disabled\":true}";
                        }
                        outcomeStatus = IncidentRemediationStatus.COMPLETED;
                    } catch (Exception ex) {
                        executionDetails = "Failed to disable machine identity " + machineId + ": " + ex.getMessage();
                        verifiedEffectsJson = "{\"error\":\"" + (ex.getMessage() != null ? ex.getMessage().replace("\"", "\\\"") : "error") + "\"}";
                        outcomeStatus = IncidentRemediationStatus.FAILED;
                    }
                } else {
                    executionDetails = "MachineIdentityService simulated: Machine identity disabled " + machineId;
                    verifiedEffectsJson = "{\"simulated\":true}";
                    outcomeStatus = IncidentRemediationStatus.COMPLETED;
                }
            }
            case CONTAIN_INCIDENT -> {
                incident.setStatus(IncidentStatus.CONTAINED);
                executionDetails = "Incident blast radius marked CONTAINED by operator";
                verifiedEffectsJson = "{\"status\":\"CONTAINED\"}";
                outcomeStatus = IncidentRemediationStatus.COMPLETED;
            }
            case NOTIFY_RESPONDERS -> {
                if (notificationService != null) {
                    notificationService.createNotification(
                            workspaceId,
                            null,
                            NotificationSeverity.CRITICAL,
                            "INCIDENT ACTION: " + incident.getIncidentNumber(),
                            "Remediation action taken: " + (sanitizedNotes != null ? sanitizedNotes : "Incident investigation escalated"),
                            incident.getSourceEventId(),
                            null
                    );
                    executionDetails = "Dispatched high-priority alert to workspace responders";
                    verifiedEffectsJson = "{\"notified\":true}";
                } else {
                    executionDetails = "NotificationService simulated: Responders alerted";
                    verifiedEffectsJson = "{\"simulated\":true}";
                }
                outcomeStatus = IncidentRemediationStatus.COMPLETED;
            }
            default -> throw ApiException.badRequest("Unsupported remediation action: " + action);
        }

        return new ExecutionOutcome(outcomeStatus, executionDetails, requestedEffectsJson, verifiedEffectsJson);
    }

    private Map<String, Object> buildRequestedEffects(
            RemediationPlaybookAction action,
            IncidentRemediationRequest request,
            UUID workspaceId,
            UUID actorId
    ) {
        Map<String, Object> reqEffects = new LinkedHashMap<>();
        reqEffects.put("action", action.name());
        switch (action) {
            case ROTATE_SECRET -> {
                reqEffects.put("targetSecretId", request.targetSecretId() != null ? request.targetSecretId().toString() : null);
                reqEffects.put("strategy", RotationStrategy.EMERGENCY.name());
            }
            case REVOKE_SECRET_LEASES -> {
                UUID secretId = request.targetSecretId();
                reqEffects.put("targetSecretId", secretId != null ? secretId.toString() : null);
                List<String> discoveredLeaseIds = Collections.emptyList();
                if (leaseService != null && secretId != null) {
                    var leasesPage = leaseService.listLeases(workspaceId, secretId, null, Pageable.unpaged(), actorId);
                    if (leasesPage != null) {
                        discoveredLeaseIds = leasesPage.getContent().stream().map(l -> l.id().toString()).toList();
                    }
                }
                reqEffects.put("discoveredLeaseCount", discoveredLeaseIds.size());
                reqEffects.put("discoveredLeaseIds", discoveredLeaseIds);
            }
            case DISABLE_MACHINE_IDENTITY -> {
                reqEffects.put("targetMachineIdentityId", request.targetMachineIdentityId() != null ? request.targetMachineIdentityId().toString() : null);
            }
            case CONTAIN_INCIDENT -> {
                reqEffects.put("targetStatus", "CONTAINED");
            }
            case NOTIFY_RESPONDERS -> {
                reqEffects.put("severity", "CRITICAL");
            }
        }
        return reqEffects;
    }

    public static boolean isHighImpactAction(RemediationPlaybookAction action) {
        return action == RemediationPlaybookAction.ROTATE_SECRET ||
                action == RemediationPlaybookAction.REVOKE_SECRET_LEASES ||
                action == RemediationPlaybookAction.DISABLE_MACHINE_IDENTITY;
    }

    public static String computeActionPayloadHash(
            RemediationPlaybookAction action,
            UUID targetSecretId,
            UUID targetMachineIdentityId,
            String notes
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = (action != null ? action.name() : "") + "|"
                    + (targetSecretId != null ? targetSecretId.toString() : "") + "|"
                    + (targetMachineIdentityId != null ? targetMachineIdentityId.toString() : "") + "|"
                    + (notes != null ? notes.trim() : "");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private String serializeToJson(Object obj) {
        if (obj == null) return null;
        if (objectMapper != null) {
            try {
                return objectMapper.writeValueAsString(obj);
            } catch (Exception ignored) {}
        }
        return String.valueOf(obj);
    }

    @Transactional
    public SecurityIncidentApproval decideRemediationApproval(
            UUID workspaceId,
            UUID incidentId,
            UUID approvalId,
            boolean approve,
            String reason,
            UUID actorId
    ) {
        verifySecurityManageAccess(workspaceId, actorId);

        SecurityIncident incident = incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Security incident not found in this workspace"));

        if (approvalRepository == null) {
            throw ApiException.badRequest("Approval repository unavailable");
        }

        SecurityIncidentApproval approval = approvalRepository.findByIdAndIncidentIdAndWorkspaceId(approvalId, incidentId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Security incident approval not found"));

        if (approval.getStatus() != IncidentApprovalStatus.PENDING) {
            throw ApiException.badRequest("Approval request has already been decided: " + approval.getStatus());
        }

        if (approval.getExpiresAt().isBefore(Instant.now())) {
            approval.setStatus(IncidentApprovalStatus.EXPIRED);
            approvalRepository.save(approval);
            throw ApiException.badRequest("Approval request has expired");
        }

        // Anti-self-approval rule 1: Requester cannot approve their own action
        if (actorId != null && actorId.equals(approval.getRequestedBy())) {
            throw ApiException.forbidden("Anti-self-approval violation: Requester cannot approve their own remediation action (two-person rule)");
        }

        // Anti-self-approval rule 2: Incident creator cannot approve high-impact remediation action (separation-of-duties principle)
        if (actorId != null && incident.getCreatedBy() != null && actorId.equals(incident.getCreatedBy())) {
            throw ApiException.forbidden("Anti-self-approval violation: Incident creator cannot approve high-impact remediation action (separation-of-duties principle)");
        }

        approval.setStatus(approve ? IncidentApprovalStatus.APPROVED : IncidentApprovalStatus.REJECTED);
        approval.setDecidedBy(actorId);
        approval.setDecidedAt(Instant.now());
        approval.setDecisionReason(reason);
        SecurityIncidentApproval saved = approvalRepository.save(approval);

        // Record audit
        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                actorId != null ? "USER" : "SYSTEM",
                AuditAction.SECURITY_INCIDENT_UPDATED,
                "SECURITY_INCIDENT_APPROVAL",
                saved.getId(),
                null,
                null,
                "Remediation approval " + (approve ? "APPROVED" : "REJECTED") + " for action " + approval.getAction() +
                        (reason != null && !reason.isBlank() ? ". Reason: " + reason : "")
        );

        // Correlate event
        correlateEvent(
                incident.getId(),
                UUID.randomUUID(),
                IncidentRelationshipType.REMEDIATION,
                "Remediation approval [" + approval.getAction() + "] " + (approve ? "APPROVED" : "REJECTED") + " by operator" +
                        (reason != null && !reason.isBlank() ? " (" + reason + ")" : "")
        );

        // Publish domain event
        Map<String, Object> meta = new HashMap<>();
        meta.put("approvalId", saved.getId().toString());
        meta.put("incidentId", incident.getId().toString());
        meta.put("action", saved.getAction().name());
        meta.put("approved", approve);
        eventPublisher.publish(
                EventType.SECURITY_INCIDENT_UPDATED,
                workspaceId,
                "SECURITY_INCIDENT",
                incident.getId().toString(),
                EventSeverity.HIGH,
                meta
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<IncidentApprovalResponse> listIncidentApprovals(UUID workspaceId, UUID incidentId, UUID actorId) {
        verifySecurityViewAccess(workspaceId, actorId);
        if (approvalRepository == null) return Collections.emptyList();
        return approvalRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .map(IncidentApprovalResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentApprovalResponse getIncidentApproval(UUID workspaceId, UUID incidentId, UUID approvalId, UUID actorId) {
        verifySecurityViewAccess(workspaceId, actorId);
        if (approvalRepository == null) throw ApiException.notFound("Approval not found");
        return approvalRepository.findByIdAndIncidentIdAndWorkspaceId(approvalId, incidentId, workspaceId)
                .map(IncidentApprovalResponse::fromEntity)
                .orElseThrow(() -> ApiException.notFound("Remediation approval not found"));
    }

    @Transactional
    public IncidentRemediationExecutionResponse executeRemediationTask(UUID executionId, String workerId) {
        if (remediationRepository == null) {
            throw ApiException.badRequest("Remediation execution repository not available");
        }

        String claimToken = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant staleCutoff = now.minus(java.time.Duration.ofMinutes(5));

        int claimed = remediationRepository.claimRemediationAtomic(
                executionId,
                IncidentRemediationStatus.CLAIMED,
                IncidentRemediationStatus.QUEUED,
                staleCutoff,
                workerId != null ? workerId : "default-worker",
                claimToken,
                now
        );

        if (claimed == 0) {
            if (remediationRepository != null) {
                remediationRepository.findById(executionId).ifPresent(r -> {
                    if (r.getStatus() == IncidentRemediationStatus.QUEUED && r.getAttemptCount() >= r.getMaxAttempts()) {
                        r.setStatus(IncidentRemediationStatus.FAILED);
                        r.setLastError("Remediation failed: maximum retry attempts (" + r.getMaxAttempts() + ") exhausted");
                        remediationRepository.save(r);
                    }
                });
            }
            return remediationRepository.findById(executionId)
                    .map(IncidentRemediationExecutionResponse::fromEntity)
                    .orElseThrow(() -> ApiException.notFound("Remediation execution not found: " + executionId));
        }

        SecurityIncidentRemediation rem = remediationRepository.findById(executionId)
                .orElseThrow(() -> ApiException.notFound("Remediation execution not found: " + executionId));

        SecurityIncident incident = incidentRepository.findById(rem.getIncidentId())
                .orElseThrow(() -> ApiException.notFound("Security incident not found: " + rem.getIncidentId()));

        try {
            // Re-verify authorization immediately before execution
            verifySecurityManageAccess(rem.getWorkspaceId(), rem.getActorId());
            if (rem.getAction() == RemediationPlaybookAction.ROTATE_SECRET) {
                verifySecretScopeAccess(rem.getWorkspaceId(), rem.getTargetSecretId(), rem.getActorId(), AccessPermission.SECRET_ROTATION_EMERGENCY);
            } else if (rem.getAction() == RemediationPlaybookAction.REVOKE_SECRET_LEASES) {
                verifySecretScopeAccess(rem.getWorkspaceId(), rem.getTargetSecretId(), rem.getActorId(), AccessPermission.SECRET_LEASE_MANAGE);
            }

            IncidentRemediationRequest req = new IncidentRemediationRequest(
                    rem.getAction(),
                    rem.getTargetSecretId(),
                    rem.getTargetMachineIdentityId(),
                    rem.getNotes(),
                    false,
                    false,
                    rem.getApprovalId(),
                    false
            );

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("incidentNumber", incident.getIncidentNumber());
            metadata.put("action", rem.getAction().name());
            metadata.put("executionId", executionId.toString());

            ExecutionOutcome outcome = executePlaybookAction(incident, rem.getAction(), req, rem.getActorId(), rem.getWorkspaceId(), metadata, rem.getNotes());

            remediationRepository.completeRemediationGuarded(
                    executionId,
                    outcome.status(),
                    outcome.executionDetails(),
                    outcome.verifiedEffectsJson(),
                    claimToken,
                    Instant.now()
            );

            rem.setStatus(outcome.status());
            rem.setExecutionDetails(outcome.executionDetails());
            rem.setVerifiedEffectsJson(outcome.verifiedEffectsJson());
            rem.setCompletedAt(Instant.now());
            rem.setWorkerId(null);
            rem.setClaimToken(null);
            rem.setLockedAt(null);

            if (rem.getApprovalId() != null && approvalRepository != null) {
                approvalRepository.findById(rem.getApprovalId()).ifPresent(app -> {
                    app.setStatus(IncidentApprovalStatus.EXECUTED);
                    approvalRepository.save(app);
                });
            }

            if (incident.getStatus() == IncidentStatus.OPEN || incident.getStatus() == IncidentStatus.INVESTIGATING) {
                incident.setStatus(IncidentStatus.REMEDIATION);
            }
            incidentRepository.save(incident);

            correlateEvent(
                    incident.getId(),
                    UUID.randomUUID(),
                    IncidentRelationshipType.REMEDIATION,
                    "Async remediation task completed [" + rem.getAction() + "] (" + outcome.status() + "): " + outcome.executionDetails()
            );

            auditService.recordAudit(
                    null,
                    rem.getWorkspaceId(),
                    rem.getActorId(),
                    "SYSTEM_WORKER",
                    AuditAction.SECURITY_INCIDENT_UPDATED,
                    "SECURITY_INCIDENT_REMEDIATION",
                    executionId,
                    null,
                    null,
                    "Async remediation task completed: " + outcome.status() + ". " + outcome.executionDetails()
            );

        } catch (Exception ex) {
            log.error("Execution of remediation task {} failed: {}", executionId, ex.getMessage(), ex);
            boolean attemptsRemaining = rem.getAttemptCount() < rem.getMaxAttempts();
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : "Execution error";

            if (attemptsRemaining) {
                remediationRepository.releaseClaimForRetry(
                        executionId,
                        IncidentRemediationStatus.QUEUED,
                        errorMsg,
                        claimToken,
                        Instant.now()
                );
                rem.setStatus(IncidentRemediationStatus.QUEUED);
            } else {
                String exhaustedMsg = "Exhausted retry attempts (" + rem.getAttemptCount() + "/" + rem.getMaxAttempts() + "). Last error: " + errorMsg;
                remediationRepository.failRemediationGuarded(
                        executionId,
                        IncidentRemediationStatus.FAILED,
                        exhaustedMsg,
                        "{\"error\":\"" + exhaustedMsg.replace("\"", "\\\"") + "\"}",
                        claimToken,
                        Instant.now()
                );
                rem.setStatus(IncidentRemediationStatus.FAILED);
            }

            rem.setLastError(errorMsg);
            rem.setWorkerId(null);
            rem.setClaimToken(null);
            rem.setLockedAt(null);

            auditService.recordAudit(
                    null,
                    rem.getWorkspaceId(),
                    rem.getActorId(),
                    "SYSTEM_WORKER",
                    AuditAction.SECURITY_INCIDENT_UPDATED,
                    "SECURITY_INCIDENT_REMEDIATION",
                    executionId,
                    null,
                    null,
                    "Async remediation task " + rem.getStatus() + ": " + errorMsg
            );
        }

        return IncidentRemediationExecutionResponse.fromEntity(rem);
    }

    @Transactional(readOnly = true)
    public List<IncidentRemediationExecutionResponse> listIncidentRemediations(UUID workspaceId, UUID incidentId, UUID actorId) {
        verifySecurityViewAccess(workspaceId, actorId);
        if (remediationRepository == null) return Collections.emptyList();
        return remediationRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .map(IncidentRemediationExecutionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentRemediationExecutionResponse getIncidentRemediation(UUID workspaceId, UUID incidentId, UUID executionId, UUID actorId) {
        verifySecurityViewAccess(workspaceId, actorId);
        if (remediationRepository == null) throw ApiException.notFound("Remediation execution not found");
        return remediationRepository.findByIdAndWorkspaceId(executionId, workspaceId)
                .map(IncidentRemediationExecutionResponse::fromEntity)
                .orElseThrow(() -> ApiException.notFound("Remediation execution not found in this workspace"));
    }

    @Transactional
    public int recoverStaleRemediations(java.time.Duration staleThreshold) {
        if (remediationRepository == null) return 0;
        Instant cutoff = Instant.now().minus(staleThreshold != null ? staleThreshold : java.time.Duration.ofMinutes(5));
        Instant now = Instant.now();
        remediationRepository.failExhaustedStaleRemediationsAtomic(
                IncidentRemediationStatus.FAILED,
                IncidentRemediationStatus.CLAIMED,
                cutoff,
                now
        );
        return remediationRepository.recoverStaleRemediationsAtomic(
                IncidentRemediationStatus.QUEUED,
                IncidentRemediationStatus.CLAIMED,
                cutoff,
                now
        );
    }

    @Transactional
    public IncidentRemediationExecutionResponse retryRemediation(UUID workspaceId, UUID incidentId, UUID executionId, UUID actorId) {
        verifySecurityManageAccess(workspaceId, actorId);
        if (remediationRepository == null) {
            throw ApiException.notFound("Remediation execution not found");
        }

        SecurityIncidentRemediation rem = remediationRepository.findByIdAndWorkspaceId(executionId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Remediation execution not found in this workspace"));

        if (!rem.getIncidentId().equals(incidentId)) {
            throw ApiException.badRequest("Remediation execution does not belong to this incident");
        }

        if (rem.getStatus() != IncidentRemediationStatus.FAILED && rem.getStatus() != IncidentRemediationStatus.PARTIAL_FAILURE) {
            throw ApiException.badRequest("Cannot retry remediation task with status: " + rem.getStatus() + ". Only FAILED or PARTIAL_FAILURE tasks can be retried.");
        }

        if (rem.getApprovalId() != null && approvalRepository != null) {
            SecurityIncidentApproval approval = approvalRepository.findById(rem.getApprovalId())
                    .orElseThrow(() -> ApiException.notFound("Associated approval not found"));
            if (approval.getExpiresAt().isBefore(Instant.now())) {
                approval.setStatus(IncidentApprovalStatus.EXPIRED);
                approvalRepository.save(approval);
                throw ApiException.badRequest("Associated approval request has expired and cannot be retried without re-approval");
            }
        }

        rem.setStatus(IncidentRemediationStatus.QUEUED);
        rem.setAttemptCount(0);
        rem.setLastError(null);
        rem.setWorkerId(null);
        rem.setClaimToken(null);
        rem.setLockedAt(null);
        rem.setCompletedAt(null);
        SecurityIncidentRemediation saved = remediationRepository.save(rem);

        correlateEvent(
                incidentId,
                UUID.randomUUID(),
                IncidentRelationshipType.REMEDIATION,
                "Remediation task [" + rem.getAction() + "] reset for retry by operator (Task ID: " + executionId + ")"
        );

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                actorId != null ? "USER" : "SYSTEM",
                AuditAction.SECURITY_INCIDENT_UPDATED,
                "SECURITY_INCIDENT_REMEDIATION",
                executionId,
                null,
                null,
                "Remediation task reset for retry by operator"
        );

        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new IncidentRemediationQueuedEvent(
                    saved.getId(),
                    workspaceId,
                    incidentId
            ));
        }

        return IncidentRemediationExecutionResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public SecurityIncident getIncident(UUID workspaceId, UUID incidentId, UUID actorId) {
        verifySecurityViewAccess(workspaceId, actorId);
        return incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Security incident not found in this workspace"));
    }

    @Transactional(readOnly = true)
    public Page<SecurityIncident> listIncidents(
            UUID workspaceId,
            IncidentStatus status,
            IncidentSeverity severity,
            Pageable pageable,
            UUID actorId
    ) {
        verifySecurityViewAccess(workspaceId, actorId);
        if (status != null) {
            return incidentRepository.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, status, pageable);
        }
        if (severity != null) {
            return incidentRepository.findByWorkspaceIdAndSeverityOrderByCreatedAtDesc(workspaceId, severity, pageable);
        }
        return incidentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    public List<SecurityIncidentEvent> getIncidentEvents(UUID workspaceId, UUID incidentId, UUID actorId) {
        verifySecurityViewAccess(workspaceId, actorId);
        getIncident(workspaceId, incidentId, actorId); // verify existence & workspace
        return incidentEventRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
    }

    /**
     * Correlate incoming event to open incidents or create new incident for critical conditions.
     */
    @Transactional
    public void processDomainEventCorrelation(DomainEvent event) {
        if (event == null || event.workspaceId() == null) return;

        // Ignore self-published incident events to prevent feedback loops
        if (event.eventType() == EventType.SECURITY_INCIDENT_CREATED ||
            event.eventType() == EventType.SECURITY_INCIDENT_UPDATED ||
            event.eventType() == EventType.SECURITY_INCIDENT_RESOLVED) {
            return;
        }

        List<SecurityIncident> activeIncidents = incidentRepository.findActiveIncidents(event.workspaceId());
        boolean correlated = false;

        for (SecurityIncident incident : activeIncidents) {
            if (incident.getSourceEventId() != null && incident.getSourceEventId().equals(event.eventId())) {
                continue;
            }

            // Correlate only if aggregate or causation matches
            boolean isMatchingAggregate = false;
            if (event.secretId() != null && incident.getDescription() != null &&
                    incident.getDescription().contains(event.secretId().toString())) {
                isMatchingAggregate = true;
            } else if (event.aggregateId() != null && incident.getDescription() != null &&
                    incident.getDescription().contains(event.aggregateId())) {
                isMatchingAggregate = true;
            } else if (event.causationId() != null && incident.getSourceEventId() != null &&
                    event.causationId().equals(incident.getSourceEventId().toString())) {
                isMatchingAggregate = true;
            } else if (event.correlationId() != null && incident.getSourceEventId() != null &&
                    event.correlationId().equals(incident.getSourceEventId().toString())) {
                isMatchingAggregate = true;
            }

            if (isMatchingAggregate) {
                correlateEvent(
                        incident.getId(),
                        event.eventId(),
                        IncidentRelationshipType.CORRELATED,
                        "Auto-correlated related event " + event.eventType()
                );
                correlated = true;
                break;
            }
        }

        // If it's a critical security trigger and no matching incident exists, create one
        if (!correlated && isIncidentTriggerEventType(event.eventType())) {
            IncidentSeverity severity = (event.severity() == EventSeverity.CRITICAL || event.eventType() == EventType.SECRET_COMPROMISED)
                    ? IncidentSeverity.CRITICAL
                    : IncidentSeverity.HIGH;

            String target = event.aggregateType() != null && event.aggregateId() != null
                    ? event.aggregateType() + ":" + event.aggregateId()
                    : (event.secretId() != null ? "SECRET:" + event.secretId() : "WORKSPACE:" + event.workspaceId());

            createIncident(
                    event.workspaceId(),
                    "Auto Incident: " + event.eventType().name(),
                    "Triggered automatically by security signal: " + event.eventType().name() + " on target [" + target + "]",
                    severity,
                    event.eventType().name(),
                    event.eventId(),
                    null
            );
        }
    }

    private void validateStatusTransition(IncidentStatus current, IncidentStatus target) {
        if (current == target) {
            throw ApiException.badRequest("Incident is already in status: " + current);
        }

        boolean valid = switch (current) {
            case OPEN -> target == IncidentStatus.INVESTIGATING
                    || target == IncidentStatus.CONTAINED
                    || target == IncidentStatus.REMEDIATION
                    || target == IncidentStatus.RESOLVED
                    || target == IncidentStatus.CLOSED; // Closed as false positive / duplicate
            case INVESTIGATING -> target == IncidentStatus.CONTAINED
                    || target == IncidentStatus.REMEDIATION
                    || target == IncidentStatus.RESOLVED
                    || target == IncidentStatus.CLOSED; // False positive
            case CONTAINED -> target == IncidentStatus.REMEDIATION
                    || target == IncidentStatus.INVESTIGATING
                    || target == IncidentStatus.RESOLVED
                    || target == IncidentStatus.CLOSED;
            case REMEDIATION -> target == IncidentStatus.RESOLVED
                    || target == IncidentStatus.INVESTIGATING
                    || target == IncidentStatus.CONTAINED;
            case RESOLVED -> target == IncidentStatus.CLOSED
                    || target == IncidentStatus.INVESTIGATING; // Reopening
            case CLOSED -> target == IncidentStatus.INVESTIGATING; // Reopening
        };

        if (!valid) {
            throw ApiException.badRequest(
                    String.format("Invalid incident status transition from %s to %s", current, target)
            );
        }
    }

    private boolean isIncidentTriggerEventType(EventType type) {
        if (type == null) return false;
        return switch (type) {
            case SECRET_COMPROMISED,
                 MACHINE_REVOKED,
                 MACHINE_SUSPENDED,
                 MACHINE_SESSION_REJECTED,
                 SECURITY_POLICY_VIOLATION,
                 ANOMALY_DETECTED,
                 ROTATION_FAILED,
                 PROVIDER_DRIFT_DETECTED,
                 LEASE_RENEWAL_REJECTED,
                 SECURITY_FINDING_CREATED,
                 EVENT_PROCESSING_FAILURE,
                 AUTOMATION_EXECUTION_FAILURE -> true;
            default -> false;
        };
    }

    private void verifySecurityViewAccess(UUID workspaceId, UUID actorId) {
        if (actorId == null) return;
        var decision = effectiveAccessService.evaluateAccess(workspaceId, null, null, null, AccessPermission.SECURITY_VIEW, actorId);
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing SECURITY_VIEW permission");
        }
    }

    private void verifySecurityManageAccess(UUID workspaceId, UUID actorId) {
        if (actorId == null) return;
        var decision = effectiveAccessService.evaluateAccess(workspaceId, null, null, null, AccessPermission.SECURITY_MANAGE, actorId);
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing SECURITY_MANAGE permission");
        }
    }

    private void verifySecretScopeAccess(UUID workspaceId, UUID secretId, UUID actorId, AccessPermission permission) {
        if (secretId == null) {
            throw ApiException.badRequest("targetSecretId is required");
        }
        if (actorId == null) return;
        var decision = effectiveAccessService.evaluateAccess(workspaceId, null, null, secretId, permission, actorId);
        if (!decision.allowed()) {
            String explanation = decision.deniedReason() != null ? decision.deniedReason() : decision.reason();
            if ("Secret not found".equals(explanation)) {
                throw ApiException.notFound("Secret not found");
            }
            if (explanation != null && (explanation.contains("Project not found in this workspace") || explanation.contains("Workspace not found"))) {
                throw ApiException.forbidden("Target secret does not belong to this workspace");
            }
            throw ApiException.forbidden("Access denied for target secret: " + explanation);
        }
    }
}
