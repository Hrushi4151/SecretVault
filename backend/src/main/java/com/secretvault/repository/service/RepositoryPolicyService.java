package com.secretvault.repository.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.repository.entity.RepositorySecurityPolicy;
import com.secretvault.repository.entity.SecretFinding;
import com.secretvault.repository.model.RepoFindingSeverity;
import com.secretvault.repository.model.RepoFindingStatus;
import com.secretvault.repository.repository.RepositorySecurityPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class RepositoryPolicyService {

    private final RepositorySecurityPolicyRepository policyRepository;
    private final EffectiveAccessService accessService;
    private final AuditService auditService;

    public RepositoryPolicyService(
            RepositorySecurityPolicyRepository policyRepository,
            EffectiveAccessService accessService,
            AuditService auditService) {
        this.policyRepository = policyRepository;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    public enum SecurityGateDecision {
        PASS,
        WARN,
        BLOCK
    }

    public record SecurityGateResult(
            SecurityGateDecision decision,
            String reason,
            int criticalCount,
            int highCount,
            int mediumCount,
            int lowCount
    ) {}

    @Transactional(readOnly = true)
    public RepositorySecurityPolicy getEffectivePolicy(UUID workspaceId, UUID repositoryId) {
        if (repositoryId != null) {
            Optional<RepositorySecurityPolicy> repoPolicy = policyRepository.findByWorkspaceIdAndRepositoryId(workspaceId, repositoryId);
            if (repoPolicy.isPresent()) {
                return repoPolicy.get();
            }
        }

        // Fallback to workspace-level policy
        List<RepositorySecurityPolicy> wsPolicies = policyRepository.findByWorkspaceIdAndRepositoryIdIsNull(workspaceId);
        if (!wsPolicies.isEmpty()) {
            return wsPolicies.get(0);
        }

        // Return sensible default policy
        RepositorySecurityPolicy defaultPolicy = new RepositorySecurityPolicy();
        defaultPolicy.setWorkspaceId(workspaceId);
        defaultPolicy.setRepositoryId(repositoryId);
        defaultPolicy.setScanOnPush(true);
        defaultPolicy.setScanPr(true);
        defaultPolicy.setScanHistory(true);
        defaultPolicy.setEntropyDetection(true);
        defaultPolicy.setProviderValidation(false);
        defaultPolicy.setFailCiThreshold("HIGH");
        defaultPolicy.setMaxHistoryDepth(500);
        defaultPolicy.setMaxFileSizeBytes(5 * 1024 * 1024L);
        return defaultPolicy;
    }

    @Transactional
    public RepositorySecurityPolicy savePolicy(
            UUID workspaceId,
            UUID repositoryId,
            RepositorySecurityPolicy policyData,
            UUID actorId) {

        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_MANAGE);

        Optional<RepositorySecurityPolicy> existingOpt = repositoryId != null
                ? policyRepository.findByWorkspaceIdAndRepositoryId(workspaceId, repositoryId)
                : policyRepository.findByWorkspaceIdAndRepositoryIdIsNull(workspaceId).stream().findFirst();

        RepositorySecurityPolicy policy = existingOpt.orElseGet(RepositorySecurityPolicy::new);
        policy.setWorkspaceId(workspaceId);
        policy.setRepositoryId(repositoryId);
        policy.setScanOnPush(policyData.isScanOnPush());
        policy.setScanPr(policyData.isScanPr());
        policy.setScanHistory(policyData.isScanHistory());
        policy.setEntropyDetection(policyData.isEntropyDetection());
        policy.setProviderValidation(policyData.isProviderValidation());
        policy.setFailCiThreshold(policyData.getFailCiThreshold() != null ? policyData.getFailCiThreshold() : "HIGH");
        policy.setMaxHistoryDepth(policyData.getMaxHistoryDepth() > 0 ? policyData.getMaxHistoryDepth() : 500);
        policy.setMaxFileSizeBytes(policyData.getMaxFileSizeBytes() > 0 ? policyData.getMaxFileSizeBytes() : 5242880L);
        policy.setExcludedPaths(policyData.getExcludedPaths());
        policy.setAllowedDetectors(policyData.getAllowedDetectors());
        policy.setUpdatedAt(Instant.now());

        RepositorySecurityPolicy saved = policyRepository.save(policy);

        auditService.record(
                workspaceId,
                actorId,
                AuditAction.ALLOWLIST_CHANGED,
                "REPOSITORY_POLICY",
                saved.getId().toString(),
                Map.of("repositoryId", repositoryId != null ? repositoryId.toString() : "workspace-default",
                        "failCiThreshold", saved.getFailCiThreshold())
        );

        return saved;
    }

    public SecurityGateResult evaluateSecurityGate(
            RepositorySecurityPolicy policy,
            List<SecretFinding> findings) {

        int critical = 0;
        int high = 0;
        int medium = 0;
        int low = 0;

        for (SecretFinding f : findings) {
            if (f.getStatus() == RepoFindingStatus.FALSE_POSITIVE || f.getStatus() == RepoFindingStatus.RESOLVED) {
                continue;
            }
            switch (f.getSeverity()) {
                case CRITICAL -> critical++;
                case HIGH -> high++;
                case MEDIUM -> medium++;
                case LOW, INFO -> low++;
            }
        }

        String threshold = policy.getFailCiThreshold() != null ? policy.getFailCiThreshold().toUpperCase(Locale.ROOT) : "HIGH";

        boolean blocked = false;
        String reason = "Security gate passed.";

        switch (threshold) {
            case "CRITICAL" -> {
                if (critical > 0) {
                    blocked = true;
                    reason = "Blocked: " + critical + " CRITICAL secret finding(s) detected.";
                }
            }
            case "HIGH" -> {
                if (critical > 0 || high > 0) {
                    blocked = true;
                    reason = "Blocked: " + (critical + high) + " CRITICAL/HIGH secret finding(s) detected.";
                }
            }
            case "MEDIUM" -> {
                if (critical > 0 || high > 0 || medium > 0) {
                    blocked = true;
                    reason = "Blocked: " + (critical + high + medium) + " CRITICAL/HIGH/MEDIUM secret finding(s) detected.";
                }
            }
            case "LOW" -> {
                if (critical > 0 || high > 0 || medium > 0 || low > 0) {
                    blocked = true;
                    reason = "Blocked: Secret finding(s) detected above LOW threshold.";
                }
            }
            case "NONE" -> {
                // Do not block
            }
        }

        if (blocked) {
            return new SecurityGateResult(SecurityGateDecision.BLOCK, reason, critical, high, medium, low);
        } else if (critical > 0 || high > 0 || medium > 0) {
            return new SecurityGateResult(
                    SecurityGateDecision.WARN,
                    "Warning: Active findings detected, but threshold is not exceeded.",
                    critical, high, medium, low
            );
        }

        return new SecurityGateResult(SecurityGateDecision.PASS, reason, critical, high, medium, low);
    }
}
