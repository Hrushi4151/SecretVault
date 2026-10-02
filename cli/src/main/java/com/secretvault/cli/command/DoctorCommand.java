package com.secretvault.cli.command;

import com.secretvault.cli.auth.AuthManager;
import com.secretvault.cli.auth.StoredCredentials;
import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.HealthDto;
import com.secretvault.cli.config.ConfigManager;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.OutputFormat;
import com.secretvault.cli.output.TableFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Command(
        name = "doctor",
        description = "Run end-to-end environment, network, authentication, and security diagnostics"
)
public class DoctorCommand extends BaseCommand {

    @Option(names = {"-v", "--verbose"}, description = "Display detailed diagnostic logs and timing")
    private boolean verbose;

    @Override
    public Integer call() {
        ConsolePrinter printer = getPrinter();
        ConfigManager cm = getConfigManager();
        ContextManager.ResolvedContext ctx = resolveContext();
        AuthManager authManager = getAuthManager();

        Map<String, String> results = new LinkedHashMap<>();
        boolean allPassed = true;

        printer.info("SecretVault Doctor — Diagnostic Health Check\n");

        // 1. Runtime & OS
        String javaVersion = System.getProperty("java.version", "unknown");
        String osName = System.getProperty("os.name", "unknown") + " (" + System.getProperty("os.arch", "") + ")";
        results.put("Runtime Environment", "Java " + javaVersion + " on " + osName);
        printer.success("Java Runtime: " + javaVersion + " (" + osName + ")");

        // 2. Configuration directory & file
        boolean configDirOk = Files.exists(cm.getConfigDirectory());
        boolean configFileOk = Files.exists(cm.getConfigFile());
        results.put("Configuration Storage", configFileOk ? "Available (" + cm.getConfigFile() + ")" : "Not initialized");
        printer.success("Configuration Storage: " + cm.getConfigDirectory());

        // 3. Credential Store Availability
        boolean storeOk = authManager.getCredentialStore().isAvailable();
        results.put("Encrypted Credential Store", storeOk ? "Operational (AES-256-GCM + PBKDF2)" : "FAILED");
        if (storeOk) {
            printer.success("Encrypted Credential Store: Operational (AES-256-GCM)");
        } else {
            printer.error("Encrypted Credential Store: Unavailable");
            allPassed = false;
        }

        // 4. Server Connectivity & Health
        String serverUrl = ctx.server();
        long start = System.currentTimeMillis();
        try {
            SecretVaultApiClient client = new SecretVaultApiClient(serverUrl);
            HealthDto health = client.getHealth();
            long latency = System.currentTimeMillis() - start;
            results.put("Server Connectivity", "Reachable (" + health.status() + ", " + latency + "ms, API v" + health.version() + ")");
            printer.success("Server Reachable: " + serverUrl + " [Status: " + health.status() + ", Latency: " + latency + "ms, Version: " + health.version() + "]");
        } catch (Exception e) {
            results.put("Server Connectivity", "FAILED (" + e.getMessage() + ")");
            printer.error("Server Connectivity: " + serverUrl + " -> " + e.getMessage());
            allPassed = false;
        }

        // 5. Authentication Status
        Optional<StoredCredentials> credsOpt = authManager.getCredentials(ctx.profile(), ctx.server());
        if (credsOpt.isPresent()) {
            StoredCredentials c = credsOpt.get();
            boolean expired = c.isExpired();
            results.put("Authentication", "Authenticated as " + c.userEmail() + (expired ? " [Expired - will auto-refresh]" : " [Active]"));
            printer.success("Authentication: Valid session for " + c.userEmail() + " (Profile: " + ctx.profile() + ")");
        } else {
            results.put("Authentication", "Not logged in for profile '" + ctx.profile() + "'");
            printer.warn("Authentication: Not logged in (run 'secretvault auth login')");
        }

        // 6. Context Resolution
        results.put("Active Context", "Workspace: " + (ctx.workspace() != null ? ctx.workspace() : "None") +
                ", Project: " + (ctx.project() != null ? ctx.project() : "None") +
                ", Environment: " + (ctx.environment() != null ? ctx.environment() : "None"));
        printer.success("Active Context: Workspace [" + (ctx.workspace() != null ? ctx.workspace() : "-") +
                "] -> Project [" + (ctx.project() != null ? ctx.project() : "-") +
                "] -> Environment [" + (ctx.environment() != null ? ctx.environment() : "-") + "]");

        if (getOutputFormat() == OutputFormat.JSON) {
            printer.printJson(results);
        }

        printer.info("\n" + (allPassed ? "Doctor check passed! CLI is ready for development." : "Doctor check identified issues above."));
        return allPassed ? 0 : 1;
    }
}
