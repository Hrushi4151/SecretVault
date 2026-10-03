package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.RotationCliDtos.*;
import com.secretvault.cli.client.dto.SecretDtos;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.OutputFormat;
import com.secretvault.cli.output.TableFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * CLI command group for Secret Rotation, Lifecycle & Policy Management.
 */
@Command(
        name = "rotation",
        aliases = {"rot"},
        description = "Manage secret rotation jobs, policies, staged rollouts, and impact analysis",
        subcommands = {
                RotationCommand.ListCommand.class,
                RotationCommand.GetCommand.class,
                RotationCommand.StartCommand.class,
                RotationCommand.CancelCommand.class,
                RotationCommand.RetryCommand.class,
                RotationCommand.RollbackCommand.class,
                RotationCommand.EmergencyCommand.class,
                RotationCommand.ImpactCommand.class,
                RotationCommand.PolicyCommand.class
        }
)
public class RotationCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault rotation --help' to view available rotation management subcommands.");
        return 0;
    }

    protected static UUID resolveSecretId(SecretVaultApiClient client, UUID workspaceId, UUID projectId, UUID environmentId, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Secret name or ID is required");
        }
        try {
            return UUID.fromString(identifier);
        } catch (IllegalArgumentException notUuid) {
            List<SecretDtos.SecretMetadataDto> secrets = client.listSecrets(workspaceId, projectId, environmentId, identifier, null);
            return secrets.stream()
                    .filter(s -> identifier.equalsIgnoreCase(s.name()))
                    .map(SecretDtos.SecretMetadataDto::id)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Secret not found matching: '" + identifier + "'"));
        }
    }

    @Command(name = "list", description = "List rotation jobs in current workspace/environment")
    public static class ListCommand extends BaseCommand {

        @Option(names = {"--secret", "--secret-id"}, description = "Filter rotation jobs by secret ID or name")
        private String secretIdentifier;

        @Option(names = {"--limit"}, description = "Maximum number of jobs to display", defaultValue = "50")
        private int limit;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                UUID secretId = null;
                if (secretIdentifier != null && !secretIdentifier.isBlank()) {
                    UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
                    UUID envId = resolveEnvironmentId(client, workspaceId, projectId, ctx.environment());
                    secretId = resolveSecretId(client, workspaceId, projectId, envId, secretIdentifier);
                }

                List<RotationJobDto> jobs = client.listRotationJobs(workspaceId, secretId, limit);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(jobs);
                    return 0;
                }

                if (jobs.isEmpty()) {
                    printer.info("No rotation jobs found.");
                    return 0;
                }

                TableFormatter table = new TableFormatter("JOB ID", "SECRET ID", "STRATEGY", "STATUS", "NEW VER", "ATTEMPTS", "CREATED AT");
                for (RotationJobDto j : jobs) {
                    table.addRow(
                            j.id().toString(),
                            j.secretId().toString().substring(0, 8) + "...",
                            j.strategy(),
                            j.status(),
                            j.targetVersionNumber() != null ? "v" + j.targetVersionNumber() : "—",
                            String.valueOf(j.retryCount()),
                            j.createdAt() != null ? j.createdAt().toString() : "—"
                    );
                }
                printer.raw(table.render());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to list rotation jobs: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Get detailed status and execution attempts of a rotation job")
    public static class GetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Rotation Job ID")
        private UUID jobId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                RotationJobDto job = client.getRotationJob(workspaceId, jobId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(job);
                    return 0;
                }

                printer.highlight("=== Rotation Job Details ===");
                printer.info("Job ID:         " + job.id());
                printer.info("Secret ID:      " + job.secretId());
                printer.info("Workspace ID:   " + job.workspaceId());
                printer.info("Strategy:       " + job.strategy());
                printer.info("Rollout:        " + job.rolloutStrategy());
                printer.info("Status:         " + job.status());
                printer.info("Old Version:    " + (job.oldVersionNumber() != null ? "v" + job.oldVersionNumber() : "—"));
                printer.info("Target Version: " + (job.targetVersionNumber() != null ? "v" + job.targetVersionNumber() : "—"));
                printer.info("Retry Count:    " + job.retryCount() + " / " + job.maxRetries());
                printer.info("Created At:     " + job.createdAt());
                printer.info("Updated At:     " + job.updatedAt());
                if (job.errorMessage() != null) {
                    printer.error("Error:          " + job.errorMessage());
                }

                if (job.attempts() != null && !job.attempts().isEmpty()) {
                    printer.info("\n--- Execution Attempts ---");
                    TableFormatter table = new TableFormatter("ATTEMPT", "STATUS", "STARTED AT", "COMPLETED AT", "ERROR");
                    for (RotationAttemptDto att : job.attempts()) {
                        table.addRow(
                                "#" + att.attemptNumber(),
                                att.status(),
                                att.startedAt() != null ? att.startedAt().toString() : "—",
                                att.completedAt() != null ? att.completedAt().toString() : "—",
                                att.errorMessage() != null ? att.errorMessage() : "None"
                        );
                    }
                    printer.raw(table.render());
                }

                return 0;
            } catch (Exception ex) {
                printer.error("Failed to fetch rotation job: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "start", description = "Trigger a rotation job for a secret")
    public static class StartCommand extends BaseCommand {

        @Parameters(index = "0", description = "Secret name or ID")
        private String secretIdentifier;

        @Option(names = {"--strategy"}, description = "Rotation strategy (MANUAL, ON_DEMAND, SCHEDULED, EXPIRY_BASED, EVENT_DRIVEN, EMERGENCY)", defaultValue = "MANUAL")
        private String strategy;

        @Option(names = {"--rollout"}, description = "Rollout strategy (IMMEDIATE, STAGED, DUAL_CREDENTIAL_GRACE_PERIOD, CANARY)", defaultValue = "DUAL_CREDENTIAL_GRACE_PERIOD")
        private String rollout;

        @Option(names = {"--reason"}, description = "Reason for triggering rotation")
        private String reason;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
                UUID envId = resolveEnvironmentId(client, workspaceId, projectId, ctx.environment());
                UUID secretId = resolveSecretId(client, workspaceId, projectId, envId, secretIdentifier);

                TriggerRotationRequest req = new TriggerRotationRequest(strategy, rollout, reason, null);
                RotationJobDto job = client.triggerRotation(workspaceId, secretId, req);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(job);
                    return 0;
                }

                printer.success("✓ Rotation job started successfully!");
                printer.info("Job ID:         " + job.id());
                printer.info("Status:         " + job.status());
                printer.info("Target Version: " + (job.targetVersionNumber() != null ? "v" + job.targetVersionNumber() : "In generation"));
                printer.info("Track progress with: secretvault rotation get " + job.id());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to start rotation: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "cancel", description = "Cancel an active or queued rotation job")
    public static class CancelCommand extends BaseCommand {

        @Parameters(index = "0", description = "Rotation Job ID")
        private UUID jobId;

        @Option(names = {"--reason"}, description = "Reason for cancellation")
        private String reason;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                CancelRotationRequest req = new CancelRotationRequest(reason != null ? reason : "Cancelled via CLI");
                RotationJobDto job = client.cancelRotation(workspaceId, jobId, req);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(job);
                    return 0;
                }

                printer.success("✓ Rotation job " + jobId + " cancelled. Status: " + job.status());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to cancel rotation: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "retry", description = "Retry a failed rotation job")
    public static class RetryCommand extends BaseCommand {

        @Parameters(index = "0", description = "Rotation Job ID")
        private UUID jobId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                RotationJobDto job = client.retryRotation(workspaceId, jobId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(job);
                    return 0;
                }

                printer.success("✓ Rotation job " + jobId + " queued for retry. Status: " + job.status());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to retry rotation: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "rollback", description = "Rollback a secret rotation to a previous version")
    public static class RollbackCommand extends BaseCommand {

        @Parameters(index = "0", description = "Rotation Job ID")
        private UUID jobId;

        @Option(names = {"--target-version"}, description = "Specific previous version number to rollback to (defaults to old version)")
        private Integer targetVersion;

        @Option(names = {"--reason"}, description = "Reason for rollback")
        private String reason;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                // Fetch job to determine secretId
                RotationJobDto currentJob = client.getRotationJob(workspaceId, jobId);
                RollbackRotationRequest req = new RollbackRotationRequest(targetVersion, reason != null ? reason : "Rollback triggered via CLI");
                RotationJobDto job = client.rollbackRotation(workspaceId, currentJob.secretId(), req);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(job);
                    return 0;
                }

                printer.success("✓ Rotation job " + jobId + " rolled back successfully.");
                printer.info("New Active Version: v" + job.targetVersionNumber());
                printer.info("Status:             " + job.status());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to rollback rotation: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "emergency", description = "Perform emergency rotation and immediate invalidation for a compromised secret")
    public static class EmergencyCommand extends BaseCommand {

        @Parameters(index = "0", description = "Secret name or ID")
        private String secretIdentifier;

        @Option(names = {"--reason"}, description = "Incident / compromise reason", defaultValue = "Emergency rotation triggered via CLI")
        private String reason;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
                UUID envId = resolveEnvironmentId(client, workspaceId, projectId, ctx.environment());
                UUID secretId = resolveSecretId(client, workspaceId, projectId, envId, secretIdentifier);

                TriggerRotationRequest req = new TriggerRotationRequest("EMERGENCY", "IMMEDIATE", reason, null);
                RotationJobDto job = client.emergencyRotate(workspaceId, secretId, req);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(job);
                    return 0;
                }

                printer.highlight("🚨 EMERGENCY ROTATION EXECUTED 🚨");
                printer.success("✓ New secret version created and activated immediately.");
                printer.info("Job ID:         " + job.id());
                printer.info("New Version:    v" + job.targetVersionNumber());
                printer.info("Status:         " + job.status());
                printer.info("All active leases for previous versions have been revoked.");
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to execute emergency rotation: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "impact", description = "Analyze runtime dependencies, affected consumers, and machines prior to rotation")
    public static class ImpactCommand extends BaseCommand {

        @Parameters(index = "0", description = "Secret name or ID")
        private String secretIdentifier;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
                UUID envId = resolveEnvironmentId(client, workspaceId, projectId, ctx.environment());
                UUID secretId = resolveSecretId(client, workspaceId, projectId, envId, secretIdentifier);

                RotationImpactDto impact = client.getRotationImpact(workspaceId, secretId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(impact);
                    return 0;
                }

                printer.highlight("=== Rotation Impact Analysis ===");
                printer.info("Secret:                  " + impact.secretName() + " (" + impact.secretId() + ")");
                printer.info("Environment:             " + impact.environmentName());
                printer.info("Current Version:         v" + impact.currentVersionNumber());
                printer.info("Total Consumers:         " + impact.totalConsumers());
                printer.info("Auto-Refresh Capable:    " + impact.consumersSupportingAutoRefresh());
                printer.info("Restart Required:        " + impact.consumersRequiringRestart());
                printer.info("Stale / Outdated:        " + impact.staleConsumers());
                printer.info("Active Leases:           " + impact.activeLeaseCount());
                printer.info("Affected Machines:       " + impact.affectedMachineIdentities().size());

                if (impact.consumers() != null && !impact.consumers().isEmpty()) {
                    printer.info("\n--- Registered Consumers ---");
                    TableFormatter table = new TableFormatter("CONSUMER", "TYPE", "CURRENT VER", "AUTO-REFRESH", "LAST SEEN", "STATUS");
                    for (ConsumerImpactSummary c : impact.consumers()) {
                        table.addRow(
                                c.consumerName(),
                                c.consumerType(),
                                c.currentVersion() != null ? "v" + c.currentVersion() : "—",
                                c.supportsDynamicRefresh() ? "YES" : "NO (Restart)",
                                c.lastSeenAt() != null ? c.lastSeenAt().toString() : "Never",
                                c.status()
                        );
                    }
                    printer.raw(table.render());
                }

                return 0;
            } catch (Exception ex) {
                printer.error("Failed to analyze rotation impact: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(
            name = "policy",
            description = "Manage automatic rotation policies for secrets",
            subcommands = {
                    PolicyCommand.GetPolicyCommand.class,
                    PolicyCommand.SetPolicyCommand.class,
                    PolicyCommand.DisablePolicyCommand.class
            }
    )
    public static class PolicyCommand extends BaseCommand {

        @Override
        public Integer call() {
            getPrinter().info("Run 'secretvault rotation policy --help' to view available policy subcommands.");
            return 0;
        }

        @Command(name = "get", description = "Get rotation policy configuration for a secret")
        public static class GetPolicyCommand extends BaseCommand {

            @Parameters(index = "0", description = "Secret name or ID")
            private String secretIdentifier;

            @Override
            public Integer call() {
                ConsolePrinter printer = getPrinter();
                try {
                    SecretVaultApiClient client = getAuthenticatedClient();
                    ContextManager.ResolvedContext ctx = resolveContext();
                    UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                    UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
                    UUID envId = resolveEnvironmentId(client, workspaceId, projectId, ctx.environment());
                    UUID secretId = resolveSecretId(client, workspaceId, projectId, envId, secretIdentifier);

                    RotationPolicyDto policy = client.getRotationPolicy(workspaceId, secretId);

                    if (getOutputFormat() == OutputFormat.JSON) {
                        printer.printJson(policy);
                        return 0;
                    }

                    printer.highlight("=== Rotation Policy Configuration ===");
                    printer.info("Policy ID:         " + policy.id());
                    printer.info("Secret ID:         " + policy.secretId());
                    printer.info("Enabled:           " + (policy.enabled() ? "YES" : "NO"));
                    printer.info("Rotation Interval: " + policy.rotationIntervalDays() + " days");
                    printer.info("Rotation Window:   " + policy.rotationWindowHours() + " hours");
                    printer.info("Grace Period:      " + policy.gracePeriodMinutes() + " minutes");
                    printer.info("Strategy:          " + policy.strategy());
                    printer.info("Rollout Strategy:  " + policy.rolloutStrategy());
                    printer.info("Validation Type:   " + policy.validationType());
                    printer.info("Max Retries:       " + policy.maxRetries());
                    printer.info("Auto-Revoke Old:   " + (policy.autoRevokeOldVersion() ? "YES" : "NO"));
                    printer.info("Auto-Rollback:     " + (policy.autoRollbackOnFailure() ? "YES" : "NO"));
                    printer.info("Next Scheduled:    " + (policy.nextScheduledRotation() != null ? policy.nextScheduledRotation().toString() : "Not scheduled"));
                    printer.info("Last Rotated At:   " + (policy.lastRotatedAt() != null ? policy.lastRotatedAt().toString() : "Never"));

                    return 0;
                } catch (Exception ex) {
                    printer.error("Failed to fetch rotation policy: " + ex.getMessage());
                    return 1;
                }
            }
        }

        @Command(name = "set", description = "Create or update rotation policy for a secret")
        public static class SetPolicyCommand extends BaseCommand {

            @Parameters(index = "0", description = "Secret name or ID")
            private String secretIdentifier;

            @Option(names = {"--enabled"}, description = "Enable or disable automatic rotation", defaultValue = "true")
            private boolean enabled;

            @Option(names = {"--interval", "--interval-days"}, description = "Rotation interval in days", defaultValue = "30")
            private int intervalDays;

            @Option(names = {"--window", "--window-hours"}, description = "Rotation execution window in hours", defaultValue = "24")
            private int windowHours;

            @Option(names = {"--grace-period", "--grace-minutes"}, description = "Dual-credential grace period in minutes", defaultValue = "60")
            private int graceMinutes;

            @Option(names = {"--strategy"}, description = "Default rotation strategy (SCHEDULED, ON_DEMAND, EXPIRY_BASED, EVENT_DRIVEN)", defaultValue = "SCHEDULED")
            private String strategy;

            @Option(names = {"--rollout"}, description = "Rollout strategy (IMMEDIATE, STAGED, DUAL_CREDENTIAL_GRACE_PERIOD)", defaultValue = "DUAL_CREDENTIAL_GRACE_PERIOD")
            private String rollout;

            @Option(names = {"--validation"}, description = "Validation strategy (NONE, CONNECTIVITY, AUTHENTICATION, PROVIDER_API, DATABASE_CONNECTION, APPLICATION_HEALTH)", defaultValue = "CONNECTIVITY")
            private String validation;

            @Option(names = {"--max-retries"}, description = "Maximum retry attempts upon failure", defaultValue = "3")
            private int maxRetries;

            @Option(names = {"--auto-revoke"}, description = "Automatically revoke previous secret version after grace period", defaultValue = "true")
            private boolean autoRevoke;

            @Option(names = {"--auto-rollback"}, description = "Automatically rollback if validation or health checks fail", defaultValue = "true")
            private boolean autoRollback;

            @Override
            public Integer call() {
                ConsolePrinter printer = getPrinter();
                try {
                    SecretVaultApiClient client = getAuthenticatedClient();
                    ContextManager.ResolvedContext ctx = resolveContext();
                    UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                    UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
                    UUID envId = resolveEnvironmentId(client, workspaceId, projectId, ctx.environment());
                    UUID secretId = resolveSecretId(client, workspaceId, projectId, envId, secretIdentifier);

                    SaveRotationPolicyRequest req = new SaveRotationPolicyRequest(
                            enabled,
                            intervalDays,
                            1,
                            90,
                            windowHours,
                            "02:00",
                            "UTC",
                            strategy,
                            rollout,
                            validation,
                            null,
                            null,
                            graceMinutes,
                            maxRetries,
                            autoRevoke,
                            autoRollback,
                            true
                    );

                    RotationPolicyDto policy = client.saveRotationPolicy(workspaceId, projectId, envId, secretId, req);

                    if (getOutputFormat() == OutputFormat.JSON) {
                        printer.printJson(policy);
                        return 0;
                    }

                    printer.success("✓ Rotation policy configured successfully for secret: " + secretIdentifier);
                    printer.info("Enabled:           " + policy.enabled());
                    printer.info("Interval:          " + policy.rotationIntervalDays() + " days");
                    printer.info("Grace Period:      " + policy.gracePeriodMinutes() + " mins");
                    printer.info("Next Scheduled:    " + policy.nextScheduledRotation());
                    return 0;
                } catch (Exception ex) {
                    printer.error("Failed to save rotation policy: " + ex.getMessage());
                    return 1;
                }
            }
        }

        @Command(name = "disable", description = "Disable automatic rotation policy for a secret")
        public static class DisablePolicyCommand extends BaseCommand {

            @Parameters(index = "0", description = "Secret name or ID")
            private String secretIdentifier;

            @Override
            public Integer call() {
                ConsolePrinter printer = getPrinter();
                try {
                    SecretVaultApiClient client = getAuthenticatedClient();
                    ContextManager.ResolvedContext ctx = resolveContext();
                    UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                    UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
                    UUID envId = resolveEnvironmentId(client, workspaceId, projectId, ctx.environment());
                    UUID secretId = resolveSecretId(client, workspaceId, projectId, envId, secretIdentifier);

                    client.disableRotationPolicy(workspaceId, secretId);

                    if (getOutputFormat() == OutputFormat.JSON) {
                        printer.printJson(java.util.Map.of("status", "DISABLED", "secretId", secretId));
                        return 0;
                    }

                    printer.success("✓ Rotation policy disabled for secret: " + secretIdentifier);
                    return 0;
                } catch (Exception ex) {
                    printer.error("Failed to disable rotation policy: " + ex.getMessage());
                    return 1;
                }
            }
        }
    }
}
