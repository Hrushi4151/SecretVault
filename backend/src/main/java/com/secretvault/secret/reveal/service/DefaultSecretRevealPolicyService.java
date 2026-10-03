package com.secretvault.secret.reveal.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.secret.reveal.dto.SecretRevealPolicyResponse;
import com.secretvault.secret.reveal.dto.UpdateSecretRevealPolicyRequest;
import com.secretvault.secret.reveal.entity.SecretRevealPolicy;
import com.secretvault.secret.reveal.model.RevealPolicyLevel;
import com.secretvault.secret.reveal.model.SecretRevealPolicyEvaluation;
import com.secretvault.secret.reveal.repository.SecretRevealPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DefaultSecretRevealPolicyService implements SecretRevealPolicyService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSecretRevealPolicyService.class);

    private static final Set<String> TRIVIAL_REASONS = Set.of(
            "test", "testing", "asdf", "qwerty", "secret", "password", "none", "n/a", "na",
            "just testing", "for testing", "test reason", "reveal secret", "reveal value", "admin"
    );

    private static final Pattern REPETITIVE_CHAR_PATTERN = Pattern.compile("^(.)\\1+$");

    private final SecretRevealPolicyRepository policyRepository;
    private final EnvironmentRepository environmentRepository;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;

    public DefaultSecretRevealPolicyService(
            SecretRevealPolicyRepository policyRepository,
            EnvironmentRepository environmentRepository,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService
    ) {
        this.policyRepository = policyRepository;
        this.environmentRepository = environmentRepository;
        this.effectiveAccessService = effectiveAccessService;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public SecretRevealPolicyEvaluation evaluatePolicy(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID userId
    ) {
        // 1. Check if an explicit custom policy exists in hierarchy
        List<SecretRevealPolicy> matchingPolicies = policyRepository.findMatchingPolicies(
                workspaceId, projectId, environmentId, secretId
        );

        if (!matchingPolicies.isEmpty()) {
            SecretRevealPolicy custom = matchingPolicies.get(0);
            List<String> factors = custom.getAllowedStepUpFactors() != null
                    ? Arrays.stream(custom.getAllowedStepUpFactors().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList()
                    : List.of("PASSWORD", "TOTP", "RECOVERY_CODE", "WEBAUTHN");

            return new SecretRevealPolicyEvaluation(
                    custom.getPolicyLevel(),
                    custom.isRequireStepUp(),
                    factors,
                    custom.isRequireWebAuthnOnly(),
                    custom.isRequireReason(),
                    custom.getMinReasonLength(),
                    custom.getMaxReasonLength(),
                    custom.isRequirePrivilegedOrJit(),
                    custom.getMaxDisplayDurationSeconds(),
                    custom.isCopyAllowed(),
                    custom.getClipboardTimeoutSeconds(),
                    custom.isBulkRevealAllowed(),
                    custom.getMaxBulkCount(),
                    custom.getRateLimitPerMinute()
            );
        }

        // 2. Fall back to environment-based defaults
        if (environmentId != null) {
            Optional<Environment> envOpt = environmentRepository.findById(environmentId);
            if (envOpt.isPresent()) {
                Environment env = envOpt.get();
                if (env.getEnvType() == EnvType.PRODUCTION || env.isProtected()) {
                    return SecretRevealPolicyEvaluation.forProduction();
                } else if (env.getEnvType() == EnvType.STAGING) {
                    return SecretRevealPolicyEvaluation.forSensitive();
                }
            }
        }

        return SecretRevealPolicyEvaluation.defaultPolicy();
    }

    @Override
    public void validateReason(String reason, SecretRevealPolicyEvaluation policy) {
        if (!policy.requireReason()) {
            return;
        }

        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("Reveal justification reason is mandatory for this protected environment");
        }

        String trimmed = reason.trim();

        if (trimmed.length() < policy.minReasonLength()) {
            throw ApiException.badRequest("Reveal justification reason must be at least " + policy.minReasonLength() + " characters");
        }

        if (trimmed.length() > policy.maxReasonLength()) {
            throw ApiException.badRequest("Reveal justification reason must not exceed " + policy.maxReasonLength() + " characters");
        }

        if (TRIVIAL_REASONS.contains(trimmed.toLowerCase())) {
            throw ApiException.badRequest("A meaningful, descriptive justification reason is required");
        }

        if (REPETITIVE_CHAR_PATTERN.matcher(trimmed).matches()) {
            throw ApiException.badRequest("A meaningful, non-repetitive justification reason is required");
        }

        if (trimmed.chars().distinct().count() <= 3 && trimmed.length() >= 8) {
            throw ApiException.badRequest("A meaningful justification reason is required");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SecretRevealPolicyResponse> getWorkspacePolicies(UUID workspaceId, UUID actorUserId) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, actorUserId);
        return policyRepository.findByWorkspaceId(workspaceId).stream()
                .map(SecretRevealPolicyResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public SecretRevealPolicyResponse setPolicy(
            UUID workspaceId,
            UpdateSecretRevealPolicyRequest request,
            UUID actorUserId
    ) {
        effectiveAccessService.checkPermission(workspaceId, request.projectId(), request.environmentId(), request.secretId(),
                AccessPermission.ACCESS_MANAGE, actorUserId);

        SecretRevealPolicy policy = resolveExistingOrCreate(workspaceId, request);

        if (request.policyLevel() != null) policy.setPolicyLevel(request.policyLevel());
        if (request.requireStepUp() != null) policy.setRequireStepUp(request.requireStepUp());
        if (request.allowedStepUpFactors() != null) policy.setAllowedStepUpFactors(request.allowedStepUpFactors());
        if (request.requireWebAuthnOnly() != null) policy.setRequireWebAuthnOnly(request.requireWebAuthnOnly());
        if (request.requireReason() != null) policy.setRequireReason(request.requireReason());
        if (request.minReasonLength() != null) policy.setMinReasonLength(request.minReasonLength());
        if (request.maxReasonLength() != null) policy.setMaxReasonLength(request.maxReasonLength());
        if (request.requirePrivilegedOrJit() != null) policy.setRequirePrivilegedOrJit(request.requirePrivilegedOrJit());
        if (request.maxDisplayDurationSeconds() != null) policy.setMaxDisplayDurationSeconds(request.maxDisplayDurationSeconds());
        if (request.copyAllowed() != null) policy.setCopyAllowed(request.copyAllowed());
        if (request.clipboardTimeoutSeconds() != null) policy.setClipboardTimeoutSeconds(request.clipboardTimeoutSeconds());
        if (request.bulkRevealAllowed() != null) policy.setBulkRevealAllowed(request.bulkRevealAllowed());
        if (request.maxBulkCount() != null) policy.setMaxBulkCount(request.maxBulkCount());
        if (request.rateLimitPerMinute() != null) policy.setRateLimitPerMinute(request.rateLimitPerMinute());
        if (request.enabled() != null) policy.setEnabled(request.enabled());
        policy.setUpdatedAt(Instant.now());

        SecretRevealPolicy saved = policyRepository.save(policy);

        auditService.recordAudit(
                null,
                workspaceId,
                actorUserId,
                "USER",
                AuditAction.SECRET_REVEAL_POLICY_UPDATED,
                "SECRET_REVEAL_POLICY",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.info("Updated secret reveal policy [{}] for workspace [{}] scope [{}] by actor [{}]",
                saved.getId(), workspaceId, saved.getScopeType(), actorUserId);

        return SecretRevealPolicyResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deletePolicy(UUID workspaceId, UUID policyId, UUID actorUserId) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null,
                AccessPermission.ACCESS_MANAGE, actorUserId);

        SecretRevealPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> ApiException.notFound("Secret reveal policy not found"));

        if (!policy.getWorkspaceId().equals(workspaceId)) {
            throw ApiException.forbidden("Cross-tenant policy access denied");
        }

        policyRepository.delete(policy);

        auditService.recordAudit(
                null,
                workspaceId,
                actorUserId,
                "USER",
                AuditAction.SECRET_REVEAL_POLICY_UPDATED,
                "SECRET_REVEAL_POLICY",
                policyId,
                null,
                null,
                "DELETED"
        );
    }

    private SecretRevealPolicy resolveExistingOrCreate(UUID workspaceId, UpdateSecretRevealPolicyRequest request) {
        PrivilegedPolicyScope scope = request.scopeType();
        Optional<SecretRevealPolicy> existing = switch (scope) {
            case WORKSPACE -> policyRepository.findByWorkspaceIdAndScopeType(workspaceId, scope);
            case PROJECT -> policyRepository.findByWorkspaceIdAndProjectIdAndScopeType(workspaceId, request.projectId(), scope);
            case ENVIRONMENT -> policyRepository.findByWorkspaceIdAndEnvironmentIdAndScopeType(workspaceId, request.environmentId(), scope);
            case SECRET -> policyRepository.findByWorkspaceIdAndSecretIdAndScopeType(workspaceId, request.secretId(), scope);
        };

        if (existing.isPresent()) {
            return existing.get();
        }

        SecretRevealPolicy policy = new SecretRevealPolicy();
        policy.setWorkspaceId(workspaceId);
        policy.setScopeType(scope);
        policy.setProjectId(request.projectId());
        policy.setEnvironmentId(request.environmentId());
        policy.setSecretId(request.secretId());
        return policy;
    }
}
