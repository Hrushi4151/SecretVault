package com.secretvault.cli.command;

import com.secretvault.cli.SecretVaultCli;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phase 15: AI Copilot CLI Command Tests")
class AiCommandTest {

    @Test
    @DisplayName("Registers AI command group in root CLI help")
    void testRegistersAiInHelp() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("--help");
        assertThat(exitCode).isEqualTo(0);
        String help = out.toString();

        assertThat(help).contains("ai");
    }

    @Test
    @DisplayName("Displays ai subcommands help output")
    void testAiHelpSubcommands() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("ai", "--help");
        assertThat(exitCode).isEqualTo(0);
        String help = out.toString();

        assertThat(help).contains("ask");
        assertThat(help).contains("rca");
        assertThat(help).contains("posture");
        assertThat(help).contains("plans");
        assertThat(help).contains("budget");
    }

    @Test
    @DisplayName("Displays ai plans subcommands help output")
    void testAiPlansHelp() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("ai", "plans", "--help");
        assertThat(exitCode).isEqualTo(0);
        String help = out.toString();

        assertThat(help).contains("list");
        assertThat(help).contains("generate");
        assertThat(help).contains("approve");
        assertThat(help).contains("execute");
        assertThat(help).contains("reject");
    }

    @Test
    @DisplayName("Displays ai rca options help output")
    void testAiRcaHelp() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("ai", "rca", "--help");
        assertThat(exitCode).isEqualTo(0);
        String help = out.toString();

        assertThat(help).contains("--target-type");
        assertThat(help).contains("--target-id");
        assertThat(help).contains("--logs");
    }
}
