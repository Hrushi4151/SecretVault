package com.secretvault.automation.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.automation.entity.AutomationExecution;
import com.secretvault.automation.entity.AutomationExecutionStatus;
import com.secretvault.automation.repository.AutomationExecutionRepository;
import com.secretvault.common.exception.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AutomationExecutionService {

    private final AutomationExecutionRepository executionRepository;
    private final EffectiveAccessService effectiveAccessService;

    public AutomationExecutionService(
            AutomationExecutionRepository executionRepository,
            EffectiveAccessService effectiveAccessService
    ) {
        this.executionRepository = executionRepository;
        this.effectiveAccessService = effectiveAccessService;
    }

    @Transactional(readOnly = true)
    public Page<AutomationExecution> listExecutions(
            UUID workspaceId,
            UUID policyId,
            AutomationExecutionStatus status,
            Pageable pageable,
            UUID actorId
    ) {
        verifyReadAccess(workspaceId, actorId);
        if (policyId != null) {
            return executionRepository.findByWorkspaceIdAndPolicyIdOrderByCreatedAtDesc(workspaceId, policyId, pageable);
        }
        if (status != null) {
            return executionRepository.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, status, pageable);
        }
        return executionRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    public AutomationExecution getExecution(UUID workspaceId, UUID executionId, UUID actorId) {
        verifyReadAccess(workspaceId, actorId);
        return executionRepository.findById(executionId)
                .filter(e -> e.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> ApiException.notFound("Automation execution not found in this workspace"));
    }

    private void verifyReadAccess(UUID workspaceId, UUID actorId) {
        if (actorId == null) return;
        var decision = effectiveAccessService.evaluateAccess(workspaceId, null, null, null, AccessPermission.AUTOMATION_VIEW, actorId);
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing WORKSPACE_AUDIT_READ permission");
        }
    }
}
