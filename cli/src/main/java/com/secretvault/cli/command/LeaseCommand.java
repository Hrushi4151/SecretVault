package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.RotationCliDtos.RenewLeaseRequest;
import com.secretvault.cli.client.dto.RotationCliDtos.RevokeLeaseRequest;
import com.secretvault.cli.client.dto.RotationCliDtos.SecretLeaseDto;
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
 * CLI command group for Runtime Secret Leases & Temporary Access Grants.
 */
@Command(
        name = "lease",
        aliases = {"leases"},
        description = "Manage runtime secret leases, dynamic TTL renewals, and revocations",
        subcommands = {
                LeaseCommand.ListCommand.class,
                LeaseCommand.GetCommand.class,
                LeaseCommand.RenewCommand.class,
                LeaseCommand.RevokeCommand.class
        }
)
public class LeaseCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault lease --help' to view available lease management subcommands.");
        return 0;
    }

    @Command(name = "list", description = "List active and historical secret leases")
    public static class ListCommand extends BaseCommand {

        @Option(names = {"--secret-id"}, description = "Filter leases by Secret ID")
        private UUID secretId;

        @Option(names = {"--consumer-id"}, description = "Filter leases by Consumer ID")
        private UUID consumerId;

        @Option(names = {"--limit"}, description = "Maximum number of leases to display", defaultValue = "50")
        private int limit;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                List<SecretLeaseDto> leases = client.listLeases(workspaceId, secretId, consumerId, limit);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(leases);
                    return 0;
                }

                if (leases.isEmpty()) {
                    printer.info("No leases found.");
                    return 0;
                }

                TableFormatter table = new TableFormatter("LEASE ID", "SECRET ID", "CONSUMER ID", "STATUS", "VERSION", "EXPIRES AT", "RENEWALS");
                for (SecretLeaseDto l : leases) {
                    table.addRow(
                            l.id().toString(),
                            l.secretId().toString().substring(0, 8) + "...",
                            l.consumerId() != null ? l.consumerId().toString().substring(0, 8) + "..." : "—",
                            l.status(),
                            l.secretVersionNumber() != null ? "v" + l.secretVersionNumber() : "—",
                            l.expiresAt() != null ? l.expiresAt().toString() : "—",
                            String.valueOf(l.renewalCount())
                    );
                }
                printer.raw(table.render());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to list secret leases: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "get", description = "Get detailed information about a secret lease")
    public static class GetCommand extends BaseCommand {

        @Parameters(index = "0", description = "Lease ID")
        private UUID leaseId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                SecretLeaseDto lease = client.getLease(workspaceId, leaseId);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(lease);
                    return 0;
                }

                printer.highlight("=== Secret Lease Details ===");
                printer.info("Lease ID:            " + lease.id());
                printer.info("Secret ID:           " + lease.secretId());
                printer.info("Secret Version:      " + (lease.secretVersionNumber() != null ? "v" + lease.secretVersionNumber() : "—"));
                printer.info("Workspace ID:        " + lease.workspaceId());
                printer.info("Project ID:          " + lease.projectId());
                printer.info("Environment ID:      " + lease.environmentId());
                printer.info("Consumer ID:         " + (lease.consumerId() != null ? lease.consumerId() : "—"));
                printer.info("Machine Identity ID: " + (lease.machineIdentityId() != null ? lease.machineIdentityId() : "—"));
                printer.info("Access Scope:        " + lease.accessScope());
                printer.info("Status:              " + lease.status());
                printer.info("TTL (Seconds):       " + lease.ttlSeconds() + "s");
                printer.info("Max Lifetime:        " + lease.maxLifetimeSeconds() + "s");
                printer.info("Renewal Count:       " + lease.renewalCount());
                printer.info("Issued At:           " + lease.issuedAt());
                printer.info("Expires At:          " + lease.expiresAt());
                printer.info("Last Renewed:        " + (lease.lastRenewedAt() != null ? lease.lastRenewedAt() : "Never"));
                if (lease.revocationReason() != null) {
                    printer.error("Revocation Reason:   " + lease.revocationReason());
                }

                return 0;
            } catch (Exception ex) {
                printer.error("Failed to fetch lease: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "renew", description = "Renew an active secret lease and extend its TTL")
    public static class RenewCommand extends BaseCommand {

        @Parameters(index = "0", description = "Lease ID")
        private UUID leaseId;

        @Option(names = {"--ttl"}, description = "Requested TTL extension in seconds (defaults to original TTL)")
        private Long ttlSeconds;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                RenewLeaseRequest req = new RenewLeaseRequest(ttlSeconds);
                SecretLeaseDto lease = client.renewLease(workspaceId, leaseId, req);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(lease);
                    return 0;
                }

                printer.success("✓ Lease renewed successfully: " + leaseId);
                printer.info("New Expires At: " + lease.expiresAt());
                printer.info("Status:         " + lease.status());
                printer.info("Renewal Count:  " + lease.renewalCount());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to renew lease: " + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "revoke", description = "Revoke an active secret lease immediately")
    public static class RevokeCommand extends BaseCommand {

        @Parameters(index = "0", description = "Lease ID")
        private UUID leaseId;

        @Option(names = {"--reason"}, description = "Reason for revocation", defaultValue = "Revoked via CLI")
        private String reason;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                ContextManager.ResolvedContext ctx = resolveContext();
                UUID workspaceId = resolveWorkspaceId(client, ctx.workspace());

                RevokeLeaseRequest req = new RevokeLeaseRequest(reason);
                SecretLeaseDto lease = client.revokeLease(workspaceId, leaseId, req);

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(lease);
                    return 0;
                }

                printer.success("✓ Lease revoked successfully: " + leaseId);
                printer.info("Status: " + lease.status());
                return 0;
            } catch (Exception ex) {
                printer.error("Failed to revoke lease: " + ex.getMessage());
                return 1;
            }
        }
    }
}
