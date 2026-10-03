package com.secretvault.cli.env;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pure data tokenizer and parser for .env files with zero shell command execution.
 * Enforces size limits, handles quoted/escaped values, comments, multiline strings,
 * and deterministic key deduplication.
 */
public final class DotEnvParser {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB limit
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]+$");

    private DotEnvParser() {}

    /**
     * Parses a .env file from the given Path with 5MB size limit check.
     */
    public static Map<String, String> parse(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        Path normalized = path.normalize();
        if (!Files.exists(normalized)) {
            throw new IllegalArgumentException("File does not exist: " + normalized);
        }
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Not a regular file: " + normalized);
        }
        long size = Files.size(normalized);
        if (size > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 5MB limit: " + size + " bytes");
        }
        String content = Files.readString(normalized, StandardCharsets.UTF_8);
        return parse(content);
    }

    /**
     * Parses a .env file from an InputStream.
     */
    public static Map<String, String> parse(InputStream in) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        byte[] bytes = in.readNBytes((int) MAX_FILE_SIZE + 1);
        if (bytes.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Stream size exceeds 5MB limit");
        }
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Parses .env text content into a map of key-value pairs.
     * Guaranteed pure data parsing: treats strings like $(whoami) or `whoami` as literal data.
     */
    public static Map<String, String> parse(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        if (content == null || content.isBlank()) {
            return result;
        }

        String[] lines = content.split("\\r?\\n");
        int i = 0;
        while (i < lines.length) {
            String rawLine = lines[i];
            String trimmedLine = rawLine.trim();

            // Skip empty lines and comment lines
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                i++;
                continue;
            }

            // Strip optional 'export ' prefix
            if (trimmedLine.startsWith("export ") || trimmedLine.startsWith("export\t")) {
                trimmedLine = trimmedLine.substring(6).trim();
            }

            int eqIndex = trimmedLine.indexOf('=');
            if (eqIndex <= 0) {
                // Line without '=' is ignored as invalid format
                i++;
                continue;
            }

            String rawKey = trimmedLine.substring(0, eqIndex).trim();
            if (!KEY_PATTERN.matcher(rawKey).matches()) {
                i++;
                continue;
            }

            String rawVal = trimmedLine.substring(eqIndex + 1).stripLeading();

            if (rawVal.startsWith("\"")) {
                // Double quoted string (supports escapes and multiline)
                StringBuilder valBuilder = new StringBuilder();
                String current = rawVal.substring(1);

                while (true) {
                    int quotePos = findUnescapedQuote(current, '"');
                    if (quotePos != -1) {
                        valBuilder.append(current, 0, quotePos);
                        break;
                    } else {
                        valBuilder.append(current).append("\n");
                        i++;
                        if (i >= lines.length) {
                            break;
                        }
                        current = lines[i];
                    }
                }

                String unescaped = unescapeDoubleQuoted(valBuilder.toString());
                result.put(rawKey, unescaped);
            } else if (rawVal.startsWith("'")) {
                // Single quoted string (literal value, supports multiline)
                StringBuilder valBuilder = new StringBuilder();
                String current = rawVal.substring(1);

                while (true) {
                    int quotePos = findUnescapedQuote(current, '\'');
                    if (quotePos != -1) {
                        valBuilder.append(current, 0, quotePos);
                        break;
                    } else {
                        valBuilder.append(current).append("\n");
                        i++;
                        if (i >= lines.length) {
                            break;
                        }
                        current = lines[i];
                    }
                }

                String unescaped = unescapeSingleQuoted(valBuilder.toString());
                result.put(rawKey, unescaped);
            } else {
                // Unquoted value: strip inline comments if any
                String val = stripInlineComment(rawVal);
                result.put(rawKey, val);
            }

            i++;
        }

        return result;
    }

    /**
     * Formats secrets into standard .env format (KEY=value or KEY="escaped_value").
     */
    public static String formatEnv(Map<String, String> secrets) {
        if (secrets == null || secrets.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : secrets.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue() : "";
            sb.append(key).append("=");
            if (needsQuotes(value)) {
                sb.append("\"").append(escapeDoubleQuoted(value)).append("\"");
            } else {
                sb.append(value);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Formats secrets into safe shell export commands (export KEY='escaped_value').
     */
    public static String formatShell(Map<String, String> secrets) {
        if (secrets == null || secrets.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : secrets.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue() : "";
            String escaped = value.replace("'", "'\\''");
            sb.append("export ").append(key).append("='").append(escaped).append("'\n");
        }
        return sb.toString();
    }

    private static int findUnescapedQuote(String s, char quoteChar) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == quoteChar) {
                int backslashes = 0;
                for (int j = i - 1; j >= 0 && s.charAt(j) == '\\'; j--) {
                    backslashes++;
                }
                if (backslashes % 2 == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String stripInlineComment(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '#' && (i == 0 || Character.isWhitespace(value.charAt(i - 1)))) {
                break;
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }

    private static String unescapeDoubleQuoted(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n': sb.append('\n'); i++; break;
                    case 'r': sb.append('\r'); i++; break;
                    case 't': sb.append('\t'); i++; break;
                    case '"': sb.append('"'); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case '$': sb.append('$'); i++; break;
                    default: sb.append(c); break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String unescapeSingleQuoted(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == '\'' || next == '\\') {
                    sb.append(next);
                    i++;
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean needsQuotes(String val) {
        if (val.isEmpty()) return false;
        if (val.startsWith(" ") || val.endsWith(" ")) return true;
        if (val.contains("\n") || val.contains("\r") || val.contains("\t")) return true;
        if (val.contains("\"") || val.contains("#") || val.contains("=") || val.contains(" ") || val.contains("$")) return true;
        return false;
    }

    private static String escapeDoubleQuoted(String val) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < val.length(); i++) {
            char c = val.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '$': sb.append("\\$"); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }
}
