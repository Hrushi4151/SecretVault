package com.secretvault.cli.command;

import com.secretvault.cli.auth.AuthManager;
import com.secretvault.cli.auth.StoredCredentials;
import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.AuthDtos;
import com.secretvault.cli.config.CliConfig;
import com.secretvault.cli.config.ConfigManager;
import com.secretvault.cli.config.ContextManager;
import com.secretvault.cli.config.ProfileConfig;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.OutputFormat;
import com.secretvault.cli.output.TableFormatter;
import com.secretvault.cli.security.RedactionHelper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Command(
        name = "auth",
        description = "Manage authentication, profiles, and secure local session credentials",
        subcommands = {
                AuthCommand.LoginCommand.class,
                AuthCommand.OidcLoginCommand.class,
                AuthCommand.LogoutCommand.class,
                AuthCommand.StatusCommand.class,
                AuthCommand.WhoamiCommand.class,
                AuthCommand.ProfilesCommand.class,
                AuthCommand.SwitchCommand.class
        }
)
public class AuthCommand extends BaseCommand {

    @Override
    public Integer call() {
        getPrinter().info("Run 'secretvault auth --help' to view available authentication subcommands.");
        return 0;
    }

    @Command(name = "login", description = "Authenticate with SecretVault server and store encrypted session tokens")
    public static class LoginCommand extends BaseCommand {

        @Option(names = {"--email"}, description = "User email address")
        private String email;

        @Option(names = {"--password-stdin"}, description = "Read password from standard input (non-echo)")
        private boolean passwordStdin;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ContextManager.ResolvedContext ctx = resolveContext();
            String targetProfile = ctx.profile();
            String targetServer = ctx.server();

            printer.info("Authenticating with SecretVault server: " + targetServer + " (Profile: " + targetProfile + ")");

            String userEmail = email;
            if (userEmail == null || userEmail.isBlank()) {
                if (System.console() != null) {
                    userEmail = System.console().readLine("Email: ");
                } else {
                    try {
                        BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                        System.out.print("Email: ");
                        userEmail = br.readLine();
                    } catch (Exception e) {
                        printer.error("Failed to read email input: " + e.getMessage());
                        return 1;
                    }
                }
            }

            if (userEmail == null || userEmail.isBlank()) {
                printer.error("Email is required for authentication.");
                return 1;
            }

