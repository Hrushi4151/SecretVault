package com.secretvault.cli.env;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DotEnvParserTest {

    @Test
    @DisplayName("Parses basic KEY=value pairs")
    void parsesBasicKeyValue() {
        String content = """
                DATABASE_URL=postgres://localhost:5432/db
                PORT=8080
                DEBUG=true
                """;
        Map<String, String> result = DotEnvParser.parse(content);
        assertThat(result)
                .containsEntry("DATABASE_URL", "postgres://localhost:5432/db")
                .containsEntry("PORT", "8080")
                .containsEntry("DEBUG", "true");
    }

    @Test
    @DisplayName("Parses double quoted values with escape sequences")
    void parsesDoubleQuotedWithEscapes() {
        String content = """
                MULTILINE="line1\\nline2\\nline3"
                TABBED="col1\\tcol2"
                QUOTED="He said \\"hello\\""
                SLASHES="path\\\\to\\\\file"
                DOLLAR="cost is \\$100"
                """;
        Map<String, String> result = DotEnvParser.parse(content);
        assertThat(result.get("MULTILINE")).isEqualTo("line1\nline2\nline3");
        assertThat(result.get("TABBED")).isEqualTo("col1\tcol2");
        assertThat(result.get("QUOTED")).isEqualTo("He said \"hello\"");
        assertThat(result.get("SLASHES")).isEqualTo("path\\to\\file");
        assertThat(result.get("DOLLAR")).isEqualTo("cost is $100");
    }

    @Test
    @DisplayName("Parses single quoted literal values")
    void parsesSingleQuotedLiteral() {
        String content = """
                LITERAL='line1\\nline2'
                SPECIAL='contains # not a comment and "double quotes"'
                """;
        Map<String, String> result = DotEnvParser.parse(content);
        assertThat(result.get("LITERAL")).isEqualTo("line1\\nline2");
        assertThat(result.get("SPECIAL")).isEqualTo("contains # not a comment and \"double quotes\"");
    }

    @Test
    @DisplayName("Parses multiline quoted values")
    void parsesMultilineQuotedValues() {
        String content = """
                CERT="-----BEGIN CERTIFICATE-----
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA
                -----END CERTIFICATE-----"
                SIMPLE=123
                """;
        Map<String, String> result = DotEnvParser.parse(content);
        assertThat(result.get("CERT")).isEqualTo("-----BEGIN CERTIFICATE-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA\n-----END CERTIFICATE-----");
        assertThat(result.get("SIMPLE")).isEqualTo("123");
    }

    @Test
    @DisplayName("Ignores comments and empty lines")
    void ignoresCommentsAndEmptyLines() {
        String content = """
                # Top level comment
                  # Indented comment
                
                ACTIVE_KEY=123
                # Disabled key
                # DISABLED=456
                
                ANOTHER_KEY=789 # inline comment
                """;
        Map<String, String> result = DotEnvParser.parse(content);
        assertThat(result).hasSize(2)
                .containsEntry("ACTIVE_KEY", "123")
                .containsEntry("ANOTHER_KEY", "789")
                .doesNotContainKey("DISABLED");
    }

    @Test
    @DisplayName("Supports export prefix")
    void supportsExportPrefix() {
        String content = """
                export API_KEY=secret_123
                export\tNODE_ENV=production
                """;
        Map<String, String> result = DotEnvParser.parse(content);
        assertThat(result)
                .containsEntry("API_KEY", "secret_123")
                .containsEntry("NODE_ENV", "production");
    }

    @Test
    @DisplayName("Parses empty values")
    void parsesEmptyValues() {
        String content = """
                EMPTY_KEY=
                EMPTY_QUOTED=""
                EMPTY_SINGLE=''
                """;
        Map<String, String> result = DotEnvParser.parse(content);
        assertThat(result)
                .containsEntry("EMPTY_KEY", "")
                .containsEntry("EMPTY_QUOTED", "")
                .containsEntry("EMPTY_SINGLE", "");
    }

    @Test
    @DisplayName("Duplicate keys resolve deterministically (last key wins)")
    void duplicateKeysDeterministic() {
        String content = """
                PORT=3000
                PORT=8080
                PORT=9000
                """;
        Map<String, String> result = DotEnvParser.parse(content);
        assertThat(result.get("PORT")).isEqualTo("9000");
    }

    @Test
    @DisplayName("Shell syntax is treated as pure literal data with zero command execution")
    void shellSyntaxTreatedAsLiteral() {
        String content = """
                CMD_SUB=$(whoami)
                BACKTICKS=`cat /etc/passwd`
                VAR_SUB=${HOME}
                DANGEROUS=$(rm -rf /)
                """;
        Map<String, String> result = DotEnvParser.parse(content);
        assertThat(result.get("CMD_SUB")).isEqualTo("$(whoami)");
        assertThat(result.get("BACKTICKS")).isEqualTo("`cat /etc/passwd`");
        assertThat(result.get("VAR_SUB")).isEqualTo("${HOME}");
        assertThat(result.get("DANGEROUS")).isEqualTo("$(rm -rf /)");
    }

    @Test
    @DisplayName("Rejects files exceeding 5MB size limit")
    void rejectsFilesExceeding5MB(@TempDir Path tempDir) throws IOException {
        Path largeFile = tempDir.resolve("large.env");
        // Create 5.1MB file
        byte[] data = new byte[5 * 1024 * 1024 + 1024];
        Files.write(largeFile, data);

        assertThatThrownBy(() -> DotEnvParser.parse(largeFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds 5MB limit");

        ByteArrayInputStream in = new ByteArrayInputStream(data);
        assertThatThrownBy(() -> DotEnvParser.parse(in))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds 5MB limit");
    }

    @Test
    @DisplayName("Formats secrets as valid .env output")
    void formatsEnvOutput() {
        Map<String, String> secrets = Map.of(
                "SIMPLE", "value",
                "SPACED", "hello world",
                "NEWLINE", "line1\nline2"
        );
        String formatted = DotEnvParser.formatEnv(secrets);
        assertThat(formatted).contains("SIMPLE=value");
        assertThat(formatted).contains("SPACED=\"hello world\"");
        assertThat(formatted).contains("NEWLINE=\"line1\\nline2\"");
    }

    @Test
    @DisplayName("Formats secrets as safe shell export commands")
    void formatsShellOutput() {
        Map<String, String> secrets = Map.of(
                "FOO", "bar",
                "WITH_QUOTE", "it's working"
        );
        String formatted = DotEnvParser.formatShell(secrets);
        assertThat(formatted).contains("export FOO='bar'");
        assertThat(formatted).contains("export WITH_QUOTE='it'\\''s working'");
    }
}
