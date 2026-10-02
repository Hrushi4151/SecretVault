package com.secretvault.security.engine.rules;

import com.secretvault.oidc.entity.OidcProvider;
import com.secretvault.oidc.entity.OidcTrustPolicy;
import com.secretvault.oidc.model.OidcProviderStatus;
import com.secretvault.oidc.repository.OidcProviderRepository;
import com.secretvault.oidc.repository.OidcTrustPolicyRepository;
import com.secretvault.oidc.service.OidcTrustPolicyService;
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
public class OverlyBroadTrustPolicyRule implements SecurityDetectionRule {

    private final OidcTrustPolicyRepository policyRepository;
    private final OidcProviderRepository providerRepository;

    public OverlyBroadTrustPolicyRule(
            OidcTrustPolicyRepository policyRepository,
            OidcProviderRepository providerRepository
    ) {
        this.policyRepository = policyRepository;
        this.providerRepository = providerRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.OVERLY_BROAD_TRUST_POLICY;
    }

    @Override
    public String getRuleName() {
        return "OverlyBroadOidcTrustPolicyAnalyzer";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> drafts = new ArrayList<>();
        List<OidcTrustPolicy> policies = policyRepository.findByWorkspaceId(workspace.getId());

        for (OidcTrustPolicy policy : policies) {
            if (!policy.isEnabled()) {
                continue;
            }

            Optional<OidcProvider> providerOpt = providerRepository.findById(policy.getOidcProviderId());
            if (providerOpt.isPresent() && providerOpt.get().getStatus() == OidcProviderStatus.DISABLED) {
                drafts.add(new SecurityFindingDraft(
                        workspace.getId(),
                        null,
                        null,
                        FindingCategory.OVERLY_BROAD_TRUST_POLICY,
                        FindingSeverity.MEDIUM,
                        FindingConfidence.HIGH,
                        "Trust Policy References Disabled Provider: [" + policy.getName() + "]",
                        "Active trust policy '" + policy.getName() + "' references OIDC provider '" + providerOpt.get().getName() + "' which is currently DISABLED.",
                        "Either re-enable the OIDC provider or disable the unused trust policy.",
                        "policy-disabled-provider:" + policy.getId(),
                        Map.of(
                                "trustPolicyId", policy.getId().toString(),
                                "providerId", providerOpt.get().getId().toString(),
                                "riskType", "DISABLED_PROVIDER_REFERENCED"
                        )
                ));
            }

            if (OidcTrustPolicyService.isBroadPolicy(policy)) {
                drafts.add(new SecurityFindingDraft(
                        workspace.getId(),
                        null,
                        null,
                        FindingCategory.OVERLY_BROAD_TRUST_POLICY,
                        FindingSeverity.HIGH,
                        FindingConfidence.HIGH,
                        "Overly Broad OIDC Trust Policy: [" + policy.getName() + "]",
                        "Trust policy '" + policy.getName() + "' lacks specific repository or branch claim constraints. Workloads across multiple repositories or branches may inadvertently match this policy.",
                        "Add explicit claim rules restricting the repository (e.g. 'repository EQUALS company/repo') and branch (e.g. 'ref EQUALS refs/heads/main').",
                        "policy-broad:" + policy.getId(),
                        Map.of(
                                "trustPolicyId", policy.getId().toString(),
                                "policyName", policy.getName(),
                                "riskType", "BROAD_OIDC_POLICY"
                        )
                ));
            }
        }

        return drafts;
    }
}
