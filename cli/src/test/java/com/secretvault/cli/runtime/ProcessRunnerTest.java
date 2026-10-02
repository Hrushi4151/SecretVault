package com.secretvault.cli.runtime;

import com.secretvault.cli.output.ConsolePrinter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessRunnerTest {

    @Test
    @DisplayName("Executes basic command and returns 0 exit code")
    void executesBasicCommandSuccessfully() {
        ConsolePrinter printer = new ConsolePrinter();
        printer.setQuiet(true);
        ProcessRunner runner = new ProcessRunner(printer);

        int exitCode = runner.runProcess(
                List.of("java", "-version"),
                Map.of("TEST_VAR", "injected_value"),
                new File(".")
        );

        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    @DisplayName("Returns non-zero exit code when child process fails")
    void returnsNonZeroExitCodeOnFailure() {
        ConsolePrinter printer = new ConsolePrinter();
        printer.setQuiet(true);
        ProcessRunner runner = new ProcessRunner(printer);

        // Run java with non-existent invalid option
        int exitCode = runner.runProcess(
                List.of("java", "--invalid-non-existent-option-xyz"),
                Map.of(),
                new File(".")
        );

        assertThat(exitCode).isNotEqualTo(0);
    }

    @Test
    @DisplayName("Returns 127 when executable does not exist")
    void returns127WhenExecutableNotFound() {
        ConsolePrinter printer = new ConsolePrinter();
        printer.setQuiet(true);
        ProcessRunner runner = new ProcessRunner(printer);

        int exitCode = runner.runProcess(
                List.of("non_existent_executable_12345"),
                Map.of(),
                new File(".")
        );

        assertThat(exitCode).isEqualTo(127);
    }
}
