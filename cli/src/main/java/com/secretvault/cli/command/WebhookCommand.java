package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.Phase13CliDtos.*;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.OutputFormat;
import com.secretvault.cli.output.TableFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * CLI command group for Outbound Webhook Governance, SSRF Protection & Deliveries.
 */
@Command(
        name = "webhook",
        aliases = {"webhooks"},
        description = "Manage outbound webhook subscriptions, endpoints, and delivery retries",
        mixinStandardHelpOptions = true,
        subcommands = {
                WebhookCommand.ListCommand.class,
                WebhookCommand.GetCommand.class,
                WebhookCommand.CreateCommand.class,
                WebhookCommand.DeleteCommand.class,
                WebhookCommand.DeliveriesCommand.class,
                WebhookCommand.ReplayDeliveryCommand.class
        }
)
public class WebhookCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault webhook --help' to view available webhook management subcommands.");
        return 0;
    }

    @Command(name = "list", description = "List configured webhook endpoints")
    public static class ListCommand extends BaseCommand {

        @Option(names = {"--limit"}, description = "Maximum endpoints to display", defaultValue = "50")
        private int limit;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                List<WebhookEndpointDto> webhooks = client.listWebhooks(workspaceId, limit);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(webhooks);
                    return 0;
                }

                if (webhooks.isEmpty()) {
                    printer.info("No webhook endpoints registered in workspace: " + ctx.workspace());
                    return 0;
                }

                TableFormatter table = new TableFormatter("WEBHOOK ID", "NAME", "STATUS", "URL", "SECRET PREFIX", "LAST SUCCESS", "LAST FAILURE");
                for (WebhookEndpointDto w : webhooks) {
                    table.addRow(
                            w.id().toString(),
                            w.name(),
                            w.enabled() ? "ACTIVE" : "DISABLED",
                            w.destinationUrl(),
                            w.secretPrefix() != null ? w.secretPrefix() + "..." : "—",
                            w.lastSuccessAt() != null ? w.lastSuccessAt().toString() : "Never",
                            w.lastFailureAt() != null ? w.lastFailureAt().toString() : "Never"
                    );
                }
                printer.raw(table.render());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to list webhooks: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Inspect a webhook endpoint details")
    public static class GetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Webhook UUID")
        private UUID webhookId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                WebhookEndpointDto w = client.getWebhook(workspaceId, webhookId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(w);
                    return 0;
                }

                printer.bold("Webhook Endpoint: " + w.name() + " (" + w.id() + ")");
                printer.item("Destination URL", w.destinationUrl());
                printer.item("Status", w.enabled() ? "ACTIVE" : "DISABLED");
                printer.item("Signing Key Prefix", w.secretPrefix() != null ? w.secretPrefix() + "..." : "—");
                printer.item("Subscribed Events", w.subscribedEvents() != null ? String.join(", ", w.subscribedEvents()) : "*");
                printer.item("Last Success", w.lastSuccessAt() != null ? w.lastSuccessAt().toString() : "Never");
                printer.item("Last Failure", w.lastFailureAt() != null ? w.lastFailureAt().toString() : "Never");
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to get webhook: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "create", description = "Register a new webhook endpoint with SSRF protection")
    public static class CreateCommand extends BaseCommand {

        @Option(names = {"--name"}, required = true, description = "Webhook name")
        private String name;

        @Option(names = {"--url"}, required = true, description = "Destination URL (must be public HTTPS/FQDN)")
        private String url;

        @Option(names = {"--events"}, description = "Comma-separated list of event types (default: all)", defaultValue = "*")
        private String events;

        @Option(names = {"--disabled"}, description = "Create in disabled state")
        private boolean disabled;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                List<String> eventList = Arrays.stream(events.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();

                CreateWebhookRequest req = new CreateWebhookRequest(
                        name,
                        url,
                        eventList,
                        !disabled
                );

                WebhookEndpointDto created = client.createWebhook(workspaceId, req);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(created);
                    return 0;
                }

                printer.success("Webhook endpoint registered successfully.");
                printer.item("Webhook ID", created.id().toString());
                printer.item("Name", created.name());
                printer.item("URL", created.destinationUrl());
                printer.item("Secret Prefix", created.secretPrefix() != null ? created.secretPrefix() + "..." : "—");
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to create webhook: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "delete", description = "Delete a webhook endpoint")
    public static class DeleteCommand extends BaseCommand {

        @Parameters(index = "0", description = "Webhook UUID")
        private UUID webhookId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                client.deleteWebhook(workspaceId, webhookId);
                printer.success("Webhook endpoint " + webhookId + " deleted successfully.");
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to delete webhook: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "deliveries", description = "List webhook delivery attempts and statuses")
    public static class DeliveriesCommand extends BaseCommand {

        @Option(names = {"--webhook-id"}, description = "Filter by Webhook UUID")
        private UUID webhookId;

        @Option(names = {"--limit"}, description = "Maximum deliveries to display", defaultValue = "50")
        private int limit;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                List<WebhookDeliveryDto> deliveries = client.listWebhookDeliveries(workspaceId, webhookId, limit);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(deliveries);
                    return 0;
                }

                if (deliveries.isEmpty()) {
                    printer.info("No webhook deliveries found.");
                    return 0;
                }

                TableFormatter table = new TableFormatter("DELIVERY ID", "STATUS", "HTTP STATUS", "ATTEMPT", "DURATION (MS)", "DELIVERED AT");
                for (WebhookDeliveryDto d : deliveries) {
                    table.addRow(
                            d.id().toString(),
                            d.status(),
                            d.httpStatus() != null ? String.valueOf(d.httpStatus()) : "—",
                            String.valueOf(d.attempt()),
                            d.durationMs() != null ? String.valueOf(d.durationMs()) : "—",
                            d.deliveredAt() != null ? d.deliveredAt().toString() : "Pending"
                    );
                }
                printer.raw(table.render());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to list deliveries: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "replay", description = "Replay a failed webhook delivery")
    public static class ReplayDeliveryCommand extends BaseCommand {

        @Parameters(index = "0", description = "Delivery UUID")
        private UUID deliveryId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                WebhookDeliveryDto delivery = client.replayWebhookDelivery(workspaceId, deliveryId);
                printer.success("Webhook delivery " + deliveryId + " replay triggered. Status: " + delivery.status());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to replay delivery: " + ex.getMessage());
                return 1;
            }
        }
    }
}
