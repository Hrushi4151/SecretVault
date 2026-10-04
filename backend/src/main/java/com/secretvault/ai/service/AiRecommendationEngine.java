package com.secretvault.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.domain.entity.AiRemediationPlan;
import com.secretvault.ai.domain.model.AiPlanStatus;
import com.secretvault.ai.domain.model.BlastRadiusImpact;
import com.secretvault.ai.domain.model.RemediationStep;
import com.secretvault.ai.domain.repository.AiRemediationPlanRepository;
import com.secretvault.ai.dto.AiRemediationPlanDto;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Autonomous Recommendation Engine & Remediation Plan Lifecycle Manager.
 * Handles reviewable proposals, human approval gates, expiration, and feedback.
 */
@Service
public class AiRecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationEngine.class);

    private final AiRemediationPlanRepository planRepository;
    private final AuditService auditService;
    private final SecurityEventService securityEventService;
    private final ObjectMapper objectMapper;

    public AiRecommendationEngine(
            AiRemediationPlanRepository planRepository,
            AuditService auditService,
            @Autowired(required = false) SecurityEventService securityEventService,
            ObjectMapper objectMapper
    ) {
        this.planRepository = planRepository;
        this.auditService = auditService;
        this.securityEventService = securityEventService;
        this.objectMapper = objectMapper;
    }

    public Page<AiRemediationPlanDto> getPlans(UUID workspaceId, Pageable pageable) {
        expireStalePlans(workspaceId);
        return planRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable)
                .map(this::toDto);
    }

    public Page<AiRemediationPlanDto> getPendingPlans(UUID workspaceId, Pageable pageable) {
        expireStalePlans(workspaceId);
        return planRepository.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, AiPlanStatus.PENDING_APPROVAL, pageable)
                .map(this::toDto);
    }

    public AiRemediationPlanDto getPlan(UUID workspaceId, UUID planId) {
        AiRemediationPlan plan = planRepository.findByIdAndWorkspaceId(planId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Remediation plan not found: " + planId));
        checkExpiration(plan);
        return toDto(plan);
    }

    @Transactional
    public AiRemediationPlanDto approvePlan(UUID workspaceId, UUID planId, UUID reviewerId) {
        AiRemediationPlan plan = planRepository.findByIdAndWorkspaceId(planId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Remediation plan not found: " + planId));

        checkExpiration(plan);

        if (plan.getStatus() != AiPlanStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Plan cannot be approved in state: " + plan.getStatus());
        }

        plan.setStatus(AiPlanStatus.APPROVED);
        plan.setReviewedByUserId(reviewerId);
        plan.setUpdatedAt(Instant.now());
        AiRemediationPlan saved = planRepository.save(plan);

        auditService.logSuccess(
                AuditAction.AI_REMEDIATION_PLAN_APPROVED,
                "AI_REMEDIATION_PLAN",
                saved.getId(),
                reviewerId,
                workspaceId,
                "Approved remediation plan: " + saved.getTitle()
        );

        if (securityEventService != null) {
            securityEventService.recordEvent(
                    workspaceId,
                    null,
                    null,
                    reviewerId,
                    SecurityEventType.AI_REMEDIATION_PLAN_APPROVED,
                    SecurityEventSeverity.INFO,
                    SecurityEventOutcome.SUCCESS,
                    "AI_COPILOT",
                    null,
                    null,
                    null,
                    Map.of("planId", saved.getId().toString())
            );
        }

        return toDto(saved);
    }

    @Transactional
    public AiRemediationPlanDto rejectPlan(UUID workspaceId, UUID planId, UUID reviewerId, String reason) {
        AiRemediationPlan plan = planRepository.findByIdAndWorkspaceId(planId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Remediation plan not found: " + planId));

        if (plan.getStatus() != AiPlanStatus.PENDING_APPROVAL && plan.getStatus() != AiPlanStatus.APPROVED) {
            throw new IllegalStateException("Plan cannot be rejected in state: " + plan.getStatus());
        }

        plan.setStatus(AiPlanStatus.REJECTED);
        plan.setReviewedByUserId(reviewerId);
        plan.setFeedbackComment(reason);
        plan.setUpdatedAt(Instant.now());
        AiRemediationPlan saved = planRepository.save(plan);

        auditService.logSuccess(
                AuditAction.AI_REMEDIATION_PLAN_REJECTED,
                "AI_REMEDIATION_PLAN",
                saved.getId(),
                reviewerId,
                workspaceId,
                "Rejected remediation plan: " + saved.getTitle() + " (Reason: " + reason + ")"
        );

        if (securityEventService != null) {
            securityEventService.recordEvent(
                    workspaceId,
                    null,
                    null,
                    reviewerId,
                    SecurityEventType.AI_REMEDIATION_PLAN_REJECTED,
                    SecurityEventSeverity.INFO,
                    SecurityEventOutcome.SUCCESS,
                    "AI_COPILOT",
                    null,
                    null,
                    null,
                    Map.of("planId", saved.getId().toString(), "reason", reason != null ? reason : "")
            );
        }

        return toDto(saved);
    }

    @Transactional
    public AiRemediationPlanDto recordFeedback(UUID workspaceId, UUID planId, int rating, String comment) {
        AiRemediationPlan plan = planRepository.findByIdAndWorkspaceId(planId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Remediation plan not found: " + planId));

        plan.setFeedbackRating(rating);
        plan.setFeedbackComment(comment);
        plan.setUpdatedAt(Instant.now());
        AiRemediationPlan saved = planRepository.save(plan);

        auditService.logSuccess(
                AuditAction.AI_PLAN_FEEDBACK_RECORDED,
                "AI_REMEDIATION_PLAN",
                saved.getId(),
                plan.getReviewedByUserId(),
                workspaceId,
                "Recorded feedback rating " + rating + "/5 for plan " + saved.getId()
        );

        return toDto(saved);
    }

    private void checkExpiration(AiRemediationPlan plan) {
        if (plan.getStatus() == AiPlanStatus.PENDING_APPROVAL && Instant.now().isAfter(plan.getExpiresAt())) {
            plan.setStatus(AiPlanStatus.EXPIRED);
            plan.setUpdatedAt(Instant.now());
            planRepository.save(plan);
        }
    }

    private void expireStalePlans(UUID workspaceId) {
        List<AiRemediationPlan> expired = planRepository.findByStatusAndExpiresAtBefore(AiPlanStatus.PENDING_APPROVAL, Instant.now());
        for (AiRemediationPlan p : expired) {
            p.setStatus(AiPlanStatus.EXPIRED);
            p.setUpdatedAt(Instant.now());
            planRepository.save(p);
        }
    }

    public AiRemediationPlanDto toDto(AiRemediationPlan entity) {
        List<RemediationStep> steps = new ArrayList<>();
        BlastRadiusImpact impact = null;

        try {
            if (entity.getRemediationStepsJson() != null) {
                steps = objectMapper.readValue(entity.getRemediationStepsJson(), new TypeReference<List<RemediationStep>>() {});
            }
            if (entity.getBlastRadiusJson() != null) {
                impact = objectMapper.readValue(entity.getBlastRadiusJson(), BlastRadiusImpact.class);
            }
        } catch (Exception ignored) {
        }

        return new AiRemediationPlanDto(
                entity.getId(),
                entity.getRcaReportId(),
                entity.getPlanType(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getRiskLevel(),
                entity.getConfidenceScore(),
                entity.getTargetResourceType(),
                entity.getTargetResourceId(),
                steps,
                entity.getPayloadDiffJson(),
                impact,
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getFeedbackRating(),
                entity.getFeedbackComment(),
                entity.getCreatedAt()
        );
    }
}
