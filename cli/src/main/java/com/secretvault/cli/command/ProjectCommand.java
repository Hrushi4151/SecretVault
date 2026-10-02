package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.ProjectDto;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.OutputFormat;
import com.secretvault.cli.output.TableFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.UUID;

@Command(
        name = "project",
        description = "Discover and inspect projects within a workspace",
        subcommands = {
                ProjectCommand.ListCommand.class,
                ProjectCommand.GetCommand.class
        }
)
public class ProjectCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault project list' or 'secretvault project get <id>' to inspect projects.");
        return 0;
    }

    @Command(name = "list", description = "List all projects in the active or specified workspace")
    public static class ListCommand extends BaseCommand {

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                List<ProjectDto> projects = client.listProjects(workspaceId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(projects);
                    return 0;
                }

                if (projects.isEmpty()) {
                    printer.info("No projects found in workspace: " + ctx.workspace());
                    return 0;
                }

                TableFormatter table = new TableFormatter("ID", "NAME", "SLUG", "STATUS", "ENVIRONMENTS");
                for (ProjectDto p : projects) {
                    int envCount = p.environments() != null ? p.environments().size() : 0;
                    table.addRow(
                            p.id().toString(),
                            p.name(),
                            p.slug(),
                            p.status() != null ? p.status() : "ACTIVE",
                            String.valueOf(envCount)
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

    @Command(name = "get", description = "Get details of a specific project")
    public static class GetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Project ID or slug")
        private String projectIdentifier;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                UUID projectId = resolveProjectId(client, workspaceId, projectIdentifier);

                ProjectDto project = client.getProject(workspaceId, projectId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(project);
                    return 0;
                }

                TableFormatter table = new TableFormatter("FIELD", "VALUE");
                table.addRow("Project ID", project.id().toString());
                table.addRow("Workspace ID", project.workspaceId().toString());
                table.addRow("Name", project.name());
                table.addRow("Slug", project.slug());
                table.addRow("Description", project.description() != null ? project.description() : "N/A");
                table.addRow("Status", project.status() != null ? project.status() : "ACTIVE");
                table.addRow("Created At", project.createdAt() != null ? project.createdAt().toString() : "N/A");

                printer.raw(table.render());

                if (project.environments() != null && !project.environments().isEmpty()) {
                    printer.info("\nEnvironments:");
                    TableFormatter envTable = new TableFormatter("ID", "NAME", "SLUG", "TYPE", "PROTECTED");
                    for (ProjectDto.EnvironmentSummaryDto env : project.environments()) {
                        envTable.addRow(
                                env.id().toString(),
                                env.name(),
                                env.slug(),
                                env.envType() != null ? env.envType() : "DEVELOPMENT",
                                String.valueOf(env.isProtected())
                        );
                    }
                    printer.raw(envTable.render());
                }

                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }
}
