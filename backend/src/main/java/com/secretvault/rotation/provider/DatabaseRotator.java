package com.secretvault.rotation.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.exception.ApiException;
import com.secretvault.rotation.engine.SecretGenerationEngine;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.SecretType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Production-grade Database Credential Rotator supporting zero-downtime dual-user rotation,
 * live connection verification, SQL identifier sanitization, and graceful credential decommissioning
 * for PostgreSQL and MySQL.
 */
@Component
@Order(10)
public class DatabaseRotator implements SecretRotator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseRotator.class);

    // Strict identifier allowlist: alphanumeric and underscore, starting with a letter, length 1-63
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,62}$");

    private final SecretGenerationEngine generationEngine;
    private final ObjectMapper objectMapper;

    public DatabaseRotator(SecretGenerationEngine generationEngine, ObjectMapper objectMapper) {
        this.generationEngine = generationEngine;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(SecretType type, RotationPolicy policy) {
        return type == SecretType.DATABASE_CREDENTIAL;
    }

    @Override
    public String generate(RotationPolicy policy, RotationJob job) {
        DbConfig config = parseConfig(policy);
        if (config != null) {
            if (StringUtils.hasText(config.baseUsername)) {
                validateIdentifier(config.baseUsername, "Base Username");
            }
            if (StringUtils.hasText(config.username)) {
                validateIdentifier(config.username, "Username");
            }
            if (StringUtils.hasText(config.databaseName)) {
                validateIdentifier(config.databaseName, "Database Name");
            }
            if (StringUtils.hasText(config.schemaName)) {
                validateIdentifier(config.schemaName, "Schema Name");
            }
            if (config.roles != null) {
                for (String role : config.roles) {
                    if (StringUtils.hasText(role)) validateIdentifier(role, "Role Name");
                }
            }
        }

        String configJson = policy != null ? policy.getSecretGeneratorConfig() : null;
        log.info("Generating secure database password using SecretGenerationEngine for job {}", job != null ? job.getId() : "N/A");

        String generatedPassword = generationEngine.generateSecret(SecretType.DATABASE_CREDENTIAL, configJson);
        if (!StringUtils.hasText(generatedPassword)) {
            generatedPassword = generationEngine.generatePassword(32, true, true);
        }

        if (config != null && (StringUtils.hasText(config.baseUsername) || StringUtils.hasText(config.username))) {
            String targetUser = resolveTargetUser(config, job);
            try {
                com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();
                node.put("username", targetUser);
                node.put("password", generatedPassword);
                if (StringUtils.hasText(config.databaseName)) node.put("database", config.databaseName);
                if (StringUtils.hasText(config.databaseType)) node.put("engine", config.databaseType.toUpperCase());
                if (StringUtils.hasText(config.jdbcUrl)) node.put("jdbcUrl", config.jdbcUrl);
                return objectMapper.writeValueAsString(node);
            } catch (Exception e) {
                log.warn("Failed to serialize database credential JSON, returning raw password: {}", e.getMessage());
            }
        }

        return generatedPassword;
    }

    @Override
    public boolean validate(String newPlaintext, RotationPolicy policy, RotationJob job) {
        if (newPlaintext == null || newPlaintext.length() < 8) {
            log.warn("Database credential validation failed: insufficient length");
            return false;
        }

        String actualPassword = extractPassword(newPlaintext);
        if (!StringUtils.hasText(actualPassword) || actualPassword.length() < 8) {
            log.warn("Database credential validation failed: invalid extracted password");
            return false;
        }

        DbConfig config = parseConfig(policy);
        if (config == null || !StringUtils.hasText(config.jdbcUrl)) {
            // No direct DB connection details; pass format validation
            return true;
        }

        // Test connectivity using admin connection if provided, or candidate user if already staged
        String testUser = StringUtils.hasText(config.adminUsername) ? config.adminUsername : resolveTargetUser(config, job);
        String testPass = StringUtils.hasText(config.adminPassword) ? config.adminPassword : actualPassword;

        if (StringUtils.hasText(testUser) && StringUtils.hasText(testPass)) {
            try {
                validateIdentifier(testUser, "Test Username");
                log.info("Validating database connectivity against '{}' with user '{}'", config.jdbcUrl, testUser);
                try (Connection conn = DriverManager.getConnection(config.jdbcUrl, testUser, testPass)) {
                    return conn.isValid(5);
                }
            } catch (Exception e) {
                log.warn("Direct JDBC validation check error for DB credential rotation: {}", e.getMessage());
                return false;
            }
        }

        return true;
    }

    @Override
    public void stage(String newPlaintext, RotationPolicy policy, RotationJob job) {
        if (newPlaintext != null && newPlaintext.trim().startsWith("{")) {
            try {
                objectMapper.readTree(newPlaintext);
            } catch (Exception e) {
                throw ApiException.badRequest("Failed to parse database credential JSON: " + e.getMessage());
            }
        }

        DbConfig config = parseConfig(policy);
        if (config == null || !StringUtils.hasText(config.jdbcUrl) || !StringUtils.hasText(config.adminUsername)) {
            log.info("No admin JDBC config specified; skipping automated dual-user DDL staging for job {}", job != null ? job.getId() : "N/A");
            return;
        }

        String targetUser = resolveTargetUser(config, job);
        validateIdentifier(targetUser, "Target Staged Username");
        validateIdentifier(config.adminUsername, "Admin Username");
        if (StringUtils.hasText(config.databaseName)) {
            validateIdentifier(config.databaseName, "Database Name");
        }
        if (StringUtils.hasText(config.schemaName)) {
            validateIdentifier(config.schemaName, "Schema Name");
        }

        String passwordToStage = extractPassword(newPlaintext);

        log.info("Staging database candidate user '{}' on '{}' for rotation job {}", targetUser, config.jdbcUrl, job.getId());

        try (Connection adminConn = DriverManager.getConnection(config.jdbcUrl, config.adminUsername, config.adminPassword)) {
            adminConn.setAutoCommit(true);

            if (config.isPostgreSql()) {
                stagePostgreSqlUser(adminConn, targetUser, passwordToStage, config);
            } else if (config.isMySql()) {
                stageMySqlUser(adminConn, targetUser, passwordToStage, config);
            } else {
                log.warn("Unsupported database engine for automated DDL staging: {}", config.jdbcUrl);
                return;
            }

            // Immediately verify the newly staged candidate user credentials
            try (Connection candidateConn = DriverManager.getConnection(config.jdbcUrl, targetUser, passwordToStage)) {
                if (!candidateConn.isValid(5)) {
                    throw ApiException.internal("DB_STAGING_VALIDATION_FAILED", "Candidate database user failed connection test after creation");
                }
                log.info("Successfully verified connectivity for staged database user '{}'", targetUser);
            }

        } catch (SQLException e) {
            log.error("Failed to stage database user '{}' for rotation job {}: {}", targetUser, job.getId(), e.getMessage(), e);
            throw ApiException.internal("DB_STAGING_FAILED", "Database DDL execution failed during user staging: " + e.getMessage());
        }
    }

    @Override
    public void activate(String newPlaintext, RotationPolicy policy, RotationJob job) {
        DbConfig config = parseConfig(policy);
        if (config == null || !StringUtils.hasText(config.jdbcUrl)) {
            log.info("Activating new database credential for rotation job {}", job.getId());
            return;
        }

        String targetUser = resolveTargetUser(config, job);
        log.info("Activating database user '{}' as active primary credential for rotation job {}", targetUser, job.getId());
    }

    @Override
    public void revokePrevious(String oldPlaintext, RotationPolicy policy, RotationJob job) {
        DbConfig config = parseConfig(policy);
        if (config == null || !StringUtils.hasText(config.jdbcUrl) || !StringUtils.hasText(config.adminUsername)) {
            log.info("No admin JDBC config specified; skipping automated old-user decommissioning for job {}", job.getId());
            return;
        }

        String oldUser = resolvePreviousUser(config, job);
        if (!StringUtils.hasText(oldUser)) {
            log.info("No previous alternate user resolved to decommission for job {}", job.getId());
            return;
        }

        validateIdentifier(oldUser, "Old User to Revoke");
        validateIdentifier(config.adminUsername, "Admin Username");

        log.info("Grace period elapsed: revoking and decommissioning previous database user '{}' for rotation job {}", oldUser, job.getId());

        try (Connection adminConn = DriverManager.getConnection(config.jdbcUrl, config.adminUsername, config.adminPassword)) {
            adminConn.setAutoCommit(true);

            if (config.isPostgreSql()) {
                revokePostgreSqlUser(adminConn, oldUser, config);
            } else if (config.isMySql()) {
                revokeMySqlUser(adminConn, oldUser, config);
            }
            log.info("Successfully decommissioned previous database user '{}'", oldUser);
        } catch (SQLException e) {
            log.error("Failed to decommission previous database user '{}' for job {}: {}", oldUser, job.getId(), e.getMessage(), e);
            // Log as warning rather than breaking completion if old user is already dropped
        }
    }

    @Override
    public void rollback(String newPlaintext, String oldPlaintext, RotationPolicy policy, RotationJob job) {
        DbConfig config = parseConfig(policy);
        if (config == null || !StringUtils.hasText(config.jdbcUrl) || !StringUtils.hasText(config.adminUsername)) {
            log.warn("Rolling back database credential rotation for job {}", job.getId());
            return;
        }

        String candidateUser = resolveTargetUser(config, job);
        if (!StringUtils.hasText(candidateUser)) return;

        log.warn("Executing database rotation rollback: removing staged candidate user '{}' for job {}", candidateUser, job.getId());
        try (Connection adminConn = DriverManager.getConnection(config.jdbcUrl, config.adminUsername, config.adminPassword)) {
            adminConn.setAutoCommit(true);
            if (config.isPostgreSql()) {
                revokePostgreSqlUser(adminConn, candidateUser, config);
            } else if (config.isMySql()) {
                revokeMySqlUser(adminConn, candidateUser, config);
            }
        } catch (Exception e) {
            log.warn("Failed to drop candidate user '{}' during rollback: {}", candidateUser, e.getMessage());
        }
    }

    // ==========================================
    // POSTGRESQL IMPLEMENTATION
    // ==========================================

    private void stagePostgreSqlUser(Connection conn, String targetUser, String password, DbConfig config) throws SQLException {
        String quotedUser = quotePgIdentifier(targetUser);
        String quotedPass = quotePgLiteral(password);

        // 1. Create or update user password
        boolean userExists = false;
        try (PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname = ?")) {
            checkStmt.setString(1, targetUser);
            try (ResultSet rs = checkStmt.executeQuery()) {
                userExists = rs.next();
            }
        }

        try (Statement stmt = conn.createStatement()) {
            if (!userExists) {
                stmt.execute("CREATE USER " + quotedUser + " WITH PASSWORD " + quotedPass + " LOGIN NOSUPERUSER");
            } else {
                stmt.execute("ALTER USER " + quotedUser + " WITH PASSWORD " + quotedPass + " LOGIN NOSUPERUSER");
            }

            // 2. Grant roles if specified
            if (config.roles != null && config.roles.length > 0) {
                for (String role : config.roles) {
                    if (StringUtils.hasText(role)) {
                        validateIdentifier(role, "Role Name");
                        stmt.execute("GRANT " + quotePgIdentifier(role) + " TO " + quotedUser);
                    }
                }
            }

            // 3. Grant schema permissions if specified
            String schema = StringUtils.hasText(config.schemaName) ? config.schemaName : "public";
            validateIdentifier(schema, "Schema Name");
            stmt.execute("GRANT USAGE ON SCHEMA " + quotePgIdentifier(schema) + " TO " + quotedUser);
            stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA " + quotePgIdentifier(schema) + " TO " + quotedUser);
        }
    }

    private void revokePostgreSqlUser(Connection conn, String oldUser, DbConfig config) throws SQLException {
        String quotedUser = quotePgIdentifier(oldUser);
        String schema = StringUtils.hasText(config.schemaName) ? config.schemaName : "public";
        validateIdentifier(schema, "Schema Name");

        try (Statement stmt = conn.createStatement()) {
            try {
                stmt.execute("REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA " + quotePgIdentifier(schema) + " FROM " + quotedUser);
                stmt.execute("REVOKE USAGE ON SCHEMA " + quotePgIdentifier(schema) + " FROM " + quotedUser);
            } catch (SQLException ignored) {}

            try {
                stmt.execute("DROP USER IF EXISTS " + quotedUser);
            } catch (SQLException e) {
                // If objects still owned, alter to NOLOGIN as safe fallback
                stmt.execute("ALTER USER " + quotedUser + " NOLOGIN");
            }
        }
    }

    // ==========================================
    // MYSQL IMPLEMENTATION
    // ==========================================

    private void stageMySqlUser(Connection conn, String targetUser, String password, DbConfig config) throws SQLException {
        String quotedUser = quoteMySqlIdentifier(targetUser);
        String quotedPass = quoteMySqlLiteral(password);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE USER IF NOT EXISTS " + quotedUser + "@'%' IDENTIFIED BY " + quotedPass);
            stmt.execute("ALTER USER " + quotedUser + "@'%' IDENTIFIED BY " + quotedPass);

            String db = StringUtils.hasText(config.databaseName) ? quoteMySqlIdentifier(config.databaseName) : "*";
            stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + db + ".* TO " + quotedUser + "@'%'");
            stmt.execute("FLUSH PRIVILEGES");
        }
    }

    private void revokeMySqlUser(Connection conn, String oldUser, DbConfig config) throws SQLException {
        String quotedUser = quoteMySqlIdentifier(oldUser);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP USER IF EXISTS " + quotedUser + "@'%'");
            stmt.execute("FLUSH PRIVILEGES");
        }
    }

    // ==========================================
    // HELPER & SECURITY UTILITIES
    // ==========================================

    public static void validateIdentifier(String identifier, String fieldName) {
        if (!StringUtils.hasText(identifier) || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw ApiException.badRequest("Invalid database identifier for " + fieldName + ": '" + identifier + "'. Must be 1-63 alphanumeric characters starting with a letter.");
        }
    }

    public static String quotePgIdentifier(String id) {
        validateIdentifier(id, "PostgreSQL Identifier");
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }

    public static String quotePgLiteral(String val) {
        if (val == null) return "NULL";
        return "'" + val.replace("'", "''") + "'";
    }

    public static String quoteMySqlIdentifier(String id) {
        validateIdentifier(id, "MySQL Identifier");
        return "`" + id.replace("`", "``") + "`";
    }

    public static String quoteMySqlLiteral(String val) {
        if (val == null) return "NULL";
        return "'" + val.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    private String resolveTargetUser(DbConfig config, RotationJob job) {
        if (StringUtils.hasText(config.baseUsername)) {
            int targetVer = job != null && job.getTargetVersionNumber() != null ? job.getTargetVersionNumber() : 1;
            String suffix = (targetVer % 2 == 1) ? "_a" : "_b";
            return config.baseUsername + suffix;
        }
        return StringUtils.hasText(config.username) ? config.username : "db_user";
    }

    private String resolvePreviousUser(DbConfig config, RotationJob job) {
        if (StringUtils.hasText(config.baseUsername)) {
            int prevVer = job != null && job.getPreviousVersionNumber() != null ? job.getPreviousVersionNumber() : 0;
            if (prevVer <= 0) return null;
            String suffix = (prevVer % 2 == 1) ? "_a" : "_b";
            return config.baseUsername + suffix;
        }
        return null; // Single-user in-place password reset has no previous user to drop
    }

    private String extractPassword(String plaintext) {
        if (plaintext == null) return null;
        if (plaintext.trim().startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(plaintext);
                if (node.has("password")) {
                    return node.get("password").asText();
                }
            } catch (Exception ignored) {}
        }
        return plaintext;
    }

    private DbConfig parseConfig(RotationPolicy policy) {
        if (policy == null || !StringUtils.hasText(policy.getSecretGeneratorConfig())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(policy.getSecretGeneratorConfig());
            DbConfig cfg = new DbConfig();
            if (root.has("jdbcUrl")) {
                cfg.jdbcUrl = root.get("jdbcUrl").asText();
            } else if (root.has("host") && root.has("port")) {
                String engine = root.has("engine") ? root.get("engine").asText() : (root.has("databaseType") ? root.get("databaseType").asText() : "postgresql");
                String db = root.has("database") ? root.get("database").asText() : (root.has("databaseName") ? root.get("databaseName").asText() : "");
                String prefix = engine.equalsIgnoreCase("mysql") ? "jdbc:mysql://" : "jdbc:postgresql://";
                cfg.jdbcUrl = prefix + root.get("host").asText() + ":" + root.get("port").asInt() + "/" + db;
            }
            if (root.has("adminUsername")) cfg.adminUsername = root.get("adminUsername").asText();
            if (root.has("adminPassword")) cfg.adminPassword = root.get("adminPassword").asText();
            if (root.has("username")) cfg.username = root.get("username").asText();
            if (root.has("baseUsername")) cfg.baseUsername = root.get("baseUsername").asText();
            if (root.has("databaseName")) cfg.databaseName = root.get("databaseName").asText();
            else if (root.has("database")) cfg.databaseName = root.get("database").asText();
            if (root.has("schemaName")) cfg.schemaName = root.get("schemaName").asText();
            else if (root.has("schema")) cfg.schemaName = root.get("schema").asText();
            if (root.has("databaseType")) cfg.databaseType = root.get("databaseType").asText();
            else if (root.has("engine")) cfg.databaseType = root.get("engine").asText();

            if (root.has("roles") && root.get("roles").isArray()) {
                JsonNode rolesNode = root.get("roles");
                cfg.roles = new String[rolesNode.size()];
                for (int i = 0; i < rolesNode.size(); i++) {
                    cfg.roles[i] = rolesNode.get(i).asText();
                }
            }
            return cfg;
        } catch (Exception e) {
            log.warn("Failed to parse DB configuration from policy: {}", e.getMessage());
            return null;
        }
    }

    public static class DbConfig {
        public String jdbcUrl;
        public String adminUsername;
        public String adminPassword;
        public String username;
        public String baseUsername;
        public String databaseName;
        public String schemaName;
        public String databaseType;
        public String[] roles;

        public boolean isPostgreSql() {
            if (StringUtils.hasText(databaseType) && "POSTGRESQL".equalsIgnoreCase(databaseType)) return true;
            return jdbcUrl != null && jdbcUrl.toLowerCase().contains(":postgresql:");
        }

        public boolean isMySql() {
            if (StringUtils.hasText(databaseType) && "MYSQL".equalsIgnoreCase(databaseType)) return true;
            return jdbcUrl != null && (jdbcUrl.toLowerCase().contains(":mysql:") || jdbcUrl.toLowerCase().contains(":mariadb:"));
        }
    }
}
