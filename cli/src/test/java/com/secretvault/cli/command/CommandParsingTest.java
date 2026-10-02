package com.secretvault.cli.command;

import com.secretvault.cli.SecretVaultCli;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class CommandParsingTest {

    @Test
    @DisplayName("Parses global options and subcommands correctly")
    void parsesGlobalOptions() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("--help");
        assertThat(exitCode).isEqualTo(0);
        assertThat(out.toString()).contains("SecretVault — Enterprise Secret Management");
        assertThat(out.toString()).contains("auth");
        assertThat(out.toString()).contains("secret");
        assertThat(out.toString()).contains("run");
    }

    @Test
    @DisplayName("Executes version command without error")
    void executesVersionCommand() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("version");
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    @DisplayName("Generates bash completion script without error")
    void generatesBashCompletionScript() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out));

        int exitCode = cmd.execute("completion", "bash");
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    @DisplayName("Generates zsh and powershell completions")
    void generatesOtherShellCompletions() {
        SecretVaultCli cli = new SecretVaultCli();
        CommandLine cmd = new CommandLine(cli);

        assertThat(cmd.execute("completion", "zsh")).isEqualTo(0);
        assertThat(cmd.execute("completion", "powershell")).isEqualTo(0);
        assertThat(cmd.execute("completion", "fish")).isEqualTo(0);
    }
}
