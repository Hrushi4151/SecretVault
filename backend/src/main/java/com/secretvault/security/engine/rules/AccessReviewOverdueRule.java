package com.secretvault.security.engine.rules;

import com.secretvault.access.review.entity.AccessReviewCampaign;
import com.secretvault.access.review.entity.CampaignStatus;
import com.secretvault.access.review.repository.AccessReviewCampaignRepository;
import com.secretvault.security.engine.SecurityDetectionRule;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.workspace.entity.Workspace;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AccessReviewOverdueRule implements SecurityDetectionRule {

    private final AccessReviewCampaignRepository campaignRepository;

    public AccessReviewOverdueRule(AccessReviewCampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.ACCESS_REVIEW_OVERDUE;
    }

    @Override
    public String getRuleName() {
        return "Access Review Campaign Overdue Detection Rule";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> findings = new ArrayList<>();
        UUID wsId = workspace.getId();

        List<AccessReviewCampaign> campaigns = campaignRepository.findByWorkspaceId(wsId);
        for (AccessReviewCampaign campaign : campaigns) {
            if (campaign.getStatus() == CampaignStatus.OPEN || campaign.getStatus() == CampaignStatus.IN_PROGRESS) {
                if (campaign.getDueDate().isBefore(evaluationTime)) {
                    int pendingItems = campaign.getTotalItemsCount() - campaign.getDecidedItemsCount();
                    Map<String, Object> evidence = Map.of(
                            "campaignId", campaign.getId().toString(),
                            "campaignName", campaign.getName(),
                            "dueDate", campaign.getDueDate().toString(),
                            "totalItems", campaign.getTotalItemsCount(),
                            "decidedItems", campaign.getDecidedItemsCount(),
                            "pendingItems", pendingItems
                    );

                    findings.add(new SecurityFindingDraft(
                            wsId,
                            campaign.getProjectId(),
                            campaign.getEnvironmentId(),
                            FindingCategory.ACCESS_REVIEW_OVERDUE,
                            FindingSeverity.HIGH,
                            FindingConfidence.HIGH,
                            "Access Review Campaign Overdue: " + campaign.getName(),
                            "Governance campaign '" + campaign.getName() + "' passed its scheduled due date with " + pendingItems + " pending certification items.",
                            "Review and decide all pending access items in the Access Review Workbench to finalize certification.",
                            "campaign:" + campaign.getId(),
                            evidence
                    ));
                }
            }
        }

        return findings;
    }
}
