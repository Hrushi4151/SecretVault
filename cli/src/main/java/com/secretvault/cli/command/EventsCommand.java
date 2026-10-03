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
 * CLI command group for Outbox Domain Events and Replay Operations.
 */
@Command(
        name = "events",
        aliases = {"event"},
        description = "Query immutable domain event streams and trigger event replays",
        mixinStandardHelpOptions = true,
        subcommands = {
                EventsCommand.ListCommand.class,
                EventsCommand.GetCommand.class,
                EventsCommand.ReplayCommand.class,
                EventsCommand.ListReplaysCommand.class
        }
)
public class EventsCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault events --help' to view available event operations.");
        return 0;
    }

    @Command(name = "list", description = "List domain events in workspace")
    public static class ListCommand extends BaseCommand {

        @Option(names = {"--type"}, description = "Filter by canonical event type (e.g. SECRET_CREATED, ROTATION_FAILED)")
        private String eventType;

        @Option(names = {"--limit"}, description = "Maximum number of events to fetch", defaultValue = "50")
        private int limit;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                List<OutboxEventDto> events = client.listEvents(workspaceId, eventType, limit);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(events);
                    return 0;
                }

                if (events.isEmpty()) {
                    printer.info("No events found in workspace: " + ctx.workspace());
                    return 0;
                }

                TableFormatter table = new TableFormatter("EVENT ID", "TYPE", "AGGREGATE TYPE", "AGGREGATE ID", "STATUS", "ATTEMPTS", "OCCURRED AT");
                for (OutboxEventDto e : events) {
                    table.addRow(
                            e.eventId() != null ? e.eventId().toString() : e.id().toString(),
                            e.eventType(),
                            e.aggregateType() != null ? e.aggregateType() : "—",
                            e.aggregateId() != null ? e.aggregateId() : "—",
                            e.status(),
                            String.valueOf(e.attemptCount()),
                            e.occurredAt() != null ? e.occurredAt().toString() : "—"
                    );
                }
                printer.raw(table.render());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to list events: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Inspect a domain event payload and metadata")
    public static class GetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Event UUID")
        private UUID eventId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                OutboxEventDto event = client.getEvent(workspaceId, eventId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(event);
                    return 0;
                }

                printer.bold("Event Details: " + event.eventId());
                printer.item("Type", event.eventType());
                printer.item("Aggregate", (event.aggregateType() != null ? event.aggregateType() : "—") + " (" + event.aggregateId() + ")");
                printer.item("Status", event.status());
                printer.item("Attempts", String.valueOf(event.attemptCount()));
                printer.item("Occurred At", event.occurredAt() != null ? event.occurredAt().toString() : "—");
                if (event.lastError() != null) {
                    printer.item("Last Error", event.lastError());
                }
                printer.bold("\nPayload (Redacted):");
                printer.raw(event.payload() != null ? event.payload() : "{}\n");
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to fetch event: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "replay", description = "Request replay of domain events")
    public static class ReplayCommand extends BaseCommand {

        @Option(names = {"--event-id"}, description = "Target specific event ID")
        private UUID targetEventId;

        @Option(names = {"--type"}, description = "Filter events by type")
        private String eventType;

        @Option(names = {"--aggregate-type"}, description = "Filter by aggregate type")
        private String aggregateType;

        @Option(names = {"--aggregate-id"}, description = "Filter by aggregate ID")
        private String aggregateId;

        @Option(names = {"--reexecute"}, description = "Re-execute downstream side-effects (automation/webhooks)", defaultValue = "false")
        private boolean reexecute;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                CreateReplayRequest req = new CreateReplayRequest(
                        targetEventId,
                        eventType,
                        aggregateType,
                        aggregateId,
                        null,
                        null,
                        reexecute
                );

                EventReplayDto result = client.requestEventReplay(workspaceId, req);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(result);
                    return 0;
                }

                printer.success("Event replay completed successfully.");
                printer.item("Replay ID", result.id().toString());
                printer.item("Status", result.status());
                printer.item("Total Events", String.valueOf(result.totalEvents()));
                printer.item("Replayed", String.valueOf(result.replayedEvents()));
                printer.item("Failed", String.valueOf(result.failedEvents()));
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to request event replay: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "replays", description = "List past event replay requests")
    public static class ListReplaysCommand extends BaseCommand {

        @Option(names = {"--limit"}, description = "Maximum replays to display", defaultValue = "20")
        private int limit;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                List<EventReplayDto> replays = client.listEventReplays(workspaceId, limit);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(replays);
                    return 0;
                }

                if (replays.isEmpty()) {
                    printer.info("No event replay history found.");
                    return 0;
                }

                TableFormatter table = new TableFormatter("REPLAY ID", "STATUS", "TOTAL", "REPLAYED", "FAILED", "SIDE-EFFECTS", "CREATED AT");
                for (EventReplayDto r : replays) {
                    table.addRow(
                            r.id().toString(),
                            r.status(),
                            String.valueOf(r.totalEvents()),
                            String.valueOf(r.replayedEvents()),
                            String.valueOf(r.failedEvents()),
                            r.reexecuteSideEffects() ? "YES" : "NO",
                            r.createdAt() != null ? r.createdAt().toString() : "—"
                    );
                }
                printer.raw(table.render());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to list replays: " + ex.getMessage());
                return 1;
            }
        }
    }
}
