package com.secretvault.cli.command;

import com.secretvault.cli.config.CliConfig;
import com.secretvault.cli.config.ConfigManager;
import com.secretvault.cli.config.ProfileConfig;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.OutputFormat;
import com.secretvault.cli.output.TableFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.Map;

@Command(
        name = "config",
        description = "Manage SecretVault CLI client configuration settings (NEVER contains credentials)",
        subcommands = {
                ConfigCommand.GetCommand.class,
                ConfigCommand.SetCommand.class,
                ConfigCommand.ListCommand.class,
                ConfigCommand.PathCommand.class,
                ConfigCommand.ResetCommand.class
        }
)
public class ConfigCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault config --help' to view available configuration subcommands.");
        return 0;
    }

    @Command(name = "get", description = "Get configuration value for a key in active profile")
    public static class GetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Configuration key (server, workspace, project, environment, output)")
        private String key;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ConfigManager cm = getConfigManager();
            CliConfig config = cm.loadConfig();
            String activeProf = (getRootCli() != null && getRootCli().getProfile() != null) ? getRootCli().getProfile() : config.getDefaultProfile();
            ProfileConfig prof = config.getProfile(activeProf);

            String value = switch (key.toLowerCase()) {
                case "server" -> prof.getServer();
                case "workspace", "workspace-slug" -> prof.getWorkspaceSlug();
                case "workspace-id" -> prof.getWorkspaceId();
                case "project", "project-slug" -> prof.getProjectSlug();
                case "project-id" -> prof.getProjectId();
                case "environment", "environment-slug", "env" -> prof.getEnvironmentSlug();
                case "environment-id" -> prof.getEnvironmentId();
                case "output", "output-format" -> prof.getOutputFormat();
                case "default-profile" -> config.getDefaultProfile();
                case "timeout" -> String.valueOf(config.getTimeoutSeconds());
                default -> null;
            };

            if (value == null) {
                printer.warn("Key '" + key + "' is not set or unknown.");
                return 1;
            }

            printer.raw(value);
            return 0;
        }
    }

    @Command(name = "set", description = "Set configuration value for a key in active profile")
    public static class SetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Configuration key (server, workspace, project, environment, output)")
        private String key;

        @Parameters(index = "1", description = "Configuration value")
        private String value;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ConfigManager cm = getConfigManager();
            CliConfig config = cm.loadConfig();
            String activeProf = (getRootCli() != null && getRootCli().getProfile() != null) ? getRootCli().getProfile() : config.getDefaultProfile();
            ProfileConfig prof = config.getProfile(activeProf);

            switch (key.toLowerCase()) {
                case "server" -> prof.setServer(value);
                case "workspace", "workspace-slug" -> prof.setWorkspaceSlug(value);
                case "workspace-id" -> prof.setWorkspaceId(value);
                case "project", "project-slug" -> prof.setProjectSlug(value);
                case "project-id" -> prof.setProjectId(value);
                case "environment", "environment-slug", "env" -> prof.setEnvironmentSlug(value);
                case "environment-id" -> prof.setEnvironmentId(value);
                case "output", "output-format" -> prof.setOutputFormat(value);
                case "default-profile" -> config.setDefaultProfile(value);
                case "timeout" -> {
                    try {
                        config.setTimeoutSeconds(Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        printer.error("Invalid timeout integer value: " + value);
                        return 1;
                    }
                }
                default -> {
                    printer.error("Unknown configuration key: '" + key + "'. Available: server, workspace, project, environment, output, default-profile, timeout");
                    return 1;
                }
            }

            cm.saveConfig(config);
            printer.success("Updated configuration key '" + key + "' = '" + value + "'");
            return 0;
        }
    }

    @Command(name = "list", description = "List all configuration settings for active profile")
    public static class ListCommand extends BaseCommand {

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ConfigManager cm = getConfigManager();
            CliConfig config = cm.loadConfig();
            String activeProf = (getRootCli() != null && getRootCli().getProfile() != null) ? getRootCli().getProfile() : config.getDefaultProfile();
            ProfileConfig p = config.getProfile(activeProf);

            if (getOutputFormat() == OutputFormat.JSON) {
                printer.printJson(Map.of(
                        "defaultProfile", config.getDefaultProfile(),
                        "activeProfile", activeProf,
                        "server", p.getServer(),
                        "workspaceSlug", p.getWorkspaceSlug() != null ? p.getWorkspaceSlug() : "",
                        "projectSlug", p.getProjectSlug() != null ? p.getProjectSlug() : "",
                        "environmentSlug", p.getEnvironmentSlug() != null ? p.getEnvironmentSlug() : "",
                        "outputFormat", p.getOutputFormat(),
                        "timeoutSeconds", config.getTimeoutSeconds()
                ));
                return 0;
            }

            TableFormatter table = new TableFormatter("SETTING", "VALUE");
            table.addRow("Default Profile", config.getDefaultProfile());
            table.addRow("Active Profile", activeProf);
            table.addRow("Server URL", p.getServer());
            table.addRow("Workspace", p.getWorkspaceSlug() != null ? p.getWorkspaceSlug() : (p.getWorkspaceId() != null ? p.getWorkspaceId() : "-"));
            table.addRow("Project", p.getProjectSlug() != null ? p.getProjectSlug() : (p.getProjectId() != null ? p.getProjectId() : "-"));
            table.addRow("Environment", p.getEnvironmentSlug() != null ? p.getEnvironmentSlug() : (p.getEnvironmentId() != null ? p.getEnvironmentId() : "-"));
            table.addRow("Output Format", p.getOutputFormat());
            table.addRow("Timeout", config.getTimeoutSeconds() + "s");

            printer.raw(table.render());
            return 0;
        }
    }

    @Command(name = "path", description = "Print the local filesystem paths for config and credential storage")
    public static class PathCommand extends BaseCommand {

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ConfigManager cm = getConfigManager();

            if (getOutputFormat() == OutputFormat.JSON) {
                printer.printJson(Map.of(
                        "configDirectory", cm.getConfigDirectory().toAbsolutePath().toString(),
                        "configFile", cm.getConfigFile().toAbsolutePath().toString()
                ));
                return 0;
            }

            printer.info("Config Directory: " + cm.getConfigDirectory().toAbsolutePath());
            printer.info("Config File:      " + cm.getConfigFile().toAbsolutePath());
            return 0;
        }
    }

    @Command(name = "reset", description = "Reset configuration file to factory defaults")
    public static class ResetCommand extends BaseCommand {

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            getConfigManager().resetConfig();
            printer.success("SecretVault configuration reset to defaults.");
            return 0;
        }
    }
}
