package com.secretvault.automation.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.automation.engine.AutomationPolicySimulator;
import com.secretvault.automation.entity.AutomationPolicy;
import com.secretvault.automation.entity.AutomationPolicyScope;
import com.secretvault.automation.model.SimulationResult;
import com.secretvault.automation.repository.AutomationPolicyRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.events.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AutomationPolicyService {

    private static final Logger log = LoggerFactory.getLogger(AutomationPolicyService.class);

    private final AutomationPolicyRepository policyRepository;
    private final AutomationPolicySimulator policySimulator;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;

    public AutomationPolicyService(
            AutomationPolicyRepository policyRepository,
            AutomationPolicySimulator policySimulator,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService
    ) {
        this.policyRepository = policyRepository;
        this.policySimulator = policySimulator;
        this.effectiveAccessService = effectiveAccessService;
        this.auditService = auditService;
    }

    @Transactional
    public AutomationPolicy createPolicy(
            UUID workspaceId,
            String name,
            String description,
            boolean enabled,
            int priority,
            AutomationPolicyScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            String triggerEventTypes,
            String conditionsJson,
            String actionsJson,
            boolean dryRun,
            boolean approvalRequired,
            UUID actorId
    ) {
        verifyManageAccess(workspaceId, actorId);

        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Policy name is required");
        }

        if (policyRepository.findByWorkspaceIdAndName(workspaceId, name.trim()).isPresent()) {
            throw ApiException.conflict("Policy with name '" + name + "' already exists in this workspace");
        }

        AutomationPolicy policy = new AutomationPolicy();
        policy.setWorkspaceId(workspaceId);
        policy.setName(name.trim());
        policy.setDescription(description);
        policy.setEnabled(enabled);
        policy.setPriority(priority > 0 ? priority : 100);
        policy.setScopeType(scopeType != null ? scopeType : AutomationPolicyScope.WORKSPACE);
        policy.setProjectId(projectId);
        policy.setEnvironmentId(environmentId);
        policy.setSecretId(secretId);
        policy.setTriggerEventTypes(triggerEventTypes != null ? triggerEventTypes : "*");
        policy.setConditionsJson(conditionsJson != null ? conditionsJson : "[]");
        policy.setActionsJson(actionsJson != null ? actionsJson : "[]");
        policy.setDryRun(dryRun);
        policy.setApprovalRequired(approvalRequired);
        policy.setPolicyVersion(1);
        policy.setCreatedBy(actorId);
        policy.setUpdatedBy(actorId);

        AutomationPolicy saved = policyRepository.save(policy);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.AUTOMATION_POLICY_CREATED,
                "AUTOMATION_POLICY",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        return saved;
    }

    @Transactional
    public AutomationPolicy updatePolicy(
            UUID workspaceId,
            UUID policyId,
            String name,
            String description,
            Boolean enabled,
            Integer priority,
            AutomationPolicyScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            String triggerEventTypes,
            String conditionsJson,
            String actionsJson,
            Boolean dryRun,
            Boolean approvalRequired,
            UUID actorId
    ) {
        verifyManageAccess(workspaceId, actorId);

        AutomationPolicy policy = policyRepository.findByIdAndWorkspaceId(policyId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Automation policy not found"));

        if (name != null && !name.isBlank()) {
            policy.setName(name.trim());
        }
        if (description != null) policy.setDescription(description);
        if (enabled != null) policy.setEnabled(enabled);
        if (priority != null && priority > 0) policy.setPriority(priority);
        if (scopeType != null) policy.setScopeType(scopeType);
        if (projectId != null) policy.setProjectId(projectId);
        if (environmentId != null) policy.setEnvironmentId(environmentId);
        if (secretId != null) policy.setSecretId(secretId);
        if (triggerEventTypes != null) policy.setTriggerEventTypes(triggerEventTypes);
        if (conditionsJson != null) policy.setConditionsJson(conditionsJson);
        if (actionsJson != null) policy.setActionsJson(actionsJson);
        if (dryRun != null) policy.setDryRun(dryRun);
        if (approvalRequired != null) policy.setApprovalRequired(approvalRequired);

        // Version increment on mutation
        policy.setPolicyVersion(policy.getPolicyVersion() + 1);
        policy.setUpdatedBy(actorId);

        AutomationPolicy saved = policyRepository.save(policy);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.AUTOMATION_POLICY_UPDATED,
                "AUTOMATION_POLICY",
                saved.getId(),
                null,
                null,
                "Updated to version " + saved.getPolicyVersion()
        );

        return saved;
    }

    @Transactional
    public AutomationPolicy togglePolicy(UUID workspaceId, UUID policyId, boolean enable, UUID actorId) {
        verifyManageAccess(workspaceId, actorId);

        AutomationPolicy policy = policyRepository.findByIdAndWorkspaceId(policyId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Automation policy not found"));

        policy.setEnabled(enable);
        policy.setUpdatedBy(actorId);
        AutomationPolicy saved = policyRepository.save(policy);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                enable ? AuditAction.AUTOMATION_POLICY_ENABLED : AuditAction.AUTOMATION_POLICY_DISABLED,
                "AUTOMATION_POLICY",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        return saved;
    }

    @Transactional
    public void deletePolicy(UUID workspaceId, UUID policyId, UUID actorId) {
        verifyManageAccess(workspaceId, actorId);

        AutomationPolicy policy = policyRepository.findByIdAndWorkspaceId(policyId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Automation policy not found"));

        policyRepository.delete(policy);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.AUTOMATION_POLICY_DELETED,
                "AUTOMATION_POLICY",
                policyId,
                null,
                null,
                "SUCCESS"
        );
    }

    @Transactional(readOnly = true)
    public AutomationPolicy getPolicy(UUID workspaceId, UUID policyId, UUID actorId) {
        verifyReadAccess(workspaceId, actorId);
        return policyRepository.findByIdAndWorkspaceId(policyId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Automation policy not found"));
    }

    @Transactional(readOnly = true)
    public Page<AutomationPolicy> listPolicies(UUID workspaceId, Pageable pageable, UUID actorId) {
        verifyReadAccess(workspaceId, actorId);
        return policyRepository.findByWorkspaceIdOrderByPriorityAsc(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    public List<SimulationResult> simulate(UUID workspaceId, DomainEvent sampleEvent, UUID actorId) {
        verifyReadAccess(workspaceId, actorId);
        List<AutomationPolicy> policies = policyRepository.findByWorkspaceIdAndEnabledTrueOrderByPriorityAsc(workspaceId);
        return policies.stream()
                .map(p -> policySimulator.simulate(p, sampleEvent))
                .toList();
    }

    private void verifyManageAccess(UUID workspaceId, UUID actorId) {
        if (actorId == null) return;
        var decision = effectiveAccessService.evaluateAccess(workspaceId, null, null, null, AccessPermission.AUTOMATION_MANAGE, actorId);
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing WORKSPACE_SETTINGS_MANAGE permission");
        }
    }

    private void verifyReadAccess(UUID workspaceId, UUID actorId) {
        if (actorId == null) return;
        var decision = effectiveAccessService.evaluateAccess(workspaceId, null, null, null, AccessPermission.AUTOMATION_VIEW, actorId);
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing WORKSPACE_SETTINGS_READ permission");
        }
    }
}
