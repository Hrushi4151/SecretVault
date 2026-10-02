package com.secretvault.cli.output;

public enum OutputFormat {
    HUMAN,
    JSON,
    QUIET,
    PLAIN;

    public static OutputFormat fromString(String format) {
        if (format == null) return HUMAN;
        return switch (format.toLowerCase().trim()) {
            case "json" -> JSON;
            case "quiet", "q" -> QUIET;
            case "plain", "raw" -> PLAIN;
            default -> HUMAN;
        };
    }
}
