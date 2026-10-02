package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.runtime.ProcessRunner;
import com.secretvault.cli.runtime.SecretInjector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Command(
        name = "run",
        description = "Execute child commands with SecretVault secrets injected directly into process memory"
)
public class RunCommand extends BaseCommand {

    @Option(names = {"--secret", "-S"}, description = "Explicit secret keys to inject (can be specified multiple times)")
    private List<String> explicitSecrets;

    @Option(names = {"--override"}, defaultValue = "true", description = "Override host environment variables with SecretVault secrets (default: true)")
    private boolean overrideExistingEnv;

    @Parameters(description = "Command and arguments to execute (use '--' before command, e.g., 'secretvault run -- npm start')")
    private List<String> commandToRun;

    @Override
    public Integer call() {
        ConsolePrinter printer = getPrinter();
        if (commandToRun == null || commandToRun.isEmpty()) {
            printer.error("No command specified to execute. Usage: secretvault run -- <command> [args...]");
            return 1;
        }

        try {
            SecretVaultApiClient client = getAuthenticatedClient();
            ContextManager.ResolvedContext ctx = resolveContext();
            UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
            UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
            UUID envId = resolveEnvironmentId(client, workspaceId, projectId, ctx.environment());

            printer.info("Injecting secrets from " + ctx.project() + " / " + ctx.environment() + " into child process...");

            SecretInjector injector = new SecretInjector(client, printer);
            Map<String, String> injectedEnv = injector.fetchAndBuildEnvironment(
                    workspaceId, projectId, envId, explicitSecrets, overrideExistingEnv
            );

            ProcessRunner runner = new ProcessRunner(printer);
            File workingDir = new File(System.getProperty("user.dir", "."));

            int exitCode = runner.runProcess(commandToRun, injectedEnv, workingDir);
            return exitCode;
        } catch (Exception e) {
            printer.error("Run command execution failed: " + e.getMessage());
            return 1;
        }
    }
}
