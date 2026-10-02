package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.ProjectDto;
import com.secretvault.cli.client.dto.WorkspaceDto;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.config.ProjectLocalConfig;
import com.secretvault.cli.output.ConsolePrinter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.UUID;

@Command(
        name = "dev",
        description = "Local development convenience workflows and project bootstrapping",
        subcommands = {
                DevCommand.InitCommand.class
        }
)
public class DevCommand extends RunCommand {

    @Command(name = "init", description = "Bootstrap .secretvault/project.json in current directory to link project context")
    public static class InitCommand extends BaseCommand {

        @Option(names = {"--init-workspace"}, description = "Workspace slug or ID")
        private String initWorkspace;

        @Option(names = {"--init-project"}, description = "Project slug or ID")
        private String initProject;

        @Option(names = {"--init-env"}, defaultValue = "development", description = "Target environment slug (default: development)")
        private String initEnv;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager cm = getContextManager();
                ContextManager.ResolvedContext ctx = resolveContext();

                String selectedWorkspace = (initWorkspace != null) ? initWorkspace : ctx.workspace();
                if (selectedWorkspace == null || selectedWorkspace.isBlank()) {
                    List<WorkspaceDto> workspaces = client.listWorkspaces();
                    if (workspaces.isEmpty()) {
                        printer.error("No workspaces available to link.");
                        return 1;
                    }
                    selectedWorkspace = workspaces.get(0).slug();
                }
                UUID workspaceId = resolveWorkspaceId(client, selectedWorkspace);

                String selectedProject = (initProject != null) ? initProject : ctx.project();
                if (selectedProject == null || selectedProject.isBlank()) {
                    List<ProjectDto> projects = client.listProjects(workspaceId);
                    if (projects.isEmpty()) {
                        printer.error("No projects found in workspace " + selectedWorkspace);
                        return 1;
                    }
                    selectedProject = projects.get(0).slug();
                }
                UUID projectId = resolveProjectId(client, workspaceId, selectedProject);

                ProjectLocalConfig local = new ProjectLocalConfig();
                local.setWorkspaceId(workspaceId.toString());
                local.setWorkspaceSlug(selectedWorkspace);
                local.setProjectId(projectId.toString());
                local.setProjectSlug(selectedProject);
                local.setEnvironmentSlug(initEnv != null ? initEnv : "development");
                local.setServer(ctx.server());

                cm.saveLocalProjectConfig(local);
                printer.success("Initialized SecretVault project in .secretvault/project.json:");
                printer.info("  Workspace:   " + selectedWorkspace);
                printer.info("  Project:     " + selectedProject);
                printer.info("  Environment: " + local.getEnvironmentSlug());
                printer.info("\nYou can now run 'secretvault run -- <command>' seamlessly in this directory!");

                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }
}
