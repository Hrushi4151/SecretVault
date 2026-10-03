package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.SecretDtos;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.OutputFormat;
import com.secretvault.cli.output.TableFormatter;
import com.secretvault.cli.security.RedactionHelper;
import com.secretvault.cli.security.SecretRevealHelper;
import com.secretvault.cli.security.StepUpAuthenticator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Command(
        name = "secret",
        aliases = {"secrets"},
        description = "Manage secret lifecycles, explicit reveals, versions, and rollbacks",
        mixinStandardHelpOptions = true,
        subcommands = {
                SecretCommand.ListCommand.class,
                SecretCommand.GetCommand.class,
                SecretCommand.RevealCommand.class,
                SecretCommand.CreateCommand.class,
                SecretCommand.SetCommand.class,
                SecretCommand.UpdateCommand.class,
                SecretCommand.DeleteCommand.class,
                SecretCommand.VersionsCommand.class,
                SecretCommand.RollbackCommand.class,
                SecretCommand.RotateCommand.class,
                SecretCommand.CompromiseCommand.class
        }
)
public class SecretCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault secret --help' to view available secret management subcommands.");
        return 0;
    }

    private static UUID resolveSecretId(SecretVaultApiClient client, UUID workspaceId, UUID projectId, UUID environmentId, String identifier) {
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

    @Command(name = "list", description = "List secret metadata in current environment (never displays plaintext values)")
    public static class ListCommand extends BaseCommand {

        @Option(names = {"--search"}, description = "Filter secrets by name substring")
        private String search;

        @Option(names = {"--status"}, description = "Filter by status (ACTIVE, ARCHIVED, DEPRECATED)")
        private String status;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
                UUID envId = resolveEnvironmentId(client, workspaceId, projectId, ctx.environment());

                List<SecretDtos.SecretMetadataDto> secrets = client.listSecrets(workspaceId, projectId, envId, search, status);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(secrets);
                    return 0;
                }

                if (secrets.isEmpty()) {
                    printer.info("No secrets found in " + ctx.project() + " / " + ctx.environment());
                    return 0;
                }

                TableFormatter table = new TableFormatter("ID", "NAME", "VERSION", "STATUS", "VALUE (MASKED)", "UPDATED AT");
                for (SecretDtos.SecretMetadataDto s : secrets) {
                    table.addRow(
                            s.id().toString(),
                            s.name(),
                            "v" + s.currentVersionNumber(),
                            s.status() != null ? s.status() : "ACTIVE",
                            s.maskedValue() != null ? s.maskedValue() : "••••••••••••••••",
                            s.updatedAt() != null ? s.updatedAt().toString() : "N/A"
                    );
                }

                printer.raw(table.render());
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Get metadata for a specific secret (does NOT display plaintext value)")
    public static class GetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Secret name or UUID")
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

                SecretDtos.SecretMetadataDto secret = client.getSecret(workspaceId, projectId, envId, secretId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(secret);
                    return 0;
                }

                TableFormatter table = new TableFormatter("FIELD", "VALUE");
                table.addRow("Secret ID", secret.id().toString());
                table.addRow("Name", secret.name());
                table.addRow("Description", secret.description() != null ? secret.description() : "N/A");
                table.addRow("Current Version", "v" + secret.currentVersionNumber());
                table.addRow("Status", secret.status() != null ? secret.status() : "ACTIVE");
                table.addRow("Value", "•••••••••••••••• (use 'secretvault secret reveal " + secret.name() + "' to decrypt)");
                table.addRow("Created At", secret.createdAt() != null ? secret.createdAt().toString() : "N/A");
                table.addRow("Updated At", secret.updatedAt() != null ? secret.updatedAt().toString() : "N/A");

                printer.raw(table.render());
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "reveal", description = "Explicitly decrypt and display secret plaintext value to stdout", mixinStandardHelpOptions = true)
    public static class RevealCommand extends BaseCommand {

        @Parameters(index = "0", description = "Secret name or UUID", arity = "0..1")
        private String secretIdentifier;

        @Option(names = {"--version", "-v"}, description = "Historical version number (default: latest active version)")
        private Integer versionNumber;

        @Option(names = {"-y", "--yes"}, description = "Skip confirmation prompt")
        private boolean autoConfirm;

        @Option(names = {"--raw"}, description = "Print only the raw secret value with no trailing metadata or styling")
        private boolean raw;

        @Option(names = {"--reason", "-r"}, description = "Audit reason for revealing this secret")
        private String reason;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                if (secretIdentifier == null || secretIdentifier.isBlank()) {
                    if (System.console() != null) {
                        secretIdentifier = System.console().readLine("Secret name or UUID: ");
                    } else {
                        try {
                            BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                            System.out.print("Secret name or UUID: ");
                            secretIdentifier = br.readLine();
                        } catch (Exception e) {
                            printer.error("Secret identifier is required.");
                            return 1;
                        }
                    }
                }
                if (secretIdentifier == null || secretIdentifier.isBlank()) {
                    printer.error("Secret name or UUID is required.");
                    return 1;
                }

                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
                UUID envId = resolveEnvironmentId(client, workspaceId, projectId, ctx.environment());
                UUID secretId = resolveSecretId(client, workspaceId, projectId, envId, secretIdentifier);

                if (!autoConfirm && !raw && getOutputFormat() != OutputFormat.JSON && getOutputFormat() != OutputFormat.QUIET) {
                    printer.warn("This command decrypts and reveals sensitive plaintext data to the terminal.");
                }

                SecretDtos.SecretRevealDto reveal = SecretRevealHelper.revealProtectedSecret(
                        client, workspaceId, projectId, envId, secretId, versionNumber, reason, printer
                );

                if (raw) {
                    System.out.print(reveal.value());
                    return 0;
                }

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(reveal);
                    return 0;
                }

                if (getOutputFormat() == OutputFormat.QUIET) {
                    printer.raw(reveal.value());
                    return 0;
                }

                TableFormatter table = new TableFormatter("FIELD", "VALUE");
                table.addRow("Secret Name", reveal.name());
                table.addRow("Version", "v" + reveal.versionNumber());
                table.addRow("Decrypted Value", reveal.value());
                table.addRow("Revealed At", reveal.revealedAt() != null ? reveal.revealedAt().toString() : "N/A");

                printer.raw(table.render());
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "create", description = "Create a new secret with version 1")
    public static class CreateCommand extends BaseCommand {

        @Parameters(index = "0", description = "Secret key name (e.g. DB_PASSWORD)")
        private String secretName;

        @Option(names = {"--description", "-d"}, description = "Optional description")
        private String description;

        @Option(names = {"--from-file"}, description = "Read secret value from a local file")
        private String fromFile;

        @Option(names = {"--value-from-env"}, description = "Read secret value from a host environment variable")
        private String valueFromEnv;

        @Option(names = {"--stdin"}, description = "Read secret value from standard input")
        private boolean readStdin;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                String secretValue = resolveSecretValueInput(printer);
                if (secretValue == null || secretValue.isEmpty()) {
                    printer.error("Secret value cannot be empty.");
                    return 1;
                }

                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
                UUID envId = resolveEnvironmentId(client, workspaceId, projectId, ctx.environment());

                SecretDtos.SecretMetadataDto created = client.createSecret(workspaceId, projectId, envId, secretName, secretValue, description);
                printer.success("Created secret '" + created.name() + "' (Version 1) in " + ctx.environment());
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }

        private String resolveSecretValueInput(ConsolePrinter printer) throws Exception {
            if (fromFile != null && !fromFile.isBlank()) {
                Path p = Paths.get(fromFile);
                if (!Files.exists(p) || !Files.isRegularFile(p)) {
                    throw new IllegalArgumentException("File does not exist: " + fromFile);
                }
                return Files.readString(p);
            }
            if (valueFromEnv != null && !valueFromEnv.isBlank()) {
                String val = System.getenv(valueFromEnv);
                if (val == null) {
                    throw new IllegalArgumentException("Environment variable '" + valueFromEnv + "' is not set");
                }
                return val;
            }
            if (readStdin) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                String res = sb.toString();
                return res.endsWith("\n") ? res.substring(0, res.length() - 1) : res;
            }

            // Interactive prompt
            if (System.console() != null) {
                char[] chars = System.console().readPassword("Enter secret value for " + secretName + ": ");
                return chars != null ? new String(chars) : "";
            } else {
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                System.out.print("Enter secret value for " + secretName + ": ");
                return br.readLine();
            }
        }
    }

    @Command(name = "set", description = "Create secret if new, or append a new version if existing")
    public static class SetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Secret key name")
        private String secretName;

        @Option(names = {"--description", "-d"}, description = "Optional description")
        private String description;

        @Option(names = {"--from-file"}, description = "Read secret value from a local file")
        private String fromFile;

        @Option(names = {"--value-from-env"}, description = "Read secret value from a host environment variable")
        private String valueFromEnv;

        @Option(names = {"--stdin"}, description = "Read secret value from standard input")
        private boolean readStdin;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
                UUID envId = resolveEnvironmentId(client, workspaceId, projectId, ctx.environment());

                String secretValue = resolveSecretValueInput();
                if (secretValue == null || secretValue.isEmpty()) {
                    printer.error("Secret value cannot be empty.");
                    return 1;
                }

                // Check if secret already exists
                List<SecretDtos.SecretMetadataDto> existing = client.listSecrets(workspaceId, projectId, envId, secretName, null);
                Optional<SecretDtos.SecretMetadataDto> match = existing.stream()
                        .filter(s -> secretName.equalsIgnoreCase(s.name()))
                        .findFirst();

                if (match.isPresent()) {
                    SecretDtos.SecretMetadataDto updated = client.updateSecret(
                            workspaceId, projectId, envId, match.get().id(),
                            description, "ACTIVE", secretValue, "Updated via SecretVault CLI secret set"
                    );
                    printer.success("Updated secret '" + updated.name() + "' -> Created Version " + updated.currentVersionNumber());
                } else {
                    SecretDtos.SecretMetadataDto created = client.createSecret(
                            workspaceId, projectId, envId, secretName, secretValue, description
                    );
                    printer.success("Created secret '" + created.name() + "' (Version 1)");
                }
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }

        private String resolveSecretValueInput() throws Exception {
            if (fromFile != null && !fromFile.isBlank()) {
                Path p = Paths.get(fromFile);
                return Files.readString(p);
            }
            if (valueFromEnv != null && !valueFromEnv.isBlank()) {
                String val = System.getenv(valueFromEnv);
                if (val == null) throw new IllegalArgumentException("Environment variable '" + valueFromEnv + "' is not set");
                return val;
            }
            if (readStdin) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                String res = sb.toString();
                return res.endsWith("\n") ? res.substring(0, res.length() - 1) : res;
            }

            if (System.console() != null) {
                char[] chars = System.console().readPassword("Enter secret value for " + secretName + ": ");
                return chars != null ? new String(chars) : "";
            } else {
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                System.out.print("Enter secret value for " + secretName + ": ");
                return br.readLine();
            }
        }
    }

    @Command(name = "update", description = "Update secret metadata or append a new version")
    public static class UpdateCommand extends BaseCommand {

        @Parameters(index = "0", description = "Secret name or UUID")
        private String secretIdentifier;

        @Option(names = {"--description", "-d"}, description = "Updated description")
        private String description;

        @Option(names = {"--from-file"}, description = "Read new secret value from a file")
        private String fromFile;

        @Option(names = {"--stdin"}, description = "Read new secret value from stdin")
        private boolean readStdin;

        @Option(names = {"--reason", "-r"}, description = "Audit reason for this update")
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

                String newValue = null;
                if (fromFile != null || readStdin) {
                    if (fromFile != null) {
                        newValue = Files.readString(Paths.get(fromFile));
                    } else {
                        BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        String r = sb.toString();
                        newValue = r.endsWith("\n") ? r.substring(0, r.length() - 1) : r;
                    }
                }

                SecretDtos.SecretMetadataDto updated = client.updateSecret(
                        workspaceId, projectId, envId, secretId, description, "ACTIVE", newValue, reason
                );

                printer.success("Successfully updated '" + updated.name() + "' (Now at Version " + updated.currentVersionNumber() + ")");
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "delete", description = "Soft-delete a secret from an environment")
    public static class DeleteCommand extends BaseCommand {

        @Parameters(index = "0", description = "Secret name or UUID")
        private String secretIdentifier;

        @Option(names = {"-y", "--yes"}, description = "Skip confirmation prompt")
        private boolean autoConfirm;

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

                if (!autoConfirm) {
                    printer.warn("Are you sure you want to delete secret '" + secretIdentifier + "' from " + ctx.environment() + "?");
                    if (System.console() != null) {
                        String input = System.console().readLine("Confirm delete [y/N]: ");
                        if (!"y".equalsIgnoreCase(input) && !"yes".equalsIgnoreCase(input)) {
                            printer.info("Delete cancelled.");
                            return 0;
                        }
                    }
                }

                client.deleteSecret(workspaceId, projectId, envId, secretId);
                printer.success("Secret '" + secretIdentifier + "' deleted successfully.");
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "versions", description = "Display immutable version history for a secret")
    public static class VersionsCommand extends BaseCommand {

        @Parameters(index = "0", description = "Secret name or UUID")
        private String secretIdentifier;

        @Option(names = {"--page"}, defaultValue = "0", description = "Page index (default: 0)")
        private int page;

        @Option(names = {"--size"}, defaultValue = "20", description = "Page size (default: 20)")
        private int size;

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

                SecretDtos.PageResponse<SecretDtos.SecretVersionDto> pageResp = client.listSecretVersions(workspaceId, projectId, envId, secretId, page, size);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(pageResp);
                    return 0;
                }

                if (pageResp == null || pageResp.content() == null || pageResp.content().isEmpty()) {
                    printer.info("No versions recorded for secret: " + secretIdentifier);
                    return 0;
                }

                TableFormatter table = new TableFormatter("VERSION", "CURRENT", "TYPE", "REASON", "CREATED AT");
                for (SecretDtos.SecretVersionDto v : pageResp.content()) {
                    table.addRow(
                            "v" + v.versionNumber(),
                            v.isCurrent() ? "YES" : "no",
                            v.versionType() != null ? v.versionType() : "STANDARD",
                            v.reason() != null ? v.reason() : "",
                            v.createdAt() != null ? v.createdAt().toString() : "N/A"
                    );
                }

                printer.raw(table.render());
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "rollback", description = "Roll back a secret to a historical version as a brand new version (vN+1)")
    public static class RollbackCommand extends BaseCommand {

        @Parameters(index = "0", description = "Secret name or UUID")
        private String secretIdentifier;

        @Option(names = {"--version", "-v"}, required = true, description = "Target historical version number to roll back to")
        private int targetVersion;

        @Option(names = {"--reason", "-r"}, description = "Audit reason for rollback")
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

                String r = reason != null ? reason : "Rolled back to v" + targetVersion + " via CLI";
                SecretDtos.SecretVersionDto rolledBack = client.rollbackSecret(workspaceId, projectId, envId, secretId, targetVersion, null, r);

                printer.success("Rollback successful: Created new Version " + rolledBack.versionNumber() + " from Version " + targetVersion);
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "rotate", description = "Trigger rotation for a secret (supports --emergency flag)")
    public static class RotateCommand extends BaseCommand {

        @Parameters(index = "0", description = "Secret name or UUID")
        private String secretIdentifier;

        @Option(names = {"--emergency"}, description = "Execute emergency immediate rotation with old-version invalidation")
        private boolean emergency;

        @Option(names = {"--strategy"}, description = "Rotation strategy (MANUAL, ON_DEMAND, SCHEDULED, EXPIRY_BASED, EMERGENCY)", defaultValue = "MANUAL")
        private String strategy;

        @Option(names = {"--rollout"}, description = "Rollout strategy (IMMEDIATE, STAGED, DUAL_CREDENTIAL_GRACE_PERIOD)", defaultValue = "DUAL_CREDENTIAL_GRACE_PERIOD")
        private String rollout;

        @Option(names = {"--reason", "-r"}, description = "Audit reason for rotation")
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

                if (emergency) {
                    com.secretvault.cli.client.dto.RotationCliDtos.TriggerRotationRequest req =
                            new com.secretvault.cli.client.dto.RotationCliDtos.TriggerRotationRequest("EMERGENCY", "IMMEDIATE", reason != null ? reason : "Emergency rotation via CLI", null);
                    var job = client.emergencyRotate(workspaceId, secretId, req);
                    printer.highlight("🚨 EMERGENCY ROTATION EXECUTED 🚨");
                    printer.success("✓ Secret rotated and new version activated immediately: v" + job.targetVersionNumber());
                    return 0;
                }

                com.secretvault.cli.client.dto.RotationCliDtos.TriggerRotationRequest req =
                        new com.secretvault.cli.client.dto.RotationCliDtos.TriggerRotationRequest(strategy, rollout, reason != null ? reason : "Rotation triggered via CLI", null);
                var job = client.triggerRotation(workspaceId, secretId, req);
                printer.success("✓ Rotation job started successfully: " + job.id() + " (Target version: " + (job.targetVersionNumber() != null ? "v" + job.targetVersionNumber() : "generating") + ")");
                return 0;
            } catch (Exception e) {
                printer.error("Failed to rotate secret: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "compromise", description = "Mark a secret as compromised and immediately initiate emergency rotation and lease revocation")
    public static class CompromiseCommand extends BaseCommand {

        @Parameters(index = "0", description = "Secret name or UUID")
        private String secretIdentifier;

        @Option(names = {"--reason", "-r"}, description = "Compromise incident reason / CVE identifier", defaultValue = "Secret marked compromised via CLI")
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

                com.secretvault.cli.client.dto.RotationCliDtos.TriggerRotationRequest req =
                        new com.secretvault.cli.client.dto.RotationCliDtos.TriggerRotationRequest("EMERGENCY", "IMMEDIATE", reason, null);
                var job = client.emergencyRotate(workspaceId, secretId, req);

                printer.highlight("🚨 SECRET MARKED COMPROMISED 🚨");
                printer.success("✓ Emergency rotation completed. New Version v" + job.targetVersionNumber() + " is active.");
                printer.info("All prior leases revoked and security findings recorded.");
                return 0;
            } catch (Exception e) {
                printer.error("Failed to execute compromised secret workflow: " + e.getMessage());
                return 1;
            }
        }
    }
}

