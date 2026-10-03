package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.env.DotEnvPuller;
import com.secretvault.cli.env.DotEnvPusher;
import com.secretvault.cli.output.ConsolePrinter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Command(
        name = "env",
        description = "Pull and push .env key-value pairs safely with diff previews and gitignore protection",
        mixinStandardHelpOptions = true,
        subcommands = {
                EnvCommand.PullCommand.class,
                EnvCommand.PushCommand.class
        }
)
public class EnvCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault env --help' to view available pull and push subcommands.");
        return 0;
    }

    @Command(name = "pull", description = "Pull environment secrets into stdout or safely to a local .env file", mixinStandardHelpOptions = true)
    public static class PullCommand extends BaseCommand {

        @Option(names = {"--output", "-o"}, description = "Target file path to write (e.g. .env, .env.local)")
        private String outputFilePath;

        @Option(names = {"--format", "-f"}, defaultValue = "env", description = "Output format: env (KEY=val), json, shell (export KEY='val')")
        private String format;

        @Option(names = {"--stdout"}, description = "Force printing secrets to stdout")
        private boolean forceStdout;

        @Option(names = {"-y", "--yes"}, description = "Skip file write warning prompt")
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

                DotEnvPuller puller = new DotEnvPuller(client, printer);
                Map<String, String> secrets = puller.fetchEnvironmentSecrets(workspaceId, projectId, envId);

                if (outputFilePath != null && !forceStdout) {
                    Path targetPath = Paths.get(outputFilePath);
                    puller.exportToFile(secrets, targetPath, autoConfirm);
                } else {
                    puller.exportToStdout(secrets, format);
                }
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "push", description = "Push local .env key-value pairs to SecretVault environment", mixinStandardHelpOptions = true)
    public static class PushCommand extends BaseCommand {

        @Option(names = {"--file", "-f"}, defaultValue = ".env", description = "Path to local .env file (default: .env)")
        private String envFilePath;

        @Option(names = {"--dry-run"}, description = "Preview planned changes without mutating remote secrets")
        private boolean dryRun;

        @Option(names = {"-y", "--yes"}, description = "Skip confirmation prompt")
        private boolean autoConfirm;

        @Option(names = {"--overwrite"}, defaultValue = "true", description = "Allow updating values for existing secret keys (default: true)")
        private boolean overwrite;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
                UUID envId = resolveEnvironmentId(client, workspaceId, projectId, ctx.environment());

                DotEnvPusher pusher = new DotEnvPusher(client, printer);
                Path path = Paths.get(envFilePath);
                DotEnvPusher.PushDiff diff = pusher.calculateDiff(path, workspaceId, projectId, envId);

                if (diff.parsedEntries().isEmpty()) {
                    printer.info("No valid secret key-value pairs found in " + envFilePath);
                    return 0;
                }

                pusher.previewDiff(diff);

                if (dryRun) {
                    printer.info("\nDry run completed. No remote changes were applied.");
                    return 0;
                }

                if (!autoConfirm) {
                    if (System.console() != null) {
                        String input = System.console().readLine("\nProceed with push to '" + ctx.environment() + "'? [y/N]: ");
                        if (!"y".equalsIgnoreCase(input) && !"yes".equalsIgnoreCase(input)) {
                            printer.info("Push cancelled by user.");
                            return 0;
                        }
                    }
                }

                pusher.executePush(diff, workspaceId, projectId, envId, overwrite);
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }
}
