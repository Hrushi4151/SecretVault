package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.WorkspaceDto;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.OutputFormat;
import com.secretvault.cli.output.TableFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.UUID;

@Command(
        name = "workspace",
        description = "Discover and inspect authorized tenant workspaces",
        subcommands = {
                WorkspaceCommand.ListCommand.class,
                WorkspaceCommand.GetCommand.class
        }
)
public class WorkspaceCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault workspace list' or 'secretvault workspace get <id>' to inspect workspaces.");
        return 0;
    }

    @Command(name = "list", description = "List all workspaces accessible to the authenticated user")
    public static class ListCommand extends BaseCommand {

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                List<WorkspaceDto> workspaces = client.listWorkspaces();

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(workspaces);
                    return 0;
                }

                if (workspaces.isEmpty()) {
                    printer.info("No workspaces found for current user.");
                    return 0;
                }

                TableFormatter table = new TableFormatter("ID", "NAME", "SLUG", "ROLE", "DEFAULT");
                for (WorkspaceDto w : workspaces) {
                    table.addRow(
                            w.id().toString(),
                            w.name(),
                            w.slug(),
                            w.role() != null ? w.role() : "VIEWER",
                            w.isDefault() ? "true" : "false"
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

    @Command(name = "get", description = "Get details of a specific workspace")
    public static class GetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Workspace ID or slug")
        private String workspaceIdentifier;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                UUID workspaceId = resolveWorkspaceId(client, workspaceIdentifier);
                WorkspaceDto workspace = client.getWorkspace(workspaceId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(workspace);
                    return 0;
                }

                TableFormatter table = new TableFormatter("FIELD", "VALUE");
                table.addRow("Workspace ID", workspace.id().toString());
                table.addRow("Name", workspace.name());
                table.addRow("Slug", workspace.slug());
                table.addRow("Role", workspace.role() != null ? workspace.role() : "VIEWER");
                table.addRow("Default", String.valueOf(workspace.isDefault()));
                table.addRow("Created At", workspace.createdAt() != null ? workspace.createdAt().toString() : "N/A");

                printer.raw(table.render());
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }
}
