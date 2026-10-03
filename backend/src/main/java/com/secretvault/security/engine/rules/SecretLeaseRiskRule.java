package com.secretvault.security.engine.rules;

import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.model.MachineStatus;
import com.secretvault.machine.repository.MachineIdentityRepository;
import com.secretvault.rotation.entity.SecretConsumer;
import com.secretvault.rotation.entity.SecretLease;
import com.secretvault.rotation.model.ConsumerStatus;
import com.secretvault.rotation.model.LeaseStatus;
import com.secretvault.rotation.repository.SecretConsumerRepository;
import com.secretvault.rotation.repository.SecretLeaseRepository;
import com.secretvault.secret.entity.Secret;
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

/**
 * Security rule detecting long-lived stale secret leases, orphaned leases attached to
 * revoked machine identities, and consumers operating on out-of-date secret versions.
 */
@Component
public class SecretLeaseRiskRule implements SecurityDetectionRule {

    private final SecretLeaseRepository leaseRepository;
    private final SecretConsumerRepository consumerRepository;
    private final MachineIdentityRepository machineRepository;
    private final SecretRepository secretRepository;

    public SecretLeaseRiskRule(
            SecretLeaseRepository leaseRepository,
            SecretConsumerRepository consumerRepository,
            MachineIdentityRepository machineRepository,
            SecretRepository secretRepository) {
        this.leaseRepository = leaseRepository;
        this.consumerRepository = consumerRepository;
        this.machineRepository = machineRepository;
        this.secretRepository = secretRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.SECRET_LEASE_RISK;
    }

    @Override
    public String getRuleName() {
        return "SecretLeaseAndConsumerRiskAnalyzer";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> drafts = new ArrayList<>();

        // 1. Scan active leases
        List<SecretLease> activeLeases = leaseRepository.findByWorkspaceIdAndStatus(workspace.getId(), LeaseStatus.ACTIVE);
        for (SecretLease lease : activeLeases) {
            // Check if attached to disabled/revoked machine
            if (lease.getMachineIdentityId() != null) {
                Optional<MachineIdentity> machineOpt = machineRepository.findById(lease.getMachineIdentityId());
                if (machineOpt.isPresent() && machineOpt.get().getStatus() != MachineStatus.ACTIVE) {
                    drafts.add(new SecurityFindingDraft(
                            workspace.getId(),
                            lease.getProjectId(),
                            lease.getEnvironmentId(),
                            FindingCategory.SECRET_LEASE_RISK,
                            FindingSeverity.HIGH,
                            FindingConfidence.HIGH,
                            "Active Secret Lease on Inactive Machine: [" + lease.getId() + "]",
                            "Secret lease " + lease.getId() + " is currently active, but the associated machine identity '" + machineOpt.get().getName() + "' is " + machineOpt.get().getStatus() + ".",
                            "Revoke the orphaned secret lease immediately.",
                            "lease-inactive-machine:" + lease.getId(),
                            Map.of(
                                    "leaseId", lease.getId().toString(),
                                    "machineIdentityId", machineOpt.get().getId().toString(),
                                    "riskType", "LEASE_ON_INACTIVE_MACHINE"
                            )
                    ));
                }
            }

            // Check if lease duration exceeds excessive threshold (> 7 days)
            if (lease.getMaxLifetimeSeconds() > 7 * 86400L) {
                drafts.add(new SecurityFindingDraft(
                        workspace.getId(),
                        lease.getProjectId(),
                        lease.getEnvironmentId(),
                        FindingCategory.SECRET_LEASE_RISK,
                        FindingSeverity.MEDIUM,
                        FindingConfidence.HIGH,
                        "Excessively Long Secret Lease Lifetime: [" + lease.getId() + "]",
                        "Secret lease " + lease.getId() + " has a maximum lifetime of " + (lease.getMaxLifetimeSeconds() / 3600) + " hours, exceeding standard short-lived lease policies.",
                        "Configure shorter TTLs (e.g., 1 hour) with periodic renewal.",
                        "lease-long-ttl:" + lease.getId(),
                        Map.of(
                                "leaseId", lease.getId().toString(),
                                "maxLifetimeSeconds", String.valueOf(lease.getMaxLifetimeSeconds()),
                                "riskType", "LONG_LIVED_LEASE"
                        )
                ));
            }
        }

        // 2. Scan stale consumers running on old versions
        List<SecretConsumer> consumers = consumerRepository.findByWorkspaceId(workspace.getId());
        for (SecretConsumer consumer : consumers) {
            if (consumer.getStatus() == ConsumerStatus.STALE) {
                drafts.add(new SecurityFindingDraft(
                        workspace.getId(),
                        consumer.getProjectId(),
                        consumer.getEnvironmentId(),
                        FindingCategory.SECRET_LEASE_RISK,
                        FindingSeverity.HIGH,
                        FindingConfidence.HIGH,
                        "Stale Consumer Operating on Deprecated Version: [" + consumer.getName() + "]",
                        "Consumer '" + consumer.getName() + "' has not refreshed its secret state or acknowledged the latest version.",
                        "Verify consumer health and refresh the application configuration.",
                        "consumer-stale:" + consumer.getId(),
                        Map.of(
                                "consumerId", consumer.getId().toString(),
                                "consumerName", consumer.getName(),
                                "riskType", "STALE_CONSUMER"
                        )
                ));
            }
        }

        return drafts;
    }
}
