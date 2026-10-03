package com.secretvault.cli.command;

import com.secretvault.cli.SecretVaultCli;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class Phase13CliCommandTest {

    @Test
    @DisplayName("Registers all Phase 13 commands in root help output")
    void registersPhase13CommandsInHelp() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("--help");
        assertThat(exitCode).isEqualTo(0);
        String help = out.toString();

        assertThat(help).contains("events");
        assertThat(help).contains("automation");
        assertThat(help).contains("webhook");
        assertThat(help).contains("incident");
        assertThat(help).contains("notification");
    }

    @Test
    @DisplayName("Displays events help and subcommands")
    void eventsHelp() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("events", "--help");
        assertThat(exitCode).isEqualTo(0);
        String help = out.toString();
        assertThat(help).contains("list");
        assertThat(help).contains("get");
        assertThat(help).contains("replay");
        assertThat(help).contains("replays");
    }

    @Test
    @DisplayName("Displays automation help and subcommands")
    void automationHelp() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("automation", "--help");
        assertThat(exitCode).isEqualTo(0);
        String help = out.toString();
        assertThat(help).contains("policies");
        assertThat(help).contains("approvals");
        assertThat(help).contains("approve");
        assertThat(help).contains("reject");
        assertThat(help).contains("executions");
    }

    @Test
    @DisplayName("Displays webhook help and subcommands")
    void webhookHelp() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("webhook", "--help");
        assertThat(exitCode).isEqualTo(0);
        String help = out.toString();
        assertThat(help).contains("list");
        assertThat(help).contains("create");
        assertThat(help).contains("delete");
        assertThat(help).contains("deliveries");
        assertThat(help).contains("replay");
    }

    @Test
    @DisplayName("Displays incident help and subcommands")
    void incidentHelp() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("incident", "--help");
        assertThat(exitCode).isEqualTo(0);
        String help = out.toString();
        assertThat(help).contains("list");
        assertThat(help).contains("get");
        assertThat(help).contains("create");
        assertThat(help).contains("update-status");
    }

    @Test
    @DisplayName("Displays notification help and subcommands")
    void notificationHelp() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("notification", "--help");
        assertThat(exitCode).isEqualTo(0);
        String help = out.toString();
        assertThat(help).contains("list");
        assertThat(help).contains("read");
        assertThat(help).contains("read-all");
    }
}
