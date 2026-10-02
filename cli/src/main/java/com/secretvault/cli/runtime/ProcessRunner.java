package com.secretvault.cli.runtime;

import com.secretvault.cli.output.ConsolePrinter;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Executes child processes with injected environments, signal forwarding, and exact exit code propagation.
 * Does NOT invoke shell strings, protecting against command injection.
 */
public class ProcessRunner {

    private final ConsolePrinter printer;

    public ProcessRunner(ConsolePrinter printer) {
        this.printer = printer;
    }

    public int runProcess(List<String> command, Map<String, String> environmentVariables, File workingDir) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("No command specified to run");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDir != null) {
            processBuilder.directory(workingDir);
        }

        // Apply injected environment
        Map<String, String> procEnv = processBuilder.environment();
        procEnv.clear();
        procEnv.putAll(environmentVariables);

        // Inherit standard I/O for interactive terminal applications
        processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process process = null;
        Thread shutdownHook = null;
        try {
            process = processBuilder.start();
            final Process childProcess = process;

            // Register shutdown hook to forward SIGINT / SIGTERM
            shutdownHook = new Thread(() -> {
                if (childProcess.isAlive()) {
                    childProcess.toHandle().descendants().forEach(ProcessHandle::destroy);
                    childProcess.destroy();
                }
            });
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            int exitCode = process.waitFor();
            return exitCode;
        } catch (IOException e) {
            printer.error("Failed to start child process: " + e.getMessage());
            return 127; // Command not found / execution error
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null && process.isAlive()) {
                process.destroy();
            }
            return 130; // Interrupted (SIGINT)
        } finally {
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // Shutdown in progress
                }
            }
        }
    }
}
