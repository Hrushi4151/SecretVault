package com.secretvault.cli;

import com.secretvault.cli.command.AuthCommand;
import com.secretvault.cli.command.AutomationCommand;
import com.secretvault.cli.command.CompletionCommand;
import com.secretvault.cli.command.ConfigCommand;
import com.secretvault.cli.command.ConsumerCommand;
import com.secretvault.cli.command.ContextCommand;
import com.secretvault.cli.command.DevCommand;
import com.secretvault.cli.command.DoctorCommand;
import com.secretvault.cli.command.EnvCommand;
import com.secretvault.cli.command.EnvironmentCommand;
import com.secretvault.cli.command.EventsCommand;
import com.secretvault.cli.command.IncidentCommand;
import com.secretvault.cli.command.LeaseCommand;
import com.secretvault.cli.command.MachineCommand;
import com.secretvault.cli.command.NotificationCommand;
import com.secretvault.cli.command.ProjectCommand;
import com.secretvault.cli.command.RotationCommand;
import com.secretvault.cli.command.RunCommand;
import com.secretvault.cli.command.SecretCommand;
import com.secretvault.cli.command.ScanCommand;
import com.secretvault.cli.command.RepositoryCliCommand;
import com.secretvault.cli.command.FindingCliCommand;
import com.secretvault.cli.command.VersionCommand;
import com.secretvault.cli.command.WebhookCommand;
import com.secretvault.cli.command.WorkspaceCommand;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.security.RedactionHelper;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Main Entrypoint and Root Command for SecretVault Developer CLI.
 */
@Command(
        name = "secretvault",
        aliases = {"sv"},
        description = "SecretVault — Enterprise Secret Management & In-Memory Runtime Injection CLI",
        mixinStandardHelpOptions = true,
        versionProvider = SecretVaultCli.VersionProvider.class,
        subcommands = {
                AuthCommand.class,
                MachineCommand.class,
                WorkspaceCommand.class,
                ProjectCommand.class,
                EnvironmentCommand.class,
                ContextCommand.class,
                SecretCommand.class,
                RotationCommand.class,
                LeaseCommand.class,
                ConsumerCommand.class,
                EventsCommand.class,
                AutomationCommand.class,
                WebhookCommand.class,
                IncidentCommand.class,
                NotificationCommand.class,
                EnvCommand.class,
                RunCommand.class,
                DevCommand.class,
                ConfigCommand.class,
                DoctorCommand.class,
                VersionCommand.class,
                CompletionCommand.class,
                ScanCommand.class,
                RepositoryCliCommand.class,
                FindingCliCommand.class,
                CommandLine.HelpCommand.class
        }
)
public class SecretVaultCli implements Callable<Integer> {

    @Option(names = {"-p", "--profile"}, description = "Authentication and configuration profile", scope = CommandLine.ScopeType.INHERIT)
    private String profile;

    @Option(names = {"-s", "--server"}, description = "SecretVault server URL", scope = CommandLine.ScopeType.INHERIT)
    private String server;

    @Option(names = {"-w", "--workspace"}, description = "Active Workspace ID or slug", scope = CommandLine.ScopeType.INHERIT)
    private String workspace;

    @Option(names = {"--project"}, description = "Active Project ID or slug", scope = CommandLine.ScopeType.INHERIT)
    private String project;

    @Option(names = {"-e", "--environment", "--env"}, description = "Active Environment ID or slug (e.g. development, staging, production)", scope = CommandLine.ScopeType.INHERIT)
    private String environment;

    @Option(names = {"--json"}, description = "Format output as JSON", scope = CommandLine.ScopeType.INHERIT)
    private boolean jsonOutput;

    @Option(names = {"-q", "--quiet"}, description = "Suppress informational messages", scope = CommandLine.ScopeType.INHERIT)
    private boolean quiet;

    @Option(names = {"--no-color"}, description = "Disable ANSI color formatting", scope = CommandLine.ScopeType.INHERIT)
    private boolean noColor;

    public String getProfile() {
        return profile;
    }

    public String getServer() {
        return server;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getProject() {
        return project;
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isJsonOutput() {
        return jsonOutput;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public boolean isNoColor() {
        return noColor;
    }

    @Override
    public Integer call() {
        ConsolePrinter printer = new ConsolePrinter();
        if (noColor) printer.setColorEnabled(false);
        if (quiet) printer.setQuiet(true);

        printer.info("SecretVault CLI — Secure Secret Management & Runtime Injection");
        printer.info("Run 'secretvault --help' to view available commands.\n");
        return 0;
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new SecretVaultCli())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
                    ConsolePrinter errPrinter = new ConsolePrinter();
                    errPrinter.error(RedactionHelper.redact(ex.getMessage()));
                    return 1;
                });

        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    public static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[]{
                    "SecretVault CLI v" + VersionCommand.CLI_VERSION + " (" + VersionCommand.BUILD_DATE + ")",
                    "JVM: " + System.getProperty("java.version", "21")
            };
        }
    }
}
