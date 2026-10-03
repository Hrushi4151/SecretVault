package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.RotationCliDtos.SecretConsumerDto;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.OutputFormat;
import com.secretvault.cli.output.TableFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * CLI command group for Secret Workload Consumers & Dependency Registry.
 */
@Command(
        name = "consumer",
        aliases = {"consumers"},
        description = "Manage registered application workloads, SDK instances, and heartbeat statuses",
        subcommands = {
                ConsumerCommand.ListCommand.class,
                ConsumerCommand.GetCommand.class,
                ConsumerCommand.DisableCommand.class
        }
)
public class ConsumerCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault consumer --help' to view available consumer management subcommands.");
        return 0;
    }

    @Command(name = "list", description = "List registered secret consumers and workloads")
    public static class ListCommand extends BaseCommand {

        @Option(names = {"--limit"}, description = "Maximum number of consumers to display", defaultValue = "50")
        private int limit;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                List<SecretConsumerDto> consumers = client.listConsumers(workspaceId, limit);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(consumers);
                    return 0;
                }

                if (consumers.isEmpty()) {
                    printer.info("No consumers registered in workspace: " + ctx.workspace());
                    return 0;
                }

                TableFormatter table = new TableFormatter("CONSUMER ID", "NAME", "TYPE", "STATUS", "AUTO-REFRESH", "LAST SEEN", "RUNTIME");
                for (SecretConsumerDto c : consumers) {
                    table.addRow(
                            c.id().toString(),
                            c.name(),
                            c.type(),
                            c.status(),
                            c.supportsDynamicRefresh() ? "YES" : "NO (Restart)",
                            c.lastHeartbeatAt() != null ? c.lastHeartbeatAt().toString() : "Never",
                            c.runtimeEnvironment() != null ? c.runtimeEnvironment() : "—"
                    );
                }
                printer.raw(table.render());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to list consumers: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Get detailed information about a registered consumer")
    public static class GetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Consumer ID")
        private UUID consumerId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                SecretConsumerDto c = client.getConsumer(workspaceId, consumerId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(c);
                    return 0;
                }

                printer.highlight("=== Secret Consumer Details ===");
                printer.info("Consumer ID:         " + c.id());
                printer.info("Name:                " + c.name());
                printer.info("Type:                " + c.type());
                printer.info("Status:              " + c.status());
                printer.info("Workspace ID:        " + c.workspaceId());
                printer.info("Project ID:          " + c.projectId());
                printer.info("Environment ID:      " + c.environmentId());
                printer.info("Machine Identity ID: " + (c.machineIdentityId() != null ? c.machineIdentityId() : "—"));
                printer.info("Auto-Refresh:        " + (c.supportsDynamicRefresh() ? "YES" : "NO"));
                printer.info("SDK Version:         " + (c.sdkVersion() != null ? c.sdkVersion() : "—"));
                printer.info("Runtime:             " + (c.runtimeEnvironment() != null ? c.runtimeEnvironment() : "—"));
                printer.info("Hostname:            " + (c.hostname() != null ? c.hostname() : "—"));
                printer.info("Last Heartbeat:      " + (c.lastHeartbeatAt() != null ? c.lastHeartbeatAt() : "Never"));
                printer.info("Registered At:       " + c.createdAt());

                return 0;
            } catch (Exception ex) {
                printer.error("Failed to fetch consumer: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "disable", description = "Disable a consumer from retrieving secrets or renewing leases")
    public static class DisableCommand extends BaseCommand {

        @Parameters(index = "0", description = "Consumer ID")
        private UUID consumerId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                SecretConsumerDto c = client.disableConsumer(workspaceId, consumerId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(c);
                    return 0;
                }

                printer.success("✓ Consumer disabled successfully: " + consumerId);
                printer.info("Status: " + c.status());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to disable consumer: " + ex.getMessage());
                return 1;
            }
        }
    }
}
