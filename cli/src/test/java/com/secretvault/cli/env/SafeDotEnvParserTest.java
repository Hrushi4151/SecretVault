package com.secretvault.cli.env;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeDotEnvParserTest {

    @Test
    @DisplayName("Parses standard key-value pairs with comments, quotes, and empty values")
    void parsesStandardEnvFormats() {
        String envContent = """
                # Database Configuration
                DB_HOST=localhost
                DB_PORT=5432
                DB_USER="vault_user"
                DB_PASS='super_secret_p@ssword'
                EMPTY_VAL=
                APP_TITLE="SecretVault Dev & Prod"
                export JWT_SECRET=my_jwt_secret # inline comment
                """;

        Map<String, String> parsed = SafeDotEnvParser.parse(envContent);

        assertThat(parsed).containsEntry("DB_HOST", "localhost");
        assertThat(parsed).containsEntry("DB_PORT", "5432");
        assertThat(parsed).containsEntry("DB_USER", "vault_user");
        assertThat(parsed).containsEntry("DB_PASS", "super_secret_p@ssword");
        assertThat(parsed).containsEntry("EMPTY_VAL", "");
        assertThat(parsed).containsEntry("APP_TITLE", "SecretVault Dev & Prod");
        assertThat(parsed).containsEntry("JWT_SECRET", "my_jwt_secret");
    }

    @Test
    @DisplayName("STRICT SECURITY: Does NOT execute subshells or backticks, treats them as literal strings")
    void treatsSubshellsAsLiteralStrings() {
        String maliciousContent = """
                MALICIOUS_1=$(rm -rf /tmp/test)
                MALICIOUS_2=`whoami`
                MALICIOUS_3="hello $(echo injected)"
                MALICIOUS_4=${DANGEROUS_VAR}
                """;

        Map<String, String> parsed = SafeDotEnvParser.parse(maliciousContent);

        assertThat(parsed.get("MALICIOUS_1")).isEqualTo("$(rm -rf /tmp/test)");
        assertThat(parsed.get("MALICIOUS_2")).isEqualTo("`whoami`");
        assertThat(parsed.get("MALICIOUS_3")).isEqualTo("hello $(echo injected)");
        assertThat(parsed.get("MALICIOUS_4")).isEqualTo("${DANGEROUS_VAR}");
    }

    @Test
    @DisplayName("Handles escaped newlines and quotes correctly")
    void handlesEscapedCharacters() {
        String content = """
                MULTILINE="Line 1\\nLine 2\\nLine 3"
                QUOTED="He said \\"Hello\\""
                """;

        Map<String, String> parsed = SafeDotEnvParser.parse(content);

        assertThat(parsed.get("MULTILINE")).isEqualTo("Line 1\nLine 2\nLine 3");
        assertThat(parsed.get("QUOTED")).isEqualTo("He said \"Hello\"");
    }

    @Test
    @DisplayName("Rejects invalid key names with illegal characters")
    void rejectsInvalidKeyNames() {
        String invalidContent = """
                VALID_KEY=123
                INVALID KEY NAME=456
                """;

        assertThatThrownBy(() -> SafeDotEnvParser.parse(invalidContent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid key name");
    }
}
