package com.secretvault.automation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.automation.engine.AutomationEngine;
import com.secretvault.automation.entity.AutomationApproval;
import com.secretvault.automation.entity.AutomationApprovalStatus;
import com.secretvault.automation.entity.AutomationExecution;
import com.secretvault.automation.entity.AutomationExecutionStatus;
import com.secretvault.automation.entity.AutomationPolicy;
import com.secretvault.automation.model.AutomationAction;
import com.secretvault.automation.model.AutomationActionType;
import com.secretvault.automation.repository.AutomationApprovalRepository;
import com.secretvault.automation.repository.AutomationExecutionRepository;
import com.secretvault.automation.repository.AutomationPolicyRepository;
import com.secretvault.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AutomationApprovalService {

    private static final Logger log = LoggerFactory.getLogger(AutomationApprovalService.class);

    private final AutomationApprovalRepository approvalRepository;
    private final AutomationExecutionRepository executionRepository;
    private final AutomationPolicyRepository policyRepository;
    private final AutomationEngine automationEngine;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AutomationApprovalService(
            AutomationApprovalRepository approvalRepository,
            AutomationExecutionRepository executionRepository,
            AutomationPolicyRepository policyRepository,
            AutomationEngine automationEngine,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.approvalRepository = approvalRepository;
        this.executionRepository = executionRepository;
        this.policyRepository = policyRepository;
        this.automationEngine = automationEngine;
        this.effectiveAccessService = effectiveAccessService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<AutomationApproval> listApprovals(
            UUID workspaceId,
            AutomationApprovalStatus status,
            Pageable pageable,
            UUID actorId
    ) {
        verifyReadAccess(workspaceId, actorId);
        if (status != null) {
            return approvalRepository.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, status, pageable);
        }
        return approvalRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable);
    }

    @Transactional
    public AutomationApproval decideApproval(
            UUID workspaceId,
            UUID approvalId,
            boolean approve,
            String rejectionReason,
            UUID actorId
    ) {
        verifyManageAccess(workspaceId, actorId);

        AutomationApproval approval = approvalRepository.findById(approvalId)
                .filter(a -> a.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> ApiException.notFound("Automation approval not found"));

        if (approval.getStatus() != AutomationApprovalStatus.PENDING) {
            throw ApiException.badRequest("Approval is already decided: " + approval.getStatus());
        }

        if (approval.getExpiresAt().isBefore(Instant.now())) {
            approval.setStatus(AutomationApprovalStatus.EXPIRED);
            approvalRepository.save(approval);
            throw ApiException.badRequest("Approval request has expired");
        }

        // Anti-self-approval rule
        if (actorId != null && actorId.toString().equals(approval.getRequestedBy())) {
            throw ApiException.forbidden("Anti-self-approval violation: Requester cannot approve their own automation action");
        }

        approval.setDecidedBy(actorId);
        approval.setDecidedAt(Instant.now());
        approval.setStatus(approve ? AutomationApprovalStatus.APPROVED : AutomationApprovalStatus.REJECTED);
        if (!approve) {
            approval.setRejectionReason(rejectionReason != null ? rejectionReason : "Rejected by operator");
        }

        AutomationApproval saved = approvalRepository.save(approval);

        // Update parent execution
        AutomationExecution exec = executionRepository.findById(approval.getExecutionId()).orElse(null);
        if (exec != null) {
            if (approve) {
                exec.setStatus(AutomationExecutionStatus.COMPLETED);
                // Execute the action
                try {
                    AutomationPolicy policy = approval.getPolicyId() != null
                            ? policyRepository.findById(approval.getPolicyId()).orElse(null)
                            : null;
                    if (policy != null) {
                        Map<String, Object> params = objectMapper.readValue(approval.getActionPayloadJson(), new TypeReference<>() {});
                        AutomationAction action = new AutomationAction(AutomationActionType.valueOf(approval.getActionType()), params);
                        automationEngine.executeAction(policy, null, action);
                    }
                } catch (Exception e) {
                    log.error("Failed to execute approved automation action {}: {}", approval.getId(), e.getMessage(), e);
                    exec.setStatus(AutomationExecutionStatus.FAILED);
                    exec.setErrorMessage("Action execution failed after approval: " + e.getMessage());
                }
            } else {
                exec.setStatus(AutomationExecutionStatus.DENIED);
                exec.setErrorMessage("Action rejected: " + rejectionReason);
            }
            executionRepository.save(exec);
        }

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                approve ? AuditAction.AUTOMATION_APPROVAL_APPROVED : AuditAction.AUTOMATION_APPROVAL_REJECTED,
                "AUTOMATION_APPROVAL",
                saved.getId(),
                null,
                null,
                approve ? "Approved action " + saved.getActionType() : "Rejected: " + rejectionReason
        );

        return saved;
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
