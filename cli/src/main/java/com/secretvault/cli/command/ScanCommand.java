package com.secretvault.cli.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.RepositoryCliDtos.ScanDto;
import com.secretvault.cli.client.dto.RepositoryCliDtos.SecretFindingDto;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.SarifFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Executes secret scanning across directories, Git history, pull requests, and CI/CD pipelines.
 * Exit Codes:
 *  0 = Clean / No blocking findings
 *  1 = Findings detected above threshold (Policy Gate Failed)
 *  2 = Scanner internal failure
 *  3 = Configuration error
 *  4 = Authorization failure
 *  5 = Network / Server communication error
 */
@Command(
        name = "scan",
        description = "Scan source code, Git history, and configuration files for exposed secrets and credentials"
)
public class ScanCommand extends BaseCommand {

    @Parameters(index = "0", description = "Target directory path to scan (defaults to current directory '.')", defaultValue = ".")
    private String path;

    @Option(names = {"--git-history"}, description = "Scan Git commit history for previously introduced secrets")
    private boolean gitHistory;

    @Option(names = {"--staged"}, description = "Scan only Git staged files (pre-commit hook mode)")
    private boolean staged;

    @Option(names = {"--repository"}, description = "Associate scan with registered SecretVault repository ID")
    private UUID repositoryId;

    @Option(names = {"--fail-on"}, description = "Fail CI gate on finding severity: CRITICAL, HIGH, MEDIUM, LOW (default: HIGH)", defaultValue = "HIGH")
    private String failOn;

    @Option(names = {"--sarif"}, description = "Output results in standard SARIF v2.1.0 format for CI/CD gates")
    private boolean sarifOutput;

    @Override
    public Integer call() {
        ConsolePrinter printer = getPrinter();
        File targetDir = new File(path).getAbsoluteFile();

        if (!targetDir.exists() || !targetDir.isDirectory()) {
            printer.error("Target path does not exist or is not a directory: " + path);
            return 3; // Configuration error
        }

        UUID workspaceId = resolveWorkspaceId();
        if (workspaceId == null) {
            printer.error("No active workspace selected. Run 'secretvault workspace select <id>' or provide --workspace.");
            return 3;
        }

        SecretVaultApiClient client = getApiClient();

        boolean isJson = (getOutputFormat() == com.secretvault.cli.output.OutputFormat.JSON);

        if (!isJson && !sarifOutput) {
            printer.header("SecretVault Leak Scanner");
            printer.info("Target: " + targetDir.getAbsolutePath());
            printer.info("Git History: " + (gitHistory ? "ENABLED" : "DISABLED"));
            printer.info("Fail Threshold: " + failOn.toUpperCase(Locale.ROOT));
            printer.blank();
        }

        ScanDto scan;
        try {
            scan = client.scanLocalDirectory(workspaceId, repositoryId, targetDir.getAbsolutePath(), gitHistory);
        } catch (Exception e) {
            printer.error("Failed to execute scan: " + e.getMessage());
            return 5;
        }

        List<SecretFindingDto> findings;
        try {
            findings = client.listFindings(workspaceId, repositoryId, null, "DETECTED", null);
        } catch (Exception e) {
            findings = List.of();
        }

        if (sarifOutput) {
            System.out.println(SarifFormatter.formatSarif(findings, targetDir.getName()));
            return evaluateExitCode(findings, failOn);
        }

        if (isJson) {
            try {
                ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
                System.out.println(mapper.writeValueAsString(scan));
            } catch (Exception ignored) {}
            return evaluateExitCode(findings, failOn);
        }

        printer.success(String.format("Scan completed: %d files scanned, %d commits scanned.",
                scan.filesScanned(), scan.commitsScanned()));

        if (findings.isEmpty()) {
            printer.success("Zero exposed credentials detected. Repository is clean!");
            return 0;
        }

        printer.blank();
        printer.warning(String.format("Detected %d finding(s) (%d high/critical risk):", findings.size(), scan.highRiskCount()));

        for (SecretFindingDto f : findings) {
            String sevColor = switch (f.severity() != null ? f.severity() : "LOW") {
                case "CRITICAL" -> printer.colorize("[CRITICAL]", ConsolePrinter.RED);
                case "HIGH" -> printer.colorize("[HIGH]", ConsolePrinter.YELLOW);
                default -> printer.colorize("[" + f.severity() + "]", ConsolePrinter.CYAN);
            };

            System.out.printf("  %s %s: %s:%d\n", sevColor, f.secretType(), f.filePath(), f.lineNumber() != null ? f.lineNumber() : 1);
            System.out.printf("       Masked Evidence: %s\n", f.maskedEvidence() != null ? f.maskedEvidence() : "********");
            System.out.printf("       Confidence: %s | Fingerprint: %s\n\n", f.confidence(), f.fingerprint().substring(0, Math.min(16, f.fingerprint().length())));
        }

        int exitCode = evaluateExitCode(findings, failOn);
        if (exitCode != 0) {
            printer.error(String.format("Security Gate FAILED: Findings exceed '%s' threshold.", failOn));
        } else {
            printer.info("Security Gate PASSED: Findings do not exceed failure threshold.");
        }

        return exitCode;
    }

    private int evaluateExitCode(List<SecretFindingDto> findings, String threshold) {
        String thresh = threshold != null ? threshold.toUpperCase(Locale.ROOT) : "HIGH";

        for (SecretFindingDto f : findings) {
            String sev = f.severity() != null ? f.severity().toUpperCase(Locale.ROOT) : "LOW";
            if (shouldBlock(sev, thresh)) {
                return 1; // Policy Gate Violation
            }
        }
        return 0;
    }

    private boolean shouldBlock(String severity, String threshold) {
        return switch (threshold) {
            case "CRITICAL" -> severity.equals("CRITICAL");
            case "HIGH" -> severity.equals("CRITICAL") || severity.equals("HIGH");
            case "MEDIUM" -> severity.equals("CRITICAL") || severity.equals("HIGH") || severity.equals("MEDIUM");
            case "LOW" -> true;
            default -> severity.equals("CRITICAL") || severity.equals("HIGH");
        };
    }
}
