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

import java.util.List;
import java.util.UUID;

/**
 * CLI command group for In-App Notifications, Alerts & Preferences.
 */
@Command(
        name = "notification",
        aliases = {"notifications", "notify"},
        description = "View and manage security alerts and in-app notifications",
        mixinStandardHelpOptions = true,
        subcommands = {
                NotificationCommand.ListCommand.class,
                NotificationCommand.ReadCommand.class,
                NotificationCommand.ReadAllCommand.class
        }
)
public class NotificationCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault notification --help' to view available notification commands.");
        return 0;
    }

    @Command(name = "list", description = "List security notifications and alerts")
    public static class ListCommand extends BaseCommand {

        @Option(names = {"--status"}, description = "Filter by status: UNREAD, READ, ACKNOWLEDGED, EXPIRED", defaultValue = "UNREAD")
        private String status;

        @Option(names = {"--limit"}, description = "Maximum notifications to display", defaultValue = "50")
        private int limit;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                List<NotificationDto> notifications = client.listNotifications(workspaceId, status, limit);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(notifications);
                    return 0;
                }

                if (notifications.isEmpty()) {
                    printer.info("No notifications found with status: " + status);
                    return 0;
                }

                TableFormatter table = new TableFormatter("NOTIFICATION ID", "SEVERITY", "STATUS", "TITLE", "CHANNEL", "CREATED AT");
                for (NotificationDto n : notifications) {
                    table.addRow(
                            n.id().toString(),
                            n.severity(),
                            n.status(),
                            n.title(),
                            n.channel() != null ? n.channel() : "IN_APP",
                            n.createdAt() != null ? n.createdAt().toString() : "—"
                    );
                }
                printer.raw(table.render());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to list notifications: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "read", description = "Mark a notification as read")
    public static class ReadCommand extends BaseCommand {

        @Parameters(index = "0", description = "Notification UUID")
        private UUID notificationId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                NotificationDto n = client.markNotificationRead(workspaceId, notificationId);
                printer.success("Notification marked as read: " + n.title());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to mark notification as read: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "read-all", description = "Mark all pending notifications as read")
    public static class ReadAllCommand extends BaseCommand {

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                client.markAllNotificationsRead(workspaceId);
                printer.success("All notifications marked as read.");
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to mark all notifications as read: " + ex.getMessage());
                return 1;
            }
        }
    }
}
