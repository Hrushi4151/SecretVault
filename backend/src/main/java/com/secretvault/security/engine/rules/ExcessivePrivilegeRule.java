package com.secretvault.security.engine.rules;

import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.environment.access.entity.EnvironmentAccess;
import com.secretvault.environment.access.entity.PermissionLevel;
import com.secretvault.environment.access.repository.EnvironmentAccessRepository;
import com.secretvault.project.access.entity.ProjectAccess;
import com.secretvault.project.access.repository.ProjectAccessRepository;
import com.secretvault.project.repository.ProjectRepository;
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
import java.util.stream.Collectors;

@Component
public class ExcessivePrivilegeRule implements SecurityDetectionRule {

    private final WorkspaceMembershipRepository membershipRepository;
    private final ProjectAccessRepository projectAccessRepository;
    private final EnvironmentAccessRepository environmentAccessRepository;
    private final AccessGrantRepository accessGrantRepository;
    private final ProjectRepository projectRepository;

    public ExcessivePrivilegeRule(
            WorkspaceMembershipRepository membershipRepository,
            ProjectAccessRepository projectAccessRepository,
            EnvironmentAccessRepository environmentAccessRepository,
            AccessGrantRepository accessGrantRepository,
            ProjectRepository projectRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.projectAccessRepository = projectAccessRepository;
        this.environmentAccessRepository = environmentAccessRepository;
        this.accessGrantRepository = accessGrantRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.EXCESSIVE_PRIVILEGE;
    }

    @Override
    public String getRuleName() {
        return "Excessive Privilege Detection Rule";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> findings = new ArrayList<>();
        UUID wsId = workspace.getId();

        List<WorkspaceMembership> members = membershipRepository.findByWorkspaceId(wsId);
        for (WorkspaceMembership member : members) {
            UUID userId = member.getUserId();

            // Check non-owners/admins for sprawling elevated project/env permissions
            if (member.getRole() == WorkspaceRole.DEVELOPER || member.getRole() == WorkspaceRole.VIEWER) {
                List<ProjectAccess> projAccesses = projectAccessRepository.findByUserId(userId).stream()
                        .filter(pa -> pa.getRole() == WorkspaceRole.ADMIN || pa.getRole() == WorkspaceRole.OWNER)
                        .toList();

                List<EnvironmentAccess> envAccesses = environmentAccessRepository.findByUserId(userId).stream()
                        .filter(ea -> ea.getPermissionLevel() == PermissionLevel.MANAGE)
                        .toList();

                List<AccessGrant> grants = accessGrantRepository.findByWorkspaceIdAndUserId(wsId, userId);

                int elevatedCount = projAccesses.size() + envAccesses.size() + grants.size();

                if (elevatedCount >= 3) {
                    Map<String, Object> evidence = Map.of(
                            "userId", userId.toString(),
                            "workspaceRole", member.getRole().name(),
                            "elevatedProjectCount", projAccesses.size(),
                            "elevatedEnvCount", envAccesses.size(),
                            "granularGrantCount", grants.size(),
                            "projectIds", projAccesses.stream().map(pa -> pa.getProjectId().toString()).toList()
                    );

                    findings.add(new SecurityFindingDraft(
                            wsId,
                            null,
                            null,
                            FindingCategory.EXCESSIVE_PRIVILEGE,
                            elevatedCount >= 5 ? FindingSeverity.HIGH : FindingSeverity.MEDIUM,
                            FindingConfidence.HIGH,
                            "Excessive Privileges Assigned to User (" + member.getRole() + ")",
                            "User holds " + elevatedCount + " elevated project, environment, or granular permission grants exceeding standard " + member.getRole() + " scope.",
                            "Conduct an access review to narrow user permissions to the least privilege required for their daily duties.",
                            "user:" + userId,
                            evidence
                    ));
                }
            }
        }

        return findings;
    }
}
