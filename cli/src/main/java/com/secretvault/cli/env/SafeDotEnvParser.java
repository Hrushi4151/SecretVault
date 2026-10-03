package com.secretvault.cli.env;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Safe, data-only .env file parser.
 * STRICTLY PREVENTS shell execution, variable expansion, subshell execution, and code evaluation.
 */
public class SafeDotEnvParser {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");
    private static final int MAX_ENV_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB limit

    public static Map<String, String> parse(String content) {
        if (content == null || content.isBlank()) {
            return new LinkedHashMap<>();
        }

        if (content.length() > MAX_ENV_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(".env content exceeds maximum allowed limit of 5MB");
        }

        Map<String, String> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                String trimmed = line.trim();

                // Skip empty lines or full comments
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                // Strip leading 'export ' if present
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring(7).trim();
                }

                int equalIdx = trimmed.indexOf('=');
                if (equalIdx == -1) {
                    continue; // Skip lines without '='
                }

                String key = trimmed.substring(0, equalIdx).trim();
                String rawValue = trimmed.substring(equalIdx + 1).trim();

                if (!KEY_PATTERN.matcher(key).matches()) {
                    throw new IllegalArgumentException("Invalid key name on line " + lineNum + ": '" + key + "'");
                }

                String parsedValue = parseValue(rawValue);
                result.put(key, parsedValue);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse .env stream: " + e.getMessage(), e);
        }

        return result;
    }

    private static String parseValue(String raw) {
        if (raw.isEmpty()) {
            return "";
        }

        // Double quoted value: KEY="value"
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            String inner = raw.substring(1, raw.length() - 1);
            return unescapeDoubleQuotes(inner);
        }

        // Single quoted value: KEY='value' (all characters preserved literally)
        if (raw.startsWith("'") && raw.endsWith("'") && raw.length() >= 2) {
            return raw.substring(1, raw.length() - 1);
        }

        // Unquoted value: strip trailing inline comments if any
        int commentIdx = raw.indexOf(" #");
        if (commentIdx != -1) {
            raw = raw.substring(0, commentIdx).trim();
        }

        return raw;
    }

    private static String unescapeDoubleQuotes(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        boolean escaped = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '\\' -> sb.append('\\');
                    case '"' -> sb.append('"');
                    case '$' -> sb.append('$');
                    default -> sb.append('\\').append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        if (escaped) {
            sb.append('\\');
        }
        return sb.toString();
    }
}
