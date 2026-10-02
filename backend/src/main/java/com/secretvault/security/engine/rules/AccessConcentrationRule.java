package com.secretvault.security.engine.rules;

import com.secretvault.security.engine.SecurityDetectionRule;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AccessConcentrationRule implements SecurityDetectionRule {

    private final WorkspaceMembershipRepository membershipRepository;

    public AccessConcentrationRule(WorkspaceMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.ACCESS_CONCENTRATION;
    }

    @Override
    public String getRuleName() {
        return "Access Concentration Risk Detection Rule";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> findings = new ArrayList<>();
        UUID wsId = workspace.getId();

        List<WorkspaceMembership> members = membershipRepository.findByWorkspaceId(wsId);
        long ownerCount = members.stream().filter(m -> m.getRole() == WorkspaceRole.OWNER).count();
        long adminCount = members.stream().filter(m -> m.getRole() == WorkspaceRole.ADMIN).count();

        if (members.size() >= 3 && ownerCount == 1 && adminCount == 0) {
            WorkspaceMembership soleOwner = members.stream()
                    .filter(m -> m.getRole() == WorkspaceRole.OWNER)
                    .findFirst()
                    .orElse(null);

            Map<String, Object> evidence = Map.of(
                    "soleOwnerUserId", soleOwner != null ? soleOwner.getUserId().toString() : "unknown",
                    "totalMembers", members.size(),
                    "ownerCount", ownerCount,
                    "adminCount", adminCount
            );

            findings.add(new SecurityFindingDraft(
                    wsId,
                    null,
                    null,
                    FindingCategory.ACCESS_CONCENTRATION,
                    FindingSeverity.MEDIUM,
                    FindingConfidence.HIGH,
                    "Administrative Access Concentration (Single Point of Failure)",
                    "Workspace contains " + members.size() + " members but only 1 administrative owner and 0 administrators.",
                    "Designate a secondary trusted administrator to ensure business continuity and dual-custody access governance.",
                    "workspace:concentration",
                    evidence
            ));
        }

        return findings;
    }
}
