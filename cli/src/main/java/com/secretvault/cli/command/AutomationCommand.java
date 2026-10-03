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
 * CLI command group for Security Automation Policies, Approvals & Executions.
 */
@Command(
        name = "automation",
        aliases = {"auto"},
        description = "Manage security automation policies, approvals, and execution logs",
        mixinStandardHelpOptions = true,
        subcommands = {
                AutomationCommand.ListPoliciesCommand.class,
                AutomationCommand.GetPolicyCommand.class,
                AutomationCommand.EnableCommand.class,
                AutomationCommand.DisableCommand.class,
                AutomationCommand.DeleteCommand.class,
                AutomationCommand.ApprovalsCommand.class,
                AutomationCommand.ApproveCommand.class,
                AutomationCommand.RejectCommand.class,
                AutomationCommand.ExecutionsCommand.class
        }
)
public class AutomationCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault automation --help' to view available automation subcommands.");
        return 0;
    }

    @Command(name = "policies", aliases = {"list"}, description = "List security automation policies")
    public static class ListPoliciesCommand extends BaseCommand {

        @Option(names = {"--limit"}, description = "Maximum policies to display", defaultValue = "50")
        private int limit;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                List<AutomationPolicyDto> policies = client.listAutomationPolicies(workspaceId, limit);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(policies);
                    return 0;
                }

                if (policies.isEmpty()) {
                    printer.info("No automation policies configured in workspace: " + ctx.workspace());
                    return 0;
                }

                TableFormatter table = new TableFormatter("POLICY ID", "NAME", "ENABLED", "PRIORITY", "SCOPE", "APPROVAL", "DRY-RUN", "TRIGGERS");
                for (AutomationPolicyDto p : policies) {
                    table.addRow(
                            p.id().toString(),
                            p.name(),
                            p.enabled() ? "ACTIVE" : "DISABLED",
                            String.valueOf(p.priority()),
                            p.scopeType() != null ? p.scopeType() : "WORKSPACE",
                            p.approvalRequired() ? "YES" : "NO",
                            p.dryRun() ? "YES" : "NO",
                            p.triggerEventTypes() != null ? p.triggerEventTypes() : "*"
                    );
                }
                printer.raw(table.render());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to list automation policies: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Get automation policy details and conditions")
    public static class GetPolicyCommand extends BaseCommand {

        @Parameters(index = "0", description = "Policy UUID")
        private UUID policyId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                AutomationPolicyDto p = client.getAutomationPolicy(workspaceId, policyId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(p);
                    return 0;
                }

                printer.bold("Automation Policy: " + p.name() + " (" + p.id() + ")");
                printer.item("Description", p.description() != null ? p.description() : "—");
                printer.item("Status", p.enabled() ? "ENABLED" : "DISABLED");
                printer.item("Priority", String.valueOf(p.priority()));
                printer.item("Scope", p.scopeType() != null ? p.scopeType() : "WORKSPACE");
                printer.item("Dry-Run Mode", p.dryRun() ? "YES" : "NO");
                printer.item("Approval Required", p.approvalRequired() ? "YES" : "NO");
                printer.item("Triggers", p.triggerEventTypes() != null ? p.triggerEventTypes() : "*");
                printer.bold("\nConditions DSL JSON:");
                printer.raw(p.conditionsJson() != null ? p.conditionsJson() : "[]\n");
                printer.bold("\nActions JSON:");
                printer.raw(p.actionsJson() != null ? p.actionsJson() : "[]\n");
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to get policy: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "enable", description = "Enable an automation policy")
    public static class EnableCommand extends BaseCommand {

        @Parameters(index = "0", description = "Policy UUID")
        private UUID policyId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                AutomationPolicyDto p = client.enableAutomationPolicy(workspaceId, policyId);
                printer.success("Automation policy '" + p.name() + "' has been enabled.");
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to enable policy: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "disable", description = "Disable an automation policy")
    public static class DisableCommand extends BaseCommand {

        @Parameters(index = "0", description = "Policy UUID")
        private UUID policyId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                AutomationPolicyDto p = client.disableAutomationPolicy(workspaceId, policyId);
                printer.success("Automation policy '" + p.name() + "' has been disabled.");
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to disable policy: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "delete", description = "Delete an automation policy")
    public static class DeleteCommand extends BaseCommand {

        @Parameters(index = "0", description = "Policy UUID")
        private UUID policyId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                client.deleteAutomationPolicy(workspaceId, policyId);
                printer.success("Automation policy " + policyId + " deleted successfully.");
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to delete policy: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "approvals", description = "List pending or decided automation approvals")
    public static class ApprovalsCommand extends BaseCommand {

        @Option(names = {"--status"}, description = "Filter by status: PENDING, APPROVED, REJECTED, EXPIRED")
        private String status;

        @Option(names = {"--limit"}, description = "Maximum approvals to display", defaultValue = "50")
        private int limit;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                List<AutomationApprovalDto> approvals = client.listAutomationApprovals(workspaceId, status, limit);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(approvals);
                    return 0;
                }

                if (approvals.isEmpty()) {
                    printer.info("No approvals matching filter.");
                    return 0;
                }

                TableFormatter table = new TableFormatter("APPROVAL ID", "POLICY ID", "ACTION", "STATUS", "EXPIRES AT", "REQUESTED BY");
                for (AutomationApprovalDto a : approvals) {
                    table.addRow(
                            a.id().toString(),
                            a.policyId() != null ? a.policyId().toString() : "—",
                            a.actionType(),
                            a.status(),
                            a.expiresAt() != null ? a.expiresAt().toString() : "Never",
                            a.requestedBy() != null ? a.requestedBy().toString() : "System"
                    );
                }
                printer.raw(table.render());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to list approvals: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "approve", description = "Approve a sensitive automation action")
    public static class ApproveCommand extends BaseCommand {

        @Parameters(index = "0", description = "Approval UUID")
        private UUID approvalId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                AutomationApprovalDto decided = client.decideAutomationApproval(workspaceId, approvalId, true, null);
                printer.success("Approval " + approvalId + " APPROVED and side-effects executed.");
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to approve automation action: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "reject", description = "Reject a sensitive automation action")
    public static class RejectCommand extends BaseCommand {

        @Parameters(index = "0", description = "Approval UUID")
        private UUID approvalId;

        @Option(names = {"--reason"}, description = "Reason for rejection", defaultValue = "Rejected via CLI")
        private String reason;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                AutomationApprovalDto decided = client.decideAutomationApproval(workspaceId, approvalId, false, reason);
                printer.success("Approval " + approvalId + " REJECTED.");
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to reject automation action: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "executions", description = "List automation policy execution audit logs")
    public static class ExecutionsCommand extends BaseCommand {

        @Option(names = {"--policy-id"}, description = "Filter by Policy UUID")
        private UUID policyId;

        @Option(names = {"--status"}, description = "Filter by status: SUCCESS, FAILED, DRY_RUN, WAITING_APPROVAL, SKIPPED")
        private String status;

        @Option(names = {"--limit"}, description = "Maximum executions to display", defaultValue = "50")
        private int limit;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                List<AutomationExecutionDto> executions = client.listAutomationExecutions(workspaceId, policyId, status, limit);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(executions);
                    return 0;
                }

                if (executions.isEmpty()) {
                    printer.info("No automation executions found.");
                    return 0;
                }

                TableFormatter table = new TableFormatter("EXECUTION ID", "TRIGGER EVENT", "STATUS", "DRY-RUN", "DURATION (MS)", "EXECUTED AT");
                for (AutomationExecutionDto e : executions) {
                    table.addRow(
                            e.id().toString(),
                            e.triggerEventType(),
                            e.status(),
                            e.dryRun() ? "YES" : "NO",
                            String.valueOf(e.durationMs()),
                            e.createdAt() != null ? e.createdAt().toString() : "—"
                    );
                }
                printer.raw(table.render());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to list executions: " + ex.getMessage());
                return 1;
            }
        }
    }
}
