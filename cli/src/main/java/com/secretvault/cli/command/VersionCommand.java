package com.secretvault.cli.command;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.HealthDto;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.OutputFormat;
import picocli.CommandLine.Command;

import java.util.Map;

@Command(
        name = "version",
        description = "Display SecretVault CLI version and backend API build information"
)
public class VersionCommand extends BaseCommand {

    public static final String CLI_VERSION = "1.0.0";
    public static final String BUILD_DATE = "2026-10-03";

    @Override
    public Integer call() {
        ConsolePrinter printer = getPrinter();
        String serverVersion = "unknown";
        try {
            SecretVaultApiClient client = new SecretVaultApiClient(resolveContext().server());
            HealthDto health = client.getHealth();
            serverVersion = health.version();
        } catch (Exception ignored) {
        }

        if (getOutputFormat() == OutputFormat.JSON) {
            printer.printJson(Map.of(
                    "cliVersion", CLI_VERSION,
                    "buildDate", BUILD_DATE,
                    "serverVersion", serverVersion,
                    "javaRuntime", System.getProperty("java.version", "21")
            ));
            return 0;
        }

        printer.info("SecretVault CLI v" + CLI_VERSION + " (" + BUILD_DATE + ")");
        printer.info("Target Server:   " + resolveContext().server() + " (API v" + serverVersion + ")");
        printer.info("Java Runtime:    " + System.getProperty("java.version", "21"));
        return 0;
    }
}
