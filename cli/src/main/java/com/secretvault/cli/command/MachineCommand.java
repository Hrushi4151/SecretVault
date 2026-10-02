package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.AuthDtos;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.TableFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Command(
        name = "machine",
        description = "Manage workspace Machine Identities and CI/CD workload actors",
        subcommands = {
                MachineCommand.ListCommand.class,
                MachineCommand.GetCommand.class,
                MachineCommand.StatusCommand.class
        }
)
public class MachineCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault machine --help' to view available subcommands.");
        return 0;
    }

    @Command(name = "list", description = "List all machine identities in the active workspace")
    public static class ListCommand extends BaseCommand {

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ContextManager.ResolvedContext ctx = resolveContext();

            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                UUID targetWs = resolveWorkspaceId(client, ctx.workspace());

                List<AuthDtos.MachineIdentityDto> list = client.listMachineIdentities(targetWs);

                if (list.isEmpty()) {
                    printer.info("No machine identities found in workspace: " + targetWs);
                    return 0;
                }

                TableFormatter table = new TableFormatter("ID", "NAME", "TYPE", "STATUS", "LAST AUTHENTICATED", "EXPIRES AT");
                for (AuthDtos.MachineIdentityDto m : list) {
                    table.addRow(
                            m.id() != null ? m.id().toString() : "-",
                            m.name() != null ? m.name() : "-",
                            m.type() != null ? m.type() : "-",
                            m.status() != null ? m.status() : "-",
                            m.lastAuthenticatedAt() != null ? m.lastAuthenticatedAt().toString() : "Never",
                            m.expiresAt() != null ? m.expiresAt().toString() : "No Expiry"
                    );
                }
                printer.raw(table.render());
                return 0;
            } catch (Exception e) {
                printer.error("Failed to list machine identities: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Get detailed information about a machine identity")
    public static class GetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Machine identity UUID")
        private UUID machineId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ContextManager.ResolvedContext ctx = resolveContext();

            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                UUID targetWs = resolveWorkspaceId(client, ctx.workspace());

                AuthDtos.MachineIdentityDto m = client.getMachineIdentity(targetWs, machineId);

                TableFormatter table = new TableFormatter("FIELD", "VALUE");
                table.addRow("ID", m.id().toString());
                table.addRow("Name", m.name());
                table.addRow("Type", m.type());
                table.addRow("Status", m.status());
                table.addRow("Description", m.description() != null ? m.description() : "None");
                table.addRow("Last Authenticated", m.lastAuthenticatedAt() != null ? m.lastAuthenticatedAt().toString() : "Never");
                table.addRow("Last Used", m.lastUsedAt() != null ? m.lastUsedAt().toString() : "Never");
                table.addRow("Expires At", m.expiresAt() != null ? m.expiresAt().toString() : "Never (Non-expiring)");

                printer.raw(table.render());
                return 0;
            } catch (Exception e) {
                printer.error("Failed to get machine identity: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "status", description = "Display current status and health of a machine identity")
    public static class StatusCommand extends BaseCommand {

        @Parameters(index = "0", description = "Machine identity UUID")
        private UUID machineId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ContextManager.ResolvedContext ctx = resolveContext();

            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                UUID targetWs = resolveWorkspaceId(client, ctx.workspace());

                AuthDtos.MachineIdentityDto m = client.getMachineIdentity(targetWs, machineId);

                String status = m.status();
                boolean isExpired = m.expiresAt() != null && Instant.now().isAfter(m.expiresAt());

                if ("ACTIVE".equalsIgnoreCase(status) && !isExpired) {
                    printer.success("Machine Identity [" + m.name() + "] is ACTIVE and operational.");
                } else if (isExpired) {
                    printer.warn("Machine Identity [" + m.name() + "] is EXPIRED (expired at " + m.expiresAt() + ").");
                } else {
                    printer.error("Machine Identity [" + m.name() + "] is " + status + ".");
                }
                return 0;
            } catch (Exception e) {
                printer.error("Failed to check machine status: " + e.getMessage());
                return 1;
            }
        }
    }
}
