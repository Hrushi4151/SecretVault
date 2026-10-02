package com.secretvault.oidc.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.repository.MachineIdentityRepository;
import com.secretvault.oidc.dto.OidcDtos;
import com.secretvault.oidc.entity.OidcClaimRule;
import com.secretvault.oidc.entity.OidcProvider;
import com.secretvault.oidc.entity.OidcTrustPolicy;
import com.secretvault.oidc.model.OidcClaimOperator;
import com.secretvault.oidc.repository.OidcClaimRuleRepository;
import com.secretvault.oidc.repository.OidcProviderRepository;
import com.secretvault.oidc.repository.OidcTrustPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OidcTrustPolicyService {

    private static final Logger log = LoggerFactory.getLogger(OidcTrustPolicyService.class);

    private final OidcTrustPolicyRepository policyRepository;
    private final OidcClaimRuleRepository claimRuleRepository;
    private final OidcProviderRepository providerRepository;
    private final MachineIdentityRepository machineRepository;
    private final AuditService auditService;

    public OidcTrustPolicyService(
            OidcTrustPolicyRepository policyRepository,
            OidcClaimRuleRepository claimRuleRepository,
            OidcProviderRepository providerRepository,
            MachineIdentityRepository machineRepository,
            AuditService auditService
    ) {
        this.policyRepository = policyRepository;
        this.claimRuleRepository = claimRuleRepository;
        this.providerRepository = providerRepository;
        this.machineRepository = machineRepository;
        this.auditService = auditService;
    }

    @Transactional
    public OidcDtos.OidcTrustPolicyResponse createTrustPolicy(
            UUID workspaceId,
            UUID machineIdentityId,
            OidcDtos.CreateTrustPolicyRequest request,
            UUID actorId
    ) {
        MachineIdentity machine = machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(machineIdentityId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Machine identity not found in this workspace"));

        OidcProvider provider = providerRepository.findByIdAndWorkspaceId(request.oidcProviderId(), workspaceId)
                .orElseThrow(() -> ApiException.notFound("OIDC provider not found in this workspace"));

        if (request.claimRules() == null || request.claimRules().isEmpty()) {
            throw ApiException.badRequest("Trust policy must contain at least one claim rule");
        }

        OidcTrustPolicy policy = new OidcTrustPolicy(
                workspaceId,
                machineIdentityId,
                provider.getId(),
                request.name().trim(),
                request.description(),
                request.enabled() != null ? request.enabled() : true,
                request.priority() != null ? request.priority() : 0,
                actorId
        );

        OidcTrustPolicy savedPolicy = policyRepository.save(policy);

        List<OidcClaimRule> rules = new ArrayList<>();
        for (OidcDtos.ClaimRuleDto ruleDto : request.claimRules()) {
            if (ruleDto.claimName() == null || ruleDto.claimName().isBlank()) {
                throw ApiException.badRequest("Claim rule name cannot be empty");
            }
            if (ruleDto.expectedValue() == null || ruleDto.expectedValue().isBlank()) {
                throw ApiException.badRequest("Claim rule expected value cannot be empty");
            }
            OidcClaimRule rule = new OidcClaimRule(
                    savedPolicy.getId(),
                    ruleDto.claimName().trim().toLowerCase(),
                    ruleDto.operator() != null ? ruleDto.operator() : OidcClaimOperator.EQUALS,
                    ruleDto.expectedValue().trim()
            );
            rules.add(rule);
        }
        claimRuleRepository.saveAll(rules);
        savedPolicy.setClaimRules(rules);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.TRUST_POLICY_CREATED,
                "OIDC_TRUST_POLICY",
                savedPolicy.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.info("Created OIDC trust policy [{}] for machine [{}] with {} rules",
                savedPolicy.getName(), machine.getName(), rules.size());

        return toDto(savedPolicy, provider.getName(), machine.getName());
    }

    @Transactional(readOnly = true)
    public OidcDtos.OidcTrustPolicyResponse getTrustPolicy(UUID id, UUID workspaceId) {
        OidcTrustPolicy policy = policyRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Trust policy not found in this workspace"));

        OidcProvider provider = providerRepository.findById(policy.getOidcProviderId()).orElse(null);
        MachineIdentity machine = machineRepository.findById(policy.getMachineIdentityId()).orElse(null);

        return toDto(policy, provider != null ? provider.getName() : "Unknown Provider",
                machine != null ? machine.getName() : "Unknown Machine");
    }

    @Transactional(readOnly = true)
    public List<OidcDtos.OidcTrustPolicyResponse> listTrustPolicies(UUID workspaceId, UUID machineIdentityId) {
        MachineIdentity machine = machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(machineIdentityId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Machine identity not found"));

        List<OidcTrustPolicy> policies = policyRepository.findByMachineIdentityId(machineIdentityId);
        return policies.stream()
                .map(p -> {
                    OidcProvider provider = providerRepository.findById(p.getOidcProviderId()).orElse(null);
                    return toDto(p, provider != null ? provider.getName() : "Unknown Provider", machine.getName());
                })
                .toList();
    }

    @Transactional
    public OidcDtos.OidcTrustPolicyResponse updateTrustPolicy(
            UUID id,
            UUID workspaceId,
            OidcDtos.UpdateTrustPolicyRequest request,
            UUID actorId
    ) {
        OidcTrustPolicy policy = policyRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Trust policy not found"));

        if (request.name() != null && !request.name().isBlank()) {
            policy.setName(request.name().trim());
        }
        if (request.description() != null) {
            policy.setDescription(request.description());
        }
        if (request.enabled() != null) {
            policy.setEnabled(request.enabled());
        }
        if (request.priority() != null) {
            policy.setPriority(request.priority());
        }

        if (request.claimRules() != null && !request.claimRules().isEmpty()) {
            claimRuleRepository.deleteByTrustPolicyId(policy.getId());
            List<OidcClaimRule> rules = new ArrayList<>();
            for (OidcDtos.ClaimRuleDto ruleDto : request.claimRules()) {
                OidcClaimRule rule = new OidcClaimRule(
                        policy.getId(),
                        ruleDto.claimName().trim().toLowerCase(),
                        ruleDto.operator() != null ? ruleDto.operator() : OidcClaimOperator.EQUALS,
                        ruleDto.expectedValue().trim()
                );
                rules.add(rule);
            }
            claimRuleRepository.saveAll(rules);
            policy.setClaimRules(rules);
        }

        policy.setUpdatedAt(Instant.now());
        OidcTrustPolicy saved = policyRepository.save(policy);

        OidcProvider provider = providerRepository.findById(policy.getOidcProviderId()).orElse(null);
        MachineIdentity machine = machineRepository.findById(policy.getMachineIdentityId()).orElse(null);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.TRUST_POLICY_UPDATED,
                "OIDC_TRUST_POLICY",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        return toDto(saved, provider != null ? provider.getName() : "Unknown Provider",
                machine != null ? machine.getName() : "Unknown Machine");
    }

    @Transactional
    public void deleteTrustPolicy(UUID id, UUID workspaceId, UUID actorId) {
        OidcTrustPolicy policy = policyRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Trust policy not found"));

        policyRepository.delete(policy);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.TRUST_POLICY_DELETED,
                "OIDC_TRUST_POLICY",
                id,
                null,
                null,
                "SUCCESS"
        );

        log.info("Deleted OIDC trust policy [{}] from workspace [{}]", policy.getName(), workspaceId);
    }

    public static String buildNaturalLanguageSummary(OidcTrustPolicy policy, String providerName, String machineName) {
        StringBuilder sb = new StringBuilder();
        sb.append(providerName != null ? providerName : "OIDC Provider");
        sb.append(" workloads matching [");

        List<String> conditions = new ArrayList<>();
        if (policy.getClaimRules() != null) {
            for (OidcClaimRule rule : policy.getClaimRules()) {
                conditions.add(rule.getClaimName() + " " + rule.getOperator() + " '" + rule.getExpectedValue() + "'");
            }
        }
        sb.append(String.join(" AND ", conditions));
        sb.append("] may authenticate as machine identity [").append(machineName).append("].");
        return sb.toString();
    }

    public static boolean isProductionPolicy(OidcTrustPolicy policy) {
        if (policy.getClaimRules() == null) return false;
        for (OidcClaimRule rule : policy.getClaimRules()) {
            if ("environment".equalsIgnoreCase(rule.getClaimName()) &&
                    "production".equalsIgnoreCase(rule.getExpectedValue())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBroadPolicy(OidcTrustPolicy policy) {
        if (policy.getClaimRules() == null || policy.getClaimRules().isEmpty()) {
            return true;
        }
        boolean hasRepo = false;
        boolean hasRefOrEnv = false;
        for (OidcClaimRule rule : policy.getClaimRules()) {
            String name = rule.getClaimName().toLowerCase();
            if (name.contains("repo") || name.contains("project")) {
                if (!rule.getExpectedValue().contains("*") && rule.getOperator() == OidcClaimOperator.EQUALS) {
                    hasRepo = true;
                }
            }
            if (name.contains("ref") || name.contains("branch") || name.contains("environment")) {
                hasRefOrEnv = true;
            }
        }
        return !hasRepo || !hasRefOrEnv;
    }

    private OidcDtos.OidcTrustPolicyResponse toDto(OidcTrustPolicy p, String providerName, String machineName) {
        List<OidcDtos.ClaimRuleDto> ruleDtos = p.getClaimRules() != null
                ? p.getClaimRules().stream()
                .map(r -> new OidcDtos.ClaimRuleDto(r.getId(), r.getClaimName(), r.getOperator(), r.getExpectedValue()))
                .toList()
                : List.of();

        return new OidcDtos.OidcTrustPolicyResponse(
                p.getId(),
                p.getWorkspaceId(),
                p.getMachineIdentityId(),
                p.getOidcProviderId(),
                providerName,
                p.getName(),
                p.getDescription(),
                p.isEnabled(),
                p.getPriority(),
                ruleDtos,
                buildNaturalLanguageSummary(p, providerName, machineName),
                isProductionPolicy(p),
                isBroadPolicy(p),
                p.getCreatedBy(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
