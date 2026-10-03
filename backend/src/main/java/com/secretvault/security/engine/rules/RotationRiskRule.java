package com.secretvault.security.engine.rules;

import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.repository.RotationJobRepository;
import com.secretvault.rotation.repository.RotationPolicyRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.repository.SecretRepository;
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
import java.util.UUID;

/**
 * Security Intelligence rule detecting unrotated production secrets, overdue rotation schedules,
 * failing rotation jobs, and compromised secret lifecycle states.
 */
@Component
public class RotationRiskRule implements SecurityDetectionRule {

    private final SecretRepository secretRepository;
    private final RotationPolicyRepository policyRepository;
    private final RotationJobRepository jobRepository;
    private final EnvironmentRepository environmentRepository;

    public RotationRiskRule(
            SecretRepository secretRepository,
            RotationPolicyRepository policyRepository,
            RotationJobRepository jobRepository,
            EnvironmentRepository environmentRepository) {
        this.secretRepository = secretRepository;
        this.policyRepository = policyRepository;
        this.jobRepository = jobRepository;
        this.environmentRepository = environmentRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.SECRET_ROTATION_RISK;
    }

    @Override
    public String getRuleName() {
        return "SecretRotationLifecycleRiskAnalyzer";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> drafts = new ArrayList<>();

        List<RotationPolicy> policies = policyRepository.findByWorkspaceId(workspace.getId());
        for (RotationPolicy policy : policies) {
            Optional<Secret> secretOpt = secretRepository.findById(policy.getSecretId());
            if (secretOpt.isEmpty() || secretOpt.get().getStatus() == SecretStatus.DELETED) {
                continue;
            }
            Secret secret = secretOpt.get();
            Optional<Environment> envOpt = environmentRepository.findById(secret.getEnvironmentId());
            if (envOpt.isEmpty()) {
                continue;
            }
            Environment env = envOpt.get();

            // Check if rotation is overdue
            if (policy.isEnabled() && policy.getNextRotationDueAt() != null && policy.getNextRotationDueAt().isBefore(evaluationTime)) {
                drafts.add(new SecurityFindingDraft(
                        workspace.getId(),
                        env.getProjectId(),
                        secret.getEnvironmentId(),
                        FindingCategory.SECRET_ROTATION_RISK,
                        FindingSeverity.HIGH,
                        FindingConfidence.HIGH,
                        "Overdue Secret Rotation: [" + secret.getName() + "]",
                        "Secret '" + secret.getName() + "' (ID: " + secret.getId() + ") is overdue for rotation. Next rotation was scheduled at " + policy.getNextRotationDueAt() + ".",
                        "Trigger a manual or scheduled rotation for secret '" + secret.getName() + "' to comply with rotation policy.",
                        "rotation-overdue:" + policy.getId(),
                        Map.of(
                                "secretId", secret.getId().toString(),
                                "policyId", policy.getId().toString(),
                                "riskType", "OVERDUE_ROTATION"
                        )
                ));
            }

            // Check if rotation policy is disabled in a production environment
            if (!policy.isEnabled()) {
                if (env.getEnvType() == EnvType.PRODUCTION) {
                    drafts.add(new SecurityFindingDraft(
                            workspace.getId(),
                            env.getProjectId(),
                            secret.getEnvironmentId(),
                            FindingCategory.SECRET_ROTATION_RISK,
                            FindingSeverity.MEDIUM,
                            FindingConfidence.HIGH,
                            "Disabled Rotation Policy in Production: [" + secret.getName() + "]",
                            "Secret '" + secret.getName() + "' in production environment '" + env.getName() + "' has rotation disabled.",
                            "Enable automated rotation policy to protect critical production credentials.",
                            "rotation-disabled-prod:" + policy.getId(),
                            Map.of(
                                    "secretId", secret.getId().toString(),
                                    "policyId", policy.getId().toString(),
                                    "riskType", "DISABLED_PRODUCTION_ROTATION"
                            )
                    ));
                }
            }
        }

        // Check recent failed rotation jobs
        List<RotationJob> jobs = jobRepository.findByWorkspaceId(workspace.getId());
        for (RotationJob job : jobs) {
            if (job.getStatus() == RotationStatus.FAILED || job.getStatus() == RotationStatus.VALIDATION_FAILED) {
                Optional<Secret> secretOpt = secretRepository.findById(job.getSecretId());
                UUID projectId = null;
                UUID envId = null;
                if (secretOpt.isPresent()) {
                    envId = secretOpt.get().getEnvironmentId();
                    projectId = environmentRepository.findById(envId).map(Environment::getProjectId).orElse(null);
                }

                drafts.add(new SecurityFindingDraft(
                        workspace.getId(),
                        projectId,
                        envId,
                        FindingCategory.SECRET_ROTATION_RISK,
                        FindingSeverity.HIGH,
                        FindingConfidence.HIGH,
                        "Failed Secret Rotation Job: [" + job.getId() + "]",
                        "Rotation job " + job.getId() + " failed with status " + job.getStatus() + ": " + (job.getErrorMessage() != null ? job.getErrorMessage() : "Unknown error"),
                        "Inspect rotation provider diagnostics and retry or rollback rotation job.",
                        "rotation-failed-job:" + job.getId(),
                        Map.of(
                                "jobId", job.getId().toString(),
                                "secretId", job.getSecretId().toString(),
                                "status", job.getStatus().name(),
                                "riskType", "FAILED_ROTATION"
                        )
                ));
            }
        }

        return drafts;
    }
}