            char[] passwordChars;
            if (passwordStdin) {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                    String line = br.readLine();
                    passwordChars = line != null ? line.toCharArray() : new char[0];
                } catch (Exception e) {
                    printer.error("Failed to read password from stdin: " + e.getMessage());
                    return 1;
                }
            } else if (System.console() != null) {
                passwordChars = System.console().readPassword("Password: ");
            } else {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                    System.out.print("Password: ");
                    String line = br.readLine();
                    passwordChars = line != null ? line.toCharArray() : new char[0];
                } catch (Exception e) {
                    printer.error("Failed to read password: " + e.getMessage());
                    return 1;
                }
            }

            if (passwordChars == null || passwordChars.length == 0) {
                printer.error("Password cannot be empty.");
                return 1;
            }

            try {
                AuthManager authManager = getAuthManager();
                AuthDtos.AuthResponse authResp = authManager.login(targetProfile, targetServer, userEmail, passwordChars);

                if (authResp.mfaRequired()) {
                    printer.warn("MFA is required for this account. Challenge ID: " + authResp.mfaChallengeId());
                    printer.info("Please use web console or complete MFA verification to obtain session.");
                    return 1;
                }

                printer.success("Authenticated successfully as " + userEmail);
                if (authResp.activeWorkspace() != null) {
                    printer.info("Active workspace context: " + authResp.activeWorkspace().name() + " (" + authResp.activeWorkspace().slug() + ")");
                }
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            } finally {
                RedactionHelper.wipe(passwordChars);
            }
        }
    }

    @Command(name = "oidc", description = "Exchange an OIDC workload token (e.g., GitHub Actions, GitLab CI) for a SecretVault machine session")
    public static class OidcLoginCommand extends BaseCommand {

        @Option(names = {"--provider-id"}, description = "OIDC Provider UUID in SecretVault")
        private UUID providerId;

        @Option(names = {"--issuer"}, description = "OIDC Issuer URL (if provider-id is not specified)")
        private String issuer;

        @Option(names = {"--token"}, description = "Raw OIDC ID Token (JWT)")
        private String token;

        @Option(names = {"--token-stdin"}, description = "Read OIDC ID Token from standard input")
        private boolean tokenStdin;

        @Option(names = {"--token-env"}, description = "Environment variable containing the OIDC token")
        private String tokenEnv;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ContextManager.ResolvedContext ctx = resolveContext();
            String targetProfile = ctx.profile();
            String targetServer = ctx.server();

            String rawToken = token;
            if (rawToken == null || rawToken.isBlank()) {
                if (tokenEnv != null && !tokenEnv.isBlank()) {
                    rawToken = System.getenv(tokenEnv);
                } else if (tokenStdin) {
                    try {
                        BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                        rawToken = br.readLine();
                    } catch (Exception e) {
                        printer.error("Failed to read OIDC token from stdin: " + e.getMessage());
                        return 1;
                    }
                } else {
                    // Check standard CI environment variables as convenience
                    String ghToken = System.getenv("ACTIONS_ID_TOKEN_REQUEST_TOKEN");
                    String gitlabToken = System.getenv("CI_JOB_JWT_V2");
                    if (ghToken != null && !ghToken.isBlank()) {
                        rawToken = ghToken;
                    } else if (gitlabToken != null && !gitlabToken.isBlank()) {
                        rawToken = gitlabToken;
                    }
                }
            }

            if (rawToken == null || rawToken.isBlank()) {
                printer.error("OIDC token is required. Use --token, --token-stdin, or --token-env.");
                return 1;
            }

            try {
                printer.info("Exchanging OIDC token with SecretVault at " + targetServer + " (Profile: " + targetProfile + ")...");
                AuthManager authManager = getAuthManager();
                AuthDtos.OidcTokenResponse resp = authManager.loginWithOidc(targetProfile, targetServer, providerId, issuer, rawToken);

                printer.success("Authenticated successfully as Machine Identity: " + 
                        (resp.machineIdentity() != null ? resp.machineIdentity().name() : "Machine"));
                printer.info("Session valid for " + resp.expiresIn() + " seconds.");
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "logout", description = "Sign out and securely revoke stored credentials")
    public static class LogoutCommand extends BaseCommand {

        @Option(names = {"--all"}, description = "Log out from all profiles and clear all stored credentials")
        private boolean logoutAll;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            AuthManager authManager = getAuthManager();

            if (logoutAll) {
                authManager.logoutAll();
                printer.success("Logged out from all profiles. Credential storage cleared.");
                return 0;
            }

            ContextManager.ResolvedContext ctx = resolveContext();
            authManager.logout(ctx.profile(), ctx.server());
            printer.success("Logged out from profile '" + ctx.profile() + "' (" + ctx.server() + ").");
            return 0;
        }
    }

    @Command(name = "status", description = "Check active authentication session status and credential validity")
    public static class StatusCommand extends BaseCommand {

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ContextManager.ResolvedContext ctx = resolveContext();
            AuthManager authManager = getAuthManager();

            Optional<StoredCredentials> credsOpt = authManager.getCredentials(ctx.profile(), ctx.server());
            if (credsOpt.isEmpty()) {
                printer.warn("Not authenticated for profile '" + ctx.profile() + "' on " + ctx.server());
                printer.info("Run 'secretvault auth login' to authenticate.");
                return 1;
            }

            StoredCredentials creds = credsOpt.get();
            if (getOutputFormat() == OutputFormat.JSON) {
                printer.printJson(Map.of(
                        "profile", ctx.profile(),
                        "server", ctx.server(),
                        "authenticated", true,
                        "userEmail", creds.userEmail() != null ? creds.userEmail() : "unknown",
                        "userId", creds.userId() != null ? creds.userId().toString() : "",
                        "expiresAt", creds.expiresAt() != null ? creds.expiresAt().toString() : "",
                        "expired", creds.isExpired()
                ));
                return 0;
            }

            TableFormatter table = new TableFormatter("PROPERTY", "VALUE");
            table.addRow("Profile", ctx.profile());
            table.addRow("Server", ctx.server());
            table.addRow("User", creds.userEmail() != null ? creds.userEmail() : "unknown");
            table.addRow("Status", creds.isExpired() ? "EXPIRED (will auto-refresh on request)" : "ACTIVE");
            table.addRow("Expires At", creds.expiresAt() != null ? creds.expiresAt().toString() : "N/A");

            printer.raw(table.render());
            return 0;
        }
    }

    @Command(name = "whoami", description = "Display current authenticated user identity and workspace context")
    public static class WhoamiCommand extends BaseCommand {

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            try {
                SecretVaultApiClient client = getAuthenticatedClient();
                AuthDtos.UserResponse user = client.getCurrentUser();
                ContextManager.ResolvedContext ctx = resolveContext();

                if (getOutputFormat() == OutputFormat.JSON) {
                    printer.printJson(Map.of(
                            "user", user,
                            "profile", ctx.profile(),
                            "server", ctx.server(),
                            "workspace", ctx.workspace() != null ? ctx.workspace() : ""
                    ));
                    return 0;
                }

                TableFormatter table = new TableFormatter("FIELD", "VALUE");
                table.addRow("User ID", user.id().toString());
                table.addRow("Email", user.email());
                table.addRow("Full Name", user.fullName() != null ? user.fullName() : "N/A");
                table.addRow("MFA Enabled", String.valueOf(user.isMfaEnabled()));
                table.addRow("Account Status", user.status());
                table.addRow("Profile", ctx.profile());
                table.addRow("Server", ctx.server());
                if (ctx.workspace() != null) {
                    table.addRow("Workspace", ctx.workspace());
                }

                printer.raw(table.render());
                return 0;
            } catch (Exception e) {
                printer.error(e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "profiles", description = "List all configured SecretVault profiles")
    public static class ProfilesCommand extends BaseCommand {

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            CliConfig config = getConfigManager().loadConfig();
            String activeProfile = config.getDefaultProfile();

            if (getOutputFormat() == OutputFormat.JSON) {
                printer.printJson(Map.of(
                        "activeProfile", activeProfile,
                        "profiles", config.getProfiles()
                ));
                return 0;
            }

            TableFormatter table = new TableFormatter("ACTIVE", "PROFILE", "SERVER", "WORKSPACE", "PROJECT", "ENVIRONMENT");
            for (Map.Entry<String, ProfileConfig> entry : config.getProfiles().entrySet()) {
                String name = entry.getKey();
                ProfileConfig p = entry.getValue();
                boolean isActive = name.equalsIgnoreCase(activeProfile);

                table.addRow(
                        isActive ? "*" : " ",
                        name,
                        p.getServer(),
                        p.getWorkspaceSlug() != null ? p.getWorkspaceSlug() : (p.getWorkspaceId() != null ? p.getWorkspaceId() : "-"),
                        p.getProjectSlug() != null ? p.getProjectSlug() : (p.getProjectId() != null ? p.getProjectId() : "-"),
                        p.getEnvironmentSlug() != null ? p.getEnvironmentSlug() : (p.getEnvironmentId() != null ? p.getEnvironmentId() : "-")
                );
            }

            printer.raw(table.render());
            return 0;
        }
    }

    @Command(name = "switch", description = "Switch active configuration profile")
    public static class SwitchCommand extends BaseCommand {

        @Parameters(index = "0", description = "Profile name to switch to")
        private String targetProfile;

        @Override
        public Integer call() {
            ConsolePrinter printer = getPrinter();
            ConfigManager cm = getConfigManager();
            CliConfig config = cm.loadConfig();

            if (!config.getProfiles().containsKey(targetProfile)) {
                config.getProfiles().put(targetProfile, new ProfileConfig("http://localhost:8080"));
            }

            config.setDefaultProfile(targetProfile);
            cm.saveConfig(config);

            printer.success("Switched active profile to '" + targetProfile + "'.");
            return 0;
        }
    }
}
