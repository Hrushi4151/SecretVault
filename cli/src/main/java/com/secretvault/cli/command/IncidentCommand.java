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
 * CLI command group for Security Incident Triage, Compromise Containment & Ops.
 */
@Command(
        name = "incident",
        aliases = {"incidents"},
        description = "Manage security incidents, automated compromise remediation, and triage lifecycles",
        mixinStandardHelpOptions = true,
        subcommands = {
                IncidentCommand.ListCommand.class,
                IncidentCommand.GetCommand.class,
                IncidentCommand.CreateCommand.class,
                IncidentCommand.UpdateStatusCommand.class
        }
)
public class IncidentCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault incident --help' to view available incident triage subcommands.");
        return 0;
    }

    @Command(name = "list", description = "List security incidents in workspace")
    public static class ListCommand extends BaseCommand {

        @Option(names = {"--status"}, description = "Filter by status: OPEN, INVESTIGATING, CONTAINED, REMEDIATION, RESOLVED, CLOSED")
        private String status;

        @Option(names = {"--severity"}, description = "Filter by severity: LOW, MEDIUM, HIGH, CRITICAL")
        private String severity;

        @Option(names = {"--limit"}, description = "Maximum incidents to display", defaultValue = "50")
        private int limit;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                List<SecurityIncidentDto> incidents = client.listIncidents(workspaceId, status, severity, limit);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(incidents);
                    return 0;
                }

                if (incidents.isEmpty()) {
                    printer.info("No security incidents matching filter.");
                    return 0;
                }

                TableFormatter table = new TableFormatter("INCIDENT #", "SEVERITY", "STATUS", "TITLE", "CATEGORY", "CREATED AT");
                for (SecurityIncidentDto inc : incidents) {
                    table.addRow(
                            inc.incidentNumber() != null ? inc.incidentNumber() : inc.id().toString().substring(0, 8),
                            inc.severity(),
                            inc.status(),
                            inc.title(),
                            inc.category() != null ? inc.category() : "GENERAL",
                            inc.createdAt() != null ? inc.createdAt().toString() : "—"
                    );
                }
                printer.raw(table.render());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to list incidents: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Inspect security incident details and resolution summary")
    public static class GetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Incident UUID")
        private UUID incidentId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                SecurityIncidentDto inc = client.getIncident(workspaceId, incidentId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(inc);
                    return 0;
                }

                printer.bold("Security Incident: " + inc.incidentNumber() + " - " + inc.title());
                printer.item("Incident ID", inc.id().toString());
                printer.item("Severity", inc.severity());
                printer.item("Status", inc.status());
                printer.item("Category", inc.category() != null ? inc.category() : "—");
                printer.item("Source Event", inc.sourceEventId() != null ? inc.sourceEventId().toString() : "Direct Report");
                printer.item("Created At", inc.createdAt() != null ? inc.createdAt().toString() : "—");
                if (inc.resolvedAt() != null) {
                    printer.item("Resolved At", inc.resolvedAt().toString());
                }
                printer.bold("\nDescription:");
                printer.raw(inc.description() != null ? inc.description() + "\n" : "No description provided.\n");
                if (inc.resolutionSummary() != null && !inc.resolutionSummary().isBlank()) {
                    printer.bold("\nResolution Summary:");
                    printer.raw(inc.resolutionSummary() + "\n");
                }
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to get incident: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "create", description = "Report and declare a new security incident")
    public static class CreateCommand extends BaseCommand {

        @Option(names = {"--title"}, required = true, description = "Incident title")
        private String title;

        @Option(names = {"--description"}, description = "Detailed description of security finding or anomaly")
        private String description;

        @Option(names = {"--severity"}, description = "Severity: LOW, MEDIUM, HIGH, CRITICAL", defaultValue = "HIGH")
        private String severity;

        @Option(names = {"--category"}, description = "Category (e.g. COMPROMISE, LEAK, ANOMALY, TAMPERING)", defaultValue = "COMPROMISE")
        private String category;

        @Option(names = {"--source-event-id"}, description = "Triggering Outbox Domain Event ID")
        private UUID sourceEventId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                CreateIncidentRequest req = new CreateIncidentRequest(
                        title,
                        description,
                        severity,
                        category,
                        sourceEventId
                );

                SecurityIncidentDto created = client.createIncident(workspaceId, req);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(created);
                    return 0;
                }

                printer.success("Security Incident declared successfully: " + created.incidentNumber());
                printer.item("Incident ID", created.id().toString());
                printer.item("Severity", created.severity());
                printer.item("Status", created.status());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to declare security incident: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "update-status", description = "Transition incident status through triage lifecycle")
    public static class UpdateStatusCommand extends BaseCommand {

        @Parameters(index = "0", description = "Incident UUID")
        private UUID incidentId;

        @Option(names = {"--status"}, required = true, description = "New Status: INVESTIGATING, CONTAINED, REMEDIATION, RESOLVED, CLOSED")
        private String status;

        @Option(names = {"--summary"}, description = "Resolution or containment notes")
        private String summary;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                SecurityIncidentDto updated = client.updateIncidentStatus(workspaceId, incidentId, status, summary);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(updated);
                    return 0;
                }

                printer.success("Incident " + updated.incidentNumber() + " transitioned to: " + updated.status());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to update incident status: " + ex.getMessage());
                return 1;
            }
        }
    }
}
