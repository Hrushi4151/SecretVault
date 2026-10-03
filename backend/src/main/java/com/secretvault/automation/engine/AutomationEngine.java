package com.secretvault.automation.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.automation.entity.*;
import com.secretvault.automation.model.*;
import com.secretvault.automation.repository.AutomationApprovalRepository;
import com.secretvault.automation.repository.AutomationExecutionRepository;
import com.secretvault.automation.repository.AutomationPolicyRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventSeverity;
import com.secretvault.events.model.EventType;
import com.secretvault.events.publisher.EventPublisher;
import com.secretvault.incident.entity.IncidentSeverity;
import com.secretvault.incident.service.SecurityIncidentService;
import com.secretvault.machine.service.MachineIdentityService;
import com.secretvault.notification.entity.NotificationSeverity;
import com.secretvault.notification.service.NotificationService;
import com.secretvault.rotation.dto.RotationDtos.TriggerRotationRequest;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.service.RotationService;
import com.secretvault.rotation.service.SecretLeaseService;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.service.SecurityFindingService;
import com.secretvault.webhook.service.WebhookDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class AutomationEngine {

    private static final Logger log = LoggerFactory.getLogger(AutomationEngine.class);

    private final AutomationPolicyRepository policyRepository;
    private final AutomationExecutionRepository executionRepository;
    private final AutomationApprovalRepository approvalRepository;
    private final ConditionAstEvaluator conditionEvaluator;
    private final AutomationLoopDetector loopDetector;
    private final NotificationService notificationService;
    private final WebhookDeliveryService webhookDeliveryService;
    private final SecurityIncidentService incidentService;
    private final SecurityFindingService findingService;
    private final RotationService rotationService;
    private final SecretLeaseService leaseService;
    private final MachineIdentityService machineService;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AutomationEngine(
            AutomationPolicyRepository policyRepository,
            AutomationExecutionRepository executionRepository,
            AutomationApprovalRepository approvalRepository,
            ConditionAstEvaluator conditionEvaluator,
            AutomationLoopDetector loopDetector,
            NotificationService notificationService,
            WebhookDeliveryService webhookDeliveryService,
            SecurityIncidentService incidentService,
            SecurityFindingService findingService,
            RotationService rotationService,
            SecretLeaseService leaseService,
            MachineIdentityService machineService,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService,
            EventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.policyRepository = policyRepository;
        this.executionRepository = executionRepository;
        this.approvalRepository = approvalRepository;
        this.conditionEvaluator = conditionEvaluator;
        this.loopDetector = loopDetector;
        this.notificationService = notificationService;
        this.webhookDeliveryService = webhookDeliveryService;
        this.incidentService = incidentService;
        this.findingService = findingService;
        this.rotationService = rotationService;
        this.leaseService = leaseService;
        this.machineService = machineService;
        this.effectiveAccessService = effectiveAccessService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processEvent(DomainEvent event) {
        if (event == null || event.workspaceId() == null) return;

        // Loop protection check
        if (loopDetector.isLoopDetected(event)) {
            log.warn("Automation loop detected for event {} in workspace {}. Aborting automation chain.", event.eventId(), event.workspaceId());
            recordLoopAborted(event);
            return;
        }

        List<AutomationPolicy> policies = policyRepository.findByWorkspaceIdAndEnabledTrueOrderByPriorityAsc(event.workspaceId());

        for (AutomationPolicy policy : policies) {
            try {
                evaluateAndExecutePolicy(policy, event);
            } catch (Exception e) {
                log.error("Failed to execute automation policy {} for event {}: {}", policy.getId(), event.eventId(), e.getMessage(), e);
            }
        }
    }

    private void evaluateAndExecutePolicy(AutomationPolicy policy, DomainEvent event) {
        // 1. Check trigger event type match
        if (!isEventTypeTriggered(policy.getTriggerEventTypes(), event.eventType())) {
            return;
        }

        // 2. Check scope match
        if (!isScopeMatched(policy, event)) {
            return;
        }

        long start = System.currentTimeMillis();

        // 3. Evaluate AST Conditions
        List<AutomationCondition> conditions = parseConditions(policy.getConditionsJson());
        boolean matched = true;
        for (AutomationCondition cond : conditions) {
            if (!conditionEvaluator.evaluate(cond, event)) {
                matched = false;
                break;
            }
        }

        if (!matched) {
            return;
        }

        List<AutomationAction> actions = parseActions(policy.getActionsJson());

        // 4. Handle Dry Run
        if (policy.isDryRun()) {
            recordExecution(policy, event, AutomationExecutionStatus.COMPLETED, true, "Dry-run evaluated true for " + actions.size() + " actions", actions, null, System.currentTimeMillis() - start);
            return;
        }

        // 5. Handle Four-Eyes Approval Workflow if required
        if (policy.isApprovalRequired() && hasSensitiveActions(actions)) {
            AutomationExecution exec = recordExecution(policy, event, AutomationExecutionStatus.AWAITING_APPROVAL, false, "Awaiting approval for sensitive actions", actions, null, System.currentTimeMillis() - start);
            createApprovals(policy, exec, actions);
            return;
        }

        // 6. Execute Authorized Actions
        List<String> executed = new ArrayList<>();
        try {
            for (AutomationAction action : actions) {
                executeAction(policy, event, action);
                executed.add(action.getType().name());
            }

            long duration = System.currentTimeMillis() - start;
            recordExecution(policy, event, AutomationExecutionStatus.COMPLETED, false, "Successfully executed actions: " + executed, actions, null, duration);

            auditService.recordAudit(
                    null,
                    policy.getWorkspaceId(),
                    null,
                    "SYSTEM",
                    AuditAction.AUTOMATION_POLICY_EXECUTED,
                    "AUTOMATION_POLICY",
                    policy.getId(),
                    null,
                    null,
                    "SUCCESS"
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            recordExecution(policy, event, AutomationExecutionStatus.FAILED, false, "Execution failed: " + e.getMessage(), actions, e.getMessage(), duration);
        }
    }

    public void executeAction(AutomationPolicy policy, DomainEvent event, AutomationAction action) {
        if (action == null || action.getType() == null) return;

        Map<String, Object> params = action.getParameters() != null ? action.getParameters() : Collections.emptyMap();
        UUID workspaceId = policy.getWorkspaceId();

        switch (action.getType()) {
            case NOTIFY -> {
                String title = (String) params.getOrDefault("title", "Automation Alert: " + policy.getName());
                String message = (String) params.getOrDefault("message", "Policy " + policy.getName() + " executed on " + event.eventType());
                String sevStr = (String) params.getOrDefault("severity", "MEDIUM");
                NotificationSeverity severity = NotificationSeverity.valueOf(sevStr);
                notificationService.createNotification(workspaceId, null, severity, title, message, event.eventId(), null);
            }
            case CREATE_SECURITY_FINDING -> {
                String title = (String) params.getOrDefault("title", "Finding: " + policy.getName());
                String desc = (String) params.getOrDefault("description", "Automated finding triggered by " + event.eventType());
                String sevStr = (String) params.getOrDefault("severity", "HIGH");
                String catStr = (String) params.getOrDefault("category", "POLICY_VIOLATION");
                findingService.upsertFinding(new SecurityFindingDraft(
                        workspaceId,
                        event.projectId(),
                        event.environmentId(),
                        FindingCategory.valueOf(catStr),
                        FindingSeverity.valueOf(sevStr),
                        FindingConfidence.HIGH,
                        title,
                        desc,
                        "Review policy automation trigger",
                        event.aggregateId() != null ? event.aggregateId() : policy.getId().toString(),
                        Map.of("policyId", policy.getId().toString(), "eventId", event.eventId().toString())
                ));
            }
            case CREATE_SECURITY_INCIDENT -> {
                String title = (String) params.getOrDefault("title", "Automated Incident: " + policy.getName());
                String desc = (String) params.getOrDefault("description", "Security incident triggered by automation policy " + policy.getName());
                String sevStr = (String) params.getOrDefault("severity", "HIGH");
                incidentService.createIncident(workspaceId, title, desc, IncidentSeverity.valueOf(sevStr), "AUTOMATION", event.eventId(), null);
            }
            case TRIGGER_ROTATION -> {
                UUID secretId = event.secretId();
                if (secretId == null && params.containsKey("secretId")) {
                    secretId = UUID.fromString((String) params.get("secretId"));
                }
                if (secretId != null) {
                    TriggerRotationRequest req = new TriggerRotationRequest(RotationStrategy.EMERGENCY, "Automated rotation triggered by policy " + policy.getName(), true, true);
                    rotationService.triggerRotation(workspaceId, secretId, req, null, "AUTO-ROT-" + policy.getId() + "-" + event.eventId());
                }
            }
            case REVOKE_LEASE -> {
                if (event.secretId() != null) {
                    // Revoke all active leases for the secret
                    leaseService.listLeases(workspaceId, event.secretId(), null, org.springframework.data.domain.Pageable.unpaged(), null)
                            .forEach(l -> leaseService.revokeLease(workspaceId, l.id(), null));
                }
            }
            case SUSPEND_MACHINE -> {
                String machineIdStr = (String) params.get("machineIdentityId");
                if (machineIdStr == null && "MACHINE_IDENTITY".equalsIgnoreCase(event.aggregateType())) {
                    machineIdStr = event.aggregateId();
                }
                if (machineIdStr != null) {
                    machineService.disableMachineIdentity(UUID.fromString(machineIdStr), workspaceId, null);
                }
            }
            case REVOKE_MACHINE -> {
                String machineIdStr = (String) params.get("machineIdentityId");
                if (machineIdStr == null && "MACHINE_IDENTITY".equalsIgnoreCase(event.aggregateType())) {
                    machineIdStr = event.aggregateId();
                }
                if (machineIdStr != null) {
                    machineService.disableMachineIdentity(UUID.fromString(machineIdStr), workspaceId, null);
                }
            }
            case CREATE_WEBHOOK_DELIVERY -> {
                webhookDeliveryService.dispatchDomainEvent(event);
            }
            case ADD_AUDIT_EVENT -> {
                auditService.recordAudit(
                        null,
                        workspaceId,
                        null,
                        "SYSTEM",
                        AuditAction.AUTOMATION_POLICY_EXECUTED,
                        "AUTOMATION_POLICY",
                        policy.getId(),
                        null,
                        null,
                        "Custom audit entry from policy " + policy.getName()
                );
            }
            default -> log.warn("Unhandled automation action: {}", action.getType());
        }
    }

    private boolean isEventTypeTriggered(String triggerEventTypes, EventType eventType) {
        if (triggerEventTypes == null || triggerEventTypes.isBlank() || "*".equals(triggerEventTypes.trim())) {
            return true;
        }
        String[] types = triggerEventTypes.split(",");
        for (String t : types) {
            String trimmed = t.trim();
            if ("*".equals(trimmed) || trimmed.equalsIgnoreCase(eventType.name())) {
                return true;
            }
        }
        return false;
    }

    private boolean isScopeMatched(AutomationPolicy policy, DomainEvent event) {
        if (policy.getScopeType() == AutomationPolicyScope.WORKSPACE) return true;
        if (policy.getScopeType() == AutomationPolicyScope.PROJECT) {
            return policy.getProjectId() == null || policy.getProjectId().equals(event.projectId());
        }
        if (policy.getScopeType() == AutomationPolicyScope.ENVIRONMENT) {
            return policy.getEnvironmentId() == null || policy.getEnvironmentId().equals(event.environmentId());
        }
        if (policy.getScopeType() == AutomationPolicyScope.SECRET) {
            return policy.getSecretId() == null || policy.getSecretId().equals(event.secretId());
        }
        return true;
    }

    private boolean hasSensitiveActions(List<AutomationAction> actions) {
        for (AutomationAction action : actions) {
            if (action.getType() == AutomationActionType.TRIGGER_ROTATION ||
                action.getType() == AutomationActionType.SUSPEND_MACHINE ||
                action.getType() == AutomationActionType.REVOKE_MACHINE ||
                action.getType() == AutomationActionType.REVOKE_LEASE) {
                return true;
            }
        }
        return false;
    }

    private void createApprovals(AutomationPolicy policy, AutomationExecution exec, List<AutomationAction> actions) {
        for (AutomationAction action : actions) {
            AutomationApproval appr = new AutomationApproval();
            appr.setWorkspaceId(policy.getWorkspaceId());
            appr.setExecutionId(exec.getId());
            appr.setPolicyId(policy.getId());
            appr.setActionType(action.getType().name());
            try {
                appr.setActionPayloadJson(objectMapper.writeValueAsString(action.getParameters()));
            } catch (Exception e) {
                appr.setActionPayloadJson("{}");
            }
            appr.setStatus(AutomationApprovalStatus.PENDING);
            appr.setRequestedBy("SYSTEM_AUTOMATION");
            appr.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
            approvalRepository.save(appr);
        }
    }

    private AutomationExecution recordExecution(
            AutomationPolicy policy,
            DomainEvent event,
            AutomationExecutionStatus status,
            boolean dryRun,
            String evalResult,
            List<AutomationAction> actions,
            String error,
            long durationMs
    ) {
        AutomationExecution exec = new AutomationExecution();
        exec.setWorkspaceId(policy.getWorkspaceId());
        exec.setPolicyId(policy.getId());
        exec.setPolicyVersion(policy.getPolicyVersion());
        exec.setEventId(event.eventId());
        exec.setTriggerEventType(event.eventType().name());
        exec.setStatus(status);
        exec.setDryRun(dryRun);
        exec.setEvaluationResultJson(evalResult);
        try {
            exec.setActionsExecutedJson(objectMapper.writeValueAsString(actions));
        } catch (Exception ignored) {
            exec.setActionsExecutedJson("[]");
        }
        exec.setErrorMessage(error);
        exec.setDurationMs(durationMs);
        exec.setCausationId(event.causationId());
        exec.setCorrelationId(event.correlationId());
        return executionRepository.save(exec);
    }

    private void recordLoopAborted(DomainEvent event) {
        AutomationExecution exec = new AutomationExecution();
        exec.setWorkspaceId(event.workspaceId());
        exec.setPolicyId(null);
        exec.setPolicyVersion(1);
        exec.setEventId(event.eventId());
        exec.setTriggerEventType(event.eventType().name());
        exec.setStatus(AutomationExecutionStatus.LOOP_ABORTED);
        exec.setErrorMessage("Automation loop detected; execution stopped safely.");
        exec.setCausationId(event.causationId());
        exec.setCorrelationId(event.correlationId());
        executionRepository.save(exec);

        incidentService.createIncident(
                event.workspaceId(),
                "SECURITY ALERT: Automation Loop Detected",
                "Automation chain exceeded loop safety thresholds for event " + event.eventId(),
                IncidentSeverity.CRITICAL,
                "LOOP_PROTECTION",
                event.eventId(),
                null
        );
    }

    private List<AutomationCondition> parseConditions(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<AutomationCondition>>() {});
        } catch (Exception e) {
            log.error("Failed to parse conditions JSON: {}", json, e);
            return Collections.emptyList();
        }
    }

    private List<AutomationAction> parseActions(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<AutomationAction>>() {});
        } catch (Exception e) {
            log.error("Failed to parse actions JSON: {}", json, e);
            return Collections.emptyList();
        }
    }
}
