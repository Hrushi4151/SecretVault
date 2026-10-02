package com.secretvault.cli.command;

import com.secretvault.cli.config.CliConfig;
import com.secretvault.cli.config.ConfigManager;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.config.ProfileConfig;
import com.secretvault.cli.config.ProjectLocalConfig;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.OutputFormat;
import com.secretvault.cli.output.TableFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Map;
import java.util.Optional;

@Command(
        name = "context",
        description = "Inspect and manage active workspace, project, and environment contexts",
        subcommands = {
                ContextCommand.GetCommand.class,
                ContextCommand.SetCommand.class,
                ContextCommand.ResetCommand.class
        }
)
public class ContextCommand extends BaseCommand {

    @Override
    public Integer call() {
        return new GetCommand().callWithBase(this);
    }

    @Command(name = "get", description = "Show resolved workspace, project, and environment context")
    public static class GetCommand extends BaseCommand {

        public Integer callWithBase(BaseCommand base) {
            this.spec = base.spec;
            return call();
        }

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ContextManager.ResolvedContext ctx = resolveContext();
            Optional<ProjectLocalConfig> localOpt = getContextManager().findLocalProjectConfig();

            if (getOutputFormat() == OutputFormat.JSON) {
                printer.printJson(Map.of(
                        "profile", ctx.profile(),
                        "server", ctx.server(),
                        "workspace", ctx.workspace() != null ? ctx.workspace() : "",
                        "project", ctx.project() != null ? ctx.project() : "",
                        "environment", ctx.environment() != null ? ctx.environment() : "",
                        "hasLocalProjectFile", localOpt.isPresent()
                ));
                return 0;
            }

            TableFormatter table = new TableFormatter("CONTEXT SCOPE", "RESOLVED VALUE", "SOURCE");
            table.addRow("Profile", ctx.profile(), "User Config");
            table.addRow("Server", ctx.server(), "Active Profile");
            table.addRow("Workspace", ctx.workspace() != null ? ctx.workspace() : "[NOT SET]",
                    ctx.workspace() != null ? (localOpt.isPresent() ? ".secretvault/project.json / Config" : "Config") : "None");
            table.addRow("Project", ctx.project() != null ? ctx.project() : "[NOT SET]",
                    ctx.project() != null ? (localOpt.isPresent() ? ".secretvault/project.json / Config" : "Config") : "None");
            table.addRow("Environment", ctx.environment() != null ? ctx.environment() : "[NOT SET]",
                    ctx.environment() != null ? (localOpt.isPresent() ? ".secretvault/project.json / Config" : "Config") : "None");

            printer.raw(table.render());
            return 0;
        }
    }

    @Command(name = "set", description = "Set default workspace, project, or environment context for current profile")
    public static class SetCommand extends BaseCommand {

        @Option(names = {"--set-workspace"}, description = "Workspace slug or ID to set")
        private String setWorkspace;

        @Option(names = {"--set-project"}, description = "Project slug or ID to set")
        private String setProject;

        @Option(names = {"--set-environment"}, description = "Environment slug or ID to set")
        private String setEnvironment;

        @Option(names = {"--local"}, description = "Save binding to local directory .secretvault/project.json")
        private boolean saveLocal;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ConfigManager cm = getConfigManager();
            CliConfig config = cm.loadConfig();

            ContextManager.ResolvedContext ctx = resolveContext();
            ProfileConfig profileConfig = config.getProfile(ctx.profile());

            String targetWorkspace = (setWorkspace != null) ? setWorkspace : (getRootCli() != null ? getRootCli().getWorkspace() : null);
            String targetProject = (setProject != null) ? setProject : (getRootCli() != null ? getRootCli().getProject() : null);
            String targetEnvironment = (setEnvironment != null) ? setEnvironment : (getRootCli() != null ? getRootCli().getEnvironment() : null);

            if (saveLocal) {
                ProjectLocalConfig local = getContextManager().findLocalProjectConfig().orElse(new ProjectLocalConfig());
                if (targetWorkspace != null) local.setWorkspaceSlug(targetWorkspace);
                if (targetProject != null) local.setProjectSlug(targetProject);
                if (targetEnvironment != null) local.setEnvironmentSlug(targetEnvironment);
                local.setServer(ctx.server());
                getContextManager().saveLocalProjectConfig(local);
                printer.success("Saved local context to .secretvault/project.json");
            } else {
                if (targetWorkspace != null) profileConfig.setWorkspaceSlug(targetWorkspace);
                if (targetProject != null) profileConfig.setProjectSlug(targetProject);
                if (targetEnvironment != null) profileConfig.setEnvironmentSlug(targetEnvironment);
                cm.saveConfig(config);
                printer.success("Updated default context for profile '" + ctx.profile() + "'.");
            }

            return 0;
        }
    }

    @Command(name = "reset", description = "Clear saved context defaults for current profile")
    public static class ResetCommand extends BaseCommand {

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ConfigManager cm = getConfigManager();
            CliConfig config = cm.loadConfig();

            ContextManager.ResolvedContext ctx = resolveContext();
            ProfileConfig profileConfig = config.getProfile(ctx.profile());

            profileConfig.setWorkspaceId(null);
            profileConfig.setWorkspaceSlug(null);
            profileConfig.setProjectId(null);
            profileConfig.setProjectSlug(null);
            profileConfig.setEnvironmentId(null);
            profileConfig.setEnvironmentSlug(null);

            cm.saveConfig(config);
            printer.success("Context defaults reset for profile '" + ctx.profile() + "'.");
            return 0;
        }
    }
}
