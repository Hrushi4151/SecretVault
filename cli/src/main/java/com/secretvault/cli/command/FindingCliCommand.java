package com.secretvault.cli.command;

import com.secretvault.cli.client.dto.RepositoryCliDtos.RemediationJobDto;
import com.secretvault.cli.client.dto.RepositoryCliDtos.SecretFindingDto;
import com.secretvault.cli.client.dto.RepositoryCliDtos.WhyExposedDto;
import com.secretvault.cli.output.ConsolePrinter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.UUID;

@Command(
        name = "finding",
        description = "Inspect, explain, and remediate exposed credentials detected across repositories",
        subcommands = {
                FindingCliCommand.ListFindingsCommand.class,
                FindingCliCommand.GetFindingCommand.class,
                FindingCliCommand.WhyExposedCommand.class,
                FindingCliCommand.ConfirmFindingCommand.class,
                FindingCliCommand.IgnoreFindingCommand.class,
                FindingCliCommand.RemediateFindingCommand.class
        }
)
public class FindingCliCommand extends BaseCommand {

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }

    @Command(name = "list", description = "List secret findings across repositories in active workspace")
    public static class ListFindingsCommand extends BaseCommand {
        @Option(names = {"--repository"}, description = "Filter by repository UUID")
        private UUID repositoryId;

        @Option(names = {"--severity"}, description = "Filter by severity: CRITICAL, HIGH, MEDIUM, LOW")
        private String severity;

        @Option(names = {"--status"}, description = "Filter by status: DETECTED, CONFIRMED, RESOLVED, FALSE_POSITIVE, IGNORED")
        private String status;

        @Option(names = {"--search"}, description = "Search query for file, detector, or fingerprint")
        private String search;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            UUID workspaceId = resolveWorkspaceId();
            if (workspaceId == null) return 3;

            try {
                List<SecretFindingDto> findings = getApiClient().listFindings(workspaceId, repositoryId, severity, status, search);
                if (findings.isEmpty()) {
                    printer.info("No secret findings match the specified criteria.");
                    return 0;
                }

                printer.header(String.format("%-36s  %-10s  %-20s  %-25s  %-8s",
                        "FINDING ID", "SEVERITY", "SECRET TYPE", "FILE PATH", "STATUS"));
                for (SecretFindingDto f : findings) {
                    printer.row(String.format("%-36s  %-10s  %-20s  %-25s  %-8s",
                            f.id(), f.severity(), f.secretType(),
                            f.filePath() + ":" + (f.lineNumber() != null ? f.lineNumber() : 1), f.status()));
                }
                return 0;
            } catch (Exception e) {
                printer.error("Failed to list findings: " + e.getMessage());
                return 5;
            }
        }
    }

    @Command(name = "get", description = "View full details and masked evidence for a finding")
    public static class GetFindingCommand extends BaseCommand {
        @Parameters(index = "0", description = "Finding UUID")
        private UUID findingId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            UUID workspaceId = resolveWorkspaceId();
            if (workspaceId == null) return 3;

            try {
                SecretFindingDto f = getApiClient().getFinding(workspaceId, findingId);
                printer.header("Secret Finding: " + f.secretType() + " in " + f.filePath());
                printer.keyVal("ID", f.id().toString());
                printer.keyVal("Severity", f.severity());
                printer.keyVal("Confidence", f.confidence());
                printer.keyVal("Status", f.status());
                printer.keyVal("Masked Evidence", f.maskedEvidence() != null ? f.maskedEvidence() : "********");
                printer.keyVal("Location", f.filePath() + ":" + (f.lineNumber() != null ? f.lineNumber() : 1));
                printer.keyVal("Commit SHA", f.commitSha() != null ? f.commitSha() : "N/A");
                printer.keyVal("Branch", f.branch() != null ? f.branch() : "N/A");
                printer.keyVal("Validation", f.validationStatus());
                printer.keyVal("Fingerprint", f.fingerprint());
                printer.keyVal("SecretVault Match", f.secretId() != null ? "MATCHED (Secret ID: " + f.secretId() + ")" : "NO MATCH");
                return 0;
            } catch (Exception e) {
                printer.error("Failed to get finding: " + e.getMessage());
                return 5;
            }
        }
    }

    @Command(name = "why-exposed", description = "Explain why this secret is considered exposed (WhyExposed / Explainability)")
    public static class WhyExposedCommand extends BaseCommand {
        @Parameters(index = "0", description = "Finding UUID")
        private UUID findingId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            UUID workspaceId = resolveWorkspaceId();
            if (workspaceId == null) return 3;

            try {
                WhyExposedDto exp = getApiClient().whyExposed(workspaceId, findingId);
                printer.header("Why is this credential considered exposed?");
                printer.keyVal("Secret Type", exp.secretType());
                printer.keyVal("Severity", exp.severity());
                printer.keyVal("Repository", exp.repositoryName() + " (" + exp.repositoryVisibility() + ")");
                printer.keyVal("Evidence", exp.maskedValue() != null ? exp.maskedValue() : "********");
                printer.keyVal("Location", exp.filePath() + ":" + (exp.lineNumber() != null ? exp.lineNumber() : 1));

                printer.blank();
                printer.info("Key Risk Factors:");
                for (String factor : exp.riskFactors()) {
                    System.out.println("  • " + factor);
                }

                printer.blank();
                printer.info("Recommended Remediation: " + exp.recommendedRemediation());
                return 0;
            } catch (Exception e) {
                printer.error("Failed to explain exposure: " + e.getMessage());
                return 5;
            }
        }
    }

    @Command(name = "confirm", description = "Mark a finding as CONFIRMED real credential")
    public static class ConfirmFindingCommand extends BaseCommand {
        @Parameters(index = "0", description = "Finding UUID")
        private UUID findingId;

        @Option(names = {"--reason"}, description = "Reason for confirmation")
        private String reason;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            UUID workspaceId = resolveWorkspaceId();
            if (workspaceId == null) return 3;

            try {
                getApiClient().updateFindingStatus(workspaceId, findingId, "CONFIRMED", reason != null ? reason : "Confirmed by CLI user");
                printer.success("Finding " + findingId + " marked as CONFIRMED.");
                return 0;
            } catch (Exception e) {
                printer.error("Failed to confirm finding: " + e.getMessage());
                return 5;
            }
        }
    }

    @Command(name = "ignore", description = "Mark a finding as IGNORED or FALSE_POSITIVE")
    public static class IgnoreFindingCommand extends BaseCommand {
        @Parameters(index = "0", description = "Finding UUID")
        private UUID findingId;

        @Option(names = {"--reason"}, description = "Reason for ignoring")
        private String reason;

        @Option(names = {"--false-positive"}, description = "Mark specifically as FALSE_POSITIVE")
        private boolean falsePositive;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            UUID workspaceId = resolveWorkspaceId();
            if (workspaceId == null) return 3;

            try {
                String status = falsePositive ? "FALSE_POSITIVE" : "IGNORED";
                getApiClient().updateFindingStatus(workspaceId, findingId, status, reason != null ? reason : "Ignored by CLI user");
                printer.success("Finding " + findingId + " marked as " + status + ".");
                return 0;
            } catch (Exception e) {
                printer.error("Failed to ignore finding: " + e.getMessage());
                return 5;
            }
        }
    }

    @Command(name = "remediate", description = "Execute automated remediation (ROTATE_SECRET, REVOKE_SECRET, MARK_FALSE_POSITIVE)")
    public static class RemediateFindingCommand extends BaseCommand {
        @Parameters(index = "0", description = "Finding UUID")
        private UUID findingId;

        @Option(names = {"--action"}, required = true, description = "Action: ROTATE_SECRET, REVOKE_SECRET, MARK_FALSE_POSITIVE, IGNORE")
        private String action;

        @Option(names = {"--notes"}, description = "Remediation audit notes")
        private String notes;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            UUID workspaceId = resolveWorkspaceId();
            if (workspaceId == null) return 3;

            try {
                RemediationJobDto job = getApiClient().remediateFinding(workspaceId, findingId, action, notes);
                printer.success("Remediation action " + action + " executed successfully (Job ID: " + job.id() + ")");
                printer.info("Status: " + job.status());
                return 0;
            } catch (Exception e) {
                printer.error("Failed to remediate finding: " + e.getMessage());
                return 5;
            }
        }
    }
}
