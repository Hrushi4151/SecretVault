package com.secretvault.security.engine.rules;

import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.machine.entity.MachineAccessGrant;
import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.model.MachineStatus;
import com.secretvault.machine.repository.MachineAccessGrantRepository;
import com.secretvault.machine.repository.MachineIdentityRepository;
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
import java.util.Optional;

@Component
public class MachineIdentityRiskRule implements SecurityDetectionRule {

    private final MachineIdentityRepository machineRepository;
    private final MachineAccessGrantRepository grantRepository;
    private final EnvironmentRepository environmentRepository;

    public MachineIdentityRiskRule(
            MachineIdentityRepository machineRepository,
            MachineAccessGrantRepository grantRepository,
            EnvironmentRepository environmentRepository
    ) {
        this.machineRepository = machineRepository;
        this.grantRepository = grantRepository;
        this.environmentRepository = environmentRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.MACHINE_IDENTITY_RISK;
    }

    @Override
    public String getRuleName() {
        return "MachineIdentitySecurityRiskAnalyzer";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> drafts = new ArrayList<>();
        List<MachineIdentity> machines = machineRepository.findByWorkspaceIdAndDeletedAtIsNull(workspace.getId());

        for (MachineIdentity machine : machines) {
            if (machine.getStatus() != MachineStatus.ACTIVE) {
                continue;
            }

            // 1. Missing Expiration Date Check
            if (machine.getExpiresAt() == null) {
                drafts.add(new SecurityFindingDraft(
                        workspace.getId(),
                        null,
                        null,
                        FindingCategory.MACHINE_IDENTITY_RISK,
                        FindingSeverity.MEDIUM,
                        FindingConfidence.HIGH,
                        "Machine Identity Without Expiration: [" + machine.getName() + "]",
                        "Machine identity '" + machine.getName() + "' (ID: " + machine.getId() + ") has no configured expiration date. Non-expiring credentials increase long-term compromise risk.",
                        "Configure an explicit expiration date (e.g. 90 days) for machine identity '" + machine.getName() + "'.",
                        "machine-no-expiry:" + machine.getId(),
                        Map.of(
                                "machineIdentityId", machine.getId().toString(),
                                "machineName", machine.getName(),
                                "riskType", "NO_EXPIRATION"
                        )
                ));
            }

            // 2. Scan machine grants
            List<MachineAccessGrant> grants = grantRepository.findByWorkspaceIdAndMachineIdentityId(workspace.getId(), machine.getId());
            for (MachineAccessGrant grant : grants) {
                if (!"ALLOW".equalsIgnoreCase(grant.getEffect())) {
                    continue;
                }

                // Workspace-wide access grant
                if (grant.getScopeType() == com.secretvault.access.model.AccessScope.WORKSPACE) {
                    drafts.add(new SecurityFindingDraft(
                            workspace.getId(),
                            null,
                            null,
                            FindingCategory.MACHINE_IDENTITY_RISK,
                            FindingSeverity.HIGH,
                            FindingConfidence.HIGH,
                            "Workspace-Wide Machine Grant: [" + machine.getName() + "]",
                            "Machine identity '" + machine.getName() + "' has an unrestricted workspace-level permission grant: '" + grant.getPermission() + "'.",
                            "Scope machine permissions down to specific projects and environments following the principle of least privilege.",
                            "machine-ws-grant:" + machine.getId() + ":" + grant.getId(),
                            Map.of(
                                    "machineIdentityId", machine.getId().toString(),
                                    "grantId", grant.getId().toString(),
                                    "permission", grant.getPermission(),
                                    "riskType", "WORKSPACE_WIDE_GRANT"
                            )
                    ));
                }

                // Production Secret Reveal Check
                if ("secret.reveal".equalsIgnoreCase(grant.getPermission()) || "*".equals(grant.getPermission())) {
                    if (grant.getEnvironmentId() != null) {
                        Optional<Environment> envOpt = environmentRepository.findById(grant.getEnvironmentId());
                        if (envOpt.isPresent() && envOpt.get().getEnvType() == EnvType.PRODUCTION) {
                            drafts.add(new SecurityFindingDraft(
                                    workspace.getId(),
                                    grant.getProjectId(),
                                    grant.getEnvironmentId(),
                                    FindingCategory.MACHINE_PRODUCTION_REVEAL,
                                    FindingSeverity.HIGH,
                                    FindingConfidence.HIGH,
                                    "Machine Production Secret Reveal: [" + machine.getName() + "]",
                                    "Machine identity '" + machine.getName() + "' holds standing secret.reveal permission on Production environment [" + envOpt.get().getName() + "].",
                                    "Review whether this CI/CD workload requires standing reveal access or if JIT elevation should be used instead.",
                                    "machine-prod-reveal:" + machine.getId() + ":" + grant.getEnvironmentId(),
                                    Map.of(
                                            "machineIdentityId", machine.getId().toString(),
                                            "environmentId", grant.getEnvironmentId().toString(),
                                            "environmentName", envOpt.get().getName(),
                                            "riskType", "PRODUCTION_REVEAL"
                                    )
                            ));
                        }
                    }
                }
            }
        }

        return drafts;
    }
}
