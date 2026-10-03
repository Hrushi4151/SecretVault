package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.RepositoryCliDtos.RepositoryDto;
import com.secretvault.cli.client.dto.RepositoryCliDtos.ScanDto;
import com.secretvault.cli.output.ConsolePrinter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Command(
        name = "repository",
        aliases = {"repo"},
        description = "Manage code repositories and continuous secret leak monitoring",
        subcommands = {
                RepositoryCliCommand.ListReposCommand.class,
                RepositoryCliCommand.GetRepoCommand.class,
                RepositoryCliCommand.ConnectRepoCommand.class,
                RepositoryCliCommand.ScanRepoCommand.class
        }
)
public class RepositoryCliCommand extends BaseCommand {

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }

    @Command(name = "list", description = "List registered repositories in the active workspace")
    public static class ListReposCommand extends BaseCommand {
        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            UUID workspaceId = resolveWorkspaceId();
            if (workspaceId == null) {
                printer.error("Workspace required. Run 'secretvault workspace select <id>'.");
                return 3;
            }

            try {
                List<RepositoryDto> repos = getApiClient().listRepositories(workspaceId);
                if (repos.isEmpty()) {
                    printer.info("No repositories connected in this workspace.");
                    return 0;
                }

                printer.header(String.format("%-36s  %-10s  %-25s  %-10s  %-8s  %-8s",
                        "REPOSITORY ID", "PROVIDER", "REPO NAME", "VISIBILITY", "FINDINGS", "CRITICAL"));
                for (RepositoryDto r : repos) {
                    printer.row(String.format("%-36s  %-10s  %-25s  %-10s  %-8d  %-8d",
                            r.id(), r.provider(), r.owner() + "/" + r.name(), r.visibility(),
                            r.totalFindings(), r.criticalFindings()));
                }
                return 0;
            } catch (Exception e) {
                printer.error("Failed to list repositories: " + e.getMessage());
                return 5;
            }
        }
    }

    @Command(name = "get", description = "Get details of a registered repository")
    public static class GetRepoCommand extends BaseCommand {
        @Parameters(index = "0", description = "Repository UUID")
        private UUID repositoryId;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            UUID workspaceId = resolveWorkspaceId();
            if (workspaceId == null) return 3;

            try {
                RepositoryDto repo = getApiClient().getRepository(workspaceId, repositoryId);
                printer.header("Repository Details: " + repo.owner() + "/" + repo.name());
                printer.keyVal("ID", repo.id().toString());
                printer.keyVal("Provider", repo.provider());
                printer.keyVal("Default Branch", repo.defaultBranch());
                printer.keyVal("Clone URL", repo.cloneUrl() != null ? repo.cloneUrl() : "N/A");
                printer.keyVal("Visibility", repo.visibility());
                printer.keyVal("Status", repo.status());
                printer.keyVal("Total Findings", String.valueOf(repo.totalFindings()));
                printer.keyVal("Critical Findings", String.valueOf(repo.criticalFindings()));
                printer.keyVal("Last Scan", repo.lastScanAt() != null ? repo.lastScanAt().toString() : "Never");
                return 0;
            } catch (Exception e) {
                printer.error("Failed to get repository: " + e.getMessage());
                return 5;
            }
        }
    }

    @Command(name = "connect", description = "Connect a code repository for continuous scanning")
    public static class ConnectRepoCommand extends BaseCommand {
        @Option(names = {"--provider"}, description = "Repository provider: GITHUB, GITLAB, BITBUCKET (default: GITHUB)", defaultValue = "GITHUB")
        private String provider;

        @Option(names = {"--owner"}, required = true, description = "Repository owner / organization")
        private String owner;

        @Option(names = {"--name"}, required = true, description = "Repository name")
        private String name;

        @Option(names = {"--branch"}, description = "Default branch (default: main)", defaultValue = "main")
        private String branch;

        @Option(names = {"--clone-url"}, description = "Git Clone URL")
        private String cloneUrl;

        @Option(names = {"--visibility"}, description = "Visibility: PRIVATE, PUBLIC, INTERNAL (default: PRIVATE)", defaultValue = "PRIVATE")
        private String visibility;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            UUID workspaceId = resolveWorkspaceId();
            if (workspaceId == null) return 3;

            try {
                Map<String, Object> req = Map.of(
                        "provider", provider,
                        "owner", owner,
                        "name", name,
                        "defaultBranch", branch,
                        "cloneUrl", cloneUrl != null ? cloneUrl : "",
                        "visibility", visibility
                );
                RepositoryDto connected = getApiClient().connectRepository(workspaceId, req);
                printer.success("Connected repository " + connected.owner() + "/" + connected.name() + " (ID: " + connected.id() + ")");
                return 0;
            } catch (Exception e) {
                printer.error("Failed to connect repository: " + e.getMessage());
                return 5;
            }
        }
    }

    @Command(name = "scan", description = "Trigger a remote secret scan on a connected repository")
    public static class ScanRepoCommand extends BaseCommand {
        @Parameters(index = "0", description = "Repository UUID")
        private UUID repositoryId;

        @Option(names = {"--type"}, description = "Scan type: FULL, INCREMENTAL, GIT_HISTORY (default: INCREMENTAL)", defaultValue = "INCREMENTAL")
        private String scanType;

        @Option(names = {"--branch"}, description = "Target branch (defaults to repo default branch)")
        private String branch;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            UUID workspaceId = resolveWorkspaceId();
            if (workspaceId == null) return 3;

            try {
                ScanDto scan = getApiClient().triggerScan(workspaceId, repositoryId, scanType, branch);
                printer.success("Scan initiated for repository " + repositoryId + " (Scan ID: " + scan.id() + ")");
                printer.info("Status: " + scan.status() + " | Scan Type: " + scan.scanType());
                return 0;
            } catch (Exception e) {
                printer.error("Failed to trigger scan: " + e.getMessage());
                return 5;
            }
        }
    }
}
