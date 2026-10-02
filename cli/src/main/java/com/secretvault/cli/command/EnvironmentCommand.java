package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.EnvironmentDto;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.OutputFormat;
import com.secretvault.cli.output.TableFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.UUID;

@Command(
        name = "environment",
        aliases = {"env-tier"},
        description = "Discover and inspect deployment environments within a project",
        subcommands = {
                EnvironmentCommand.ListCommand.class,
                EnvironmentCommand.GetCommand.class
        }
)
public class EnvironmentCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault environment list' or 'secretvault environment get <id>' to inspect environments.");
        return 0;
    }

    @Command(name = "list", description = "List all environments in the active project")
    public static class ListCommand extends BaseCommand {

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                UUID projectId = resolveProjectId(client, workspaceId, ctx.project());

                List<EnvironmentDto> envs = client.listEnvironments(workspaceId, projectId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(envs);
                    return 0;
                }

                if (envs.isEmpty()) {
                    printer.info("No environments found in project: " + ctx.project());
                    return 0;
                }

                TableFormatter table = new TableFormatter("ID", "NAME", "SLUG", "TYPE", "PROTECTED", "STATUS");
                for (EnvironmentDto env : envs) {
                    table.addRow(
                            env.id().toString(),
                            env.name(),
                            env.slug(),
                            env.envType() != null ? env.envType() : "DEVELOPMENT",
                            env.isProtected() ? "true (RESTRICTED)" : "false",
                            env.status() != null ? env.status() : "ACTIVE"
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

    @Command(name = "get", description = "Get details of a specific environment")
    public static class GetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Environment ID or slug (e.g. development, staging, production)")
        private String envIdentifier;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());
                UUID projectId = resolveProjectId(client, workspaceId, ctx.project());
                UUID envId = resolveEnvironmentId(client, workspaceId, projectId, envIdentifier);

                EnvironmentDto env = client.getEnvironment(workspaceId, projectId, envId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(env);
                    return 0;
                }

                TableFormatter table = new TableFormatter("FIELD", "VALUE");
                table.addRow("Environment ID", env.id().toString());
                table.addRow("Project ID", env.projectId().toString());
                table.addRow("Name", env.name());
                table.addRow("Slug", env.slug());
                table.addRow("Tier Type", env.envType() != null ? env.envType() : "DEVELOPMENT");
                table.addRow("Protected", String.valueOf(env.isProtected()));
                table.addRow("Status", env.status() != null ? env.status() : "ACTIVE");
                table.addRow("Created At", env.createdAt() != null ? env.createdAt().toString() : "N/A");

                printer.raw(table.render());
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }
}
