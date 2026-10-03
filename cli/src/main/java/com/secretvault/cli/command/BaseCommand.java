package com.secretvault.cli.command;

import com.secretvault.cli.SecretVaultCli;
import com.secretvault.cli.auth.AuthManager;
import com.secretvault.cli.auth.EncryptedFileCredentialStore;
import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.EnvironmentDto;
import com.secretvault.cli.client.dto.ProjectDto;
import com.secretvault.cli.client.dto.WorkspaceDto;
import com.secretvault.cli.config.ConfigManager;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.OutputFormat;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Abstract Base Command providing shared context resolution, client lifecycle, and console printers.
 */
@Command
public abstract class BaseCommand implements Callable<Integer> {

    @Spec
    protected CommandSpec spec;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
    protected boolean helpRequested;

    protected ConfigManager getConfigManager() {
        return new ConfigManager();
    }

    protected ContextManager getContextManager() {
        return new ContextManager(getConfigManager());
    }

    protected AuthManager getAuthManager() {
        ConfigManager cm = getConfigManager();
        return new AuthManager(cm, new EncryptedFileCredentialStore(cm.getConfigDirectory()));
    }

    protected ConsolePrinter getPrinter() {
        ConsolePrinter printer = new ConsolePrinter();
        SecretVaultCli cli = getRootCli();
        if (cli != null) {
            if (cli.isNoColor()) {
                printer.setColorEnabled(false);
            }
            if (cli.isQuiet()) {
                printer.setQuiet(true);
            }
        }
        return printer;
    }

    protected OutputFormat getOutputFormat() {
        SecretVaultCli cli = getRootCli();
        if (cli != null && cli.isJsonOutput()) {
            return OutputFormat.JSON;
        }
        if (cli != null && cli.isQuiet()) {
            return OutputFormat.QUIET;
        }
        ContextManager.ResolvedContext ctx = resolveContext();
        return OutputFormat.fromString(ctx.outputFormat());
    }

    protected SecretVaultCli getRootCli() {
        if (spec != null && spec.root() != null && spec.root().userObject() instanceof SecretVaultCli cli) {
            return cli;
        }
        return null;
    }

    protected ContextManager.ResolvedContext resolveContext() {
        SecretVaultCli cli = getRootCli();
        String effProfile = (cli != null) ? cli.getProfile() : null;
        String effServer = (cli != null) ? cli.getServer() : null;
        String effWorkspace = (cli != null) ? cli.getWorkspace() : null;
        String effProject = (cli != null) ? cli.getProject() : null;
        String effEnv = (cli != null) ? cli.getEnvironment() : null;
        String effOutput = (cli != null && cli.isJsonOutput()) ? "json" : (cli != null && cli.isQuiet() ? "quiet" : null);

        return getContextManager().resolve(effProfile, effServer, effWorkspace, effProject, effEnv, effOutput);
    }

    protected SecretVaultApiClient getAuthenticatedClient() {
        ContextManager.ResolvedContext ctx = resolveContext();
        return getAuthManager().createAuthenticatedClient(ctx.profile(), ctx.server());
    }

    protected UUID resolveWorkspaceId(SecretVaultApiClient client, String workspaceIdentifier) {
        if (workspaceIdentifier == null || workspaceIdentifier.isBlank()) {
            throw new IllegalArgumentException("Workspace context is missing. Specify --workspace <name|id> or set default context.");
        }
        try {
            return UUID.fromString(workspaceIdentifier);
        } catch (IllegalArgumentException notUuid) {
            List<WorkspaceDto> list = client.listWorkspaces();
            return list.stream()
                    .filter(w -> workspaceIdentifier.equalsIgnoreCase(w.slug()) || workspaceIdentifier.equalsIgnoreCase(w.name()))
                    .map(WorkspaceDto::id)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Workspace not found matching: '" + workspaceIdentifier + "'"));
        }
    }

    protected UUID resolveProjectId(SecretVaultApiClient client, UUID workspaceId, String projectIdentifier) {
        if (projectIdentifier == null || projectIdentifier.isBlank()) {
            throw new IllegalArgumentException("Project context is missing. Specify --project <name|id> or set default context.");
        }
        try {
            return UUID.fromString(projectIdentifier);
        } catch (IllegalArgumentException notUuid) {
            List<ProjectDto> list = client.listProjects(workspaceId);
            return list.stream()
                    .filter(p -> projectIdentifier.equalsIgnoreCase(p.slug()) || projectIdentifier.equalsIgnoreCase(p.name()))
                    .map(ProjectDto::id)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Project not found matching: '" + projectIdentifier + "'"));
        }
    }

    protected UUID resolveEnvironmentId(SecretVaultApiClient client, UUID workspaceId, UUID projectId, String envIdentifier) {
        if (envIdentifier == null || envIdentifier.isBlank()) {
            throw new IllegalArgumentException("Environment context is missing. Specify --environment <slug|id> (e.g. development).");
        }
        try {
            return UUID.fromString(envIdentifier);
        } catch (IllegalArgumentException notUuid) {
            List<EnvironmentDto> list = client.listEnvironments(workspaceId, projectId);
            return list.stream()
                    .filter(e -> envIdentifier.equalsIgnoreCase(e.slug()) || envIdentifier.equalsIgnoreCase(e.name()))
                    .map(EnvironmentDto::id)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Environment not found matching: '" + envIdentifier + "'"));
        }
    }
}
