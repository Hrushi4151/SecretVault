package com.secretvault.cli.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secretvault.cli.security.RedactionHelper;

import java.io.PrintStream;

/**
 * Standardized terminal printer with color styling, JSON serialization, and stderr separation.
 */
public class ConsolePrinter {

    // ANSI Colors
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    private final PrintStream out;
    private final PrintStream err;
    private final ObjectMapper objectMapper;
    private boolean colorEnabled = true;
    private boolean quiet = false;

    public ConsolePrinter() {
        this(System.out, System.err);
    }

    public ConsolePrinter(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);

        // Disable colors if NO_COLOR env var exists or plain dumb terminal
        if (System.getenv("NO_COLOR") != null || "dumb".equals(System.getenv("TERM"))) {
            this.colorEnabled = false;
        }
    }

    public void setColorEnabled(boolean colorEnabled) {
        this.colorEnabled = colorEnabled;
    }

    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public void info(String message) {
        if (!quiet) {
            out.println(color(message, ""));
        }
    }

    public void highlight(String message) {
        if (!quiet) {
            out.println(color(message, CYAN + BOLD));
        }
    }

    public void success(String message) {
        if (!quiet) {
            out.println(color("✓ " + message, GREEN));
        }
    }

    public void warn(String message) {
        err.println(color("⚠ WARNING: " + RedactionHelper.redact(message), YELLOW));
    }

    public void error(String message) {
        err.println(color("✖ ERROR: " + RedactionHelper.redact(message), RED + BOLD));
    }

    public void raw(String text) {
        out.println(text);
    }

    public void printJson(Object object) {
        try {
            out.println(objectMapper.writeValueAsString(object));
        } catch (Exception e) {
            err.println(color("Error formatting JSON: " + e.getMessage(), RED));
        }
    }

    private String color(String text, String ansiCode) {
        if (!colorEnabled || ansiCode == null || ansiCode.isEmpty()) {
            return text;
        }
        return ansiCode + text + RESET;
    }
}
