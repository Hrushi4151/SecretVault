package com.secretvault.events.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable standard record implementation of DomainEvent.
 * Automatically validates and redacts sensitive metadata fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BaseDomainEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("eventType") EventType eventType,
        @JsonProperty("eventVersion") int eventVersion,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("recordedAt") Instant recordedAt,
        @JsonProperty("workspaceId") UUID workspaceId,
        @JsonProperty("projectId") UUID projectId,
        @JsonProperty("environmentId") UUID environmentId,
        @JsonProperty("secretId") UUID secretId,
        @JsonProperty("actorType") String actorType,
        @JsonProperty("actorId") String actorId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("causationId") String causationId,
        @JsonProperty("requestId") String requestId,
        @JsonProperty("source") String source,
        @JsonProperty("severity") EventSeverity severity,
        @JsonProperty("aggregateType") String aggregateType,
        @JsonProperty("aggregateId") String aggregateId,
        @JsonProperty("metadata") Map<String, Object> metadata
) implements DomainEvent {

    public BaseDomainEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (occurredAt == null) occurredAt = Instant.now();
        if (recordedAt == null) recordedAt = Instant.now();
        if (eventVersion <= 0) eventVersion = 1;
        if (severity == null) severity = EventSeverity.INFO;
        if (metadata != null) {
            metadata = Collections.unmodifiableMap(sanitizeMetadata(eventType, metadata));
        } else {
            metadata = Collections.emptyMap();
        }
    }

    @Override
    public UUID getEventId() { return eventId; }
    @Override
    public EventType getEventType() { return eventType; }
    @Override
    public int getEventVersion() { return eventVersion; }
    @Override
    public Instant getOccurredAt() { return occurredAt; }
    @Override
    public Instant getRecordedAt() { return recordedAt; }
    @Override
    public UUID getWorkspaceId() { return workspaceId; }
    @Override
    public UUID getProjectId() { return projectId; }
    @Override
    public UUID getEnvironmentId() { return environmentId; }
    @Override
    public UUID getSecretId() { return secretId; }
    @Override
    public String getActorType() { return actorType; }
    @Override
    public String getActorId() { return actorId; }
    @Override
    public String getCorrelationId() { return correlationId; }
    @Override
    public String getCausationId() { return causationId; }
    @Override
    public String getRequestId() { return requestId; }
    @Override
    public String getSource() { return source; }
    @Override
    public EventSeverity getSeverity() { return severity; }
    @Override
    public String getAggregateType() { return aggregateType; }
    @Override
    public String getAggregateId() { return aggregateId; }
    @Override
    public Map<String, Object> getMetadata() { return metadata; }

    private static final Set<String> KNOWN_LEGACY_REDACT_KEYS = Set.of(
            "secret", "secretname", "secret_name", "apikey", "api_key", "password", "private_key", "authorization",
            "dek", "secretvalue", "user_password", "api_token", "credentials", "rawsecret", "plaintext"
    );

    private static final Set<String> COMMON_SAFE_KEYS = Set.of(
            "correlationid", "causationid", "requestid", "source", "version", "status",
            "errorcode", "errormessage", "durationms", "retrycount", "batchsize",
            "component", "safe_metric", "safe_resource_type", "tags", "reason", "action",
            "safefield", "key"
    );

    private static final Set<String> SECRET_ALLOWLIST = Set.of(
            "secretkey", "secretname", "secrettype", "versionnumber", "fingerprint", "sourceenvironment",
            "targetenvironment", "branchname", "sourcebranch", "targetbranch", "reason",
            "action", "revocationreason", "compromisedby", "compromisedetails", "remediationnote",
            "changesummary", "tags", "path", "scope", "totalsecrets", "rolledbacktoversion",
            "promotedtoenvironment", "maskedpreview", "status", "actor", "version"
    );

    private static final Set<String> ROTATION_ALLOWLIST = Set.of(
            "policyid", "providertype", "intervaldays", "intervalseconds", "autorotate", "enabled", "status", "stage",
            "validationstatus", "validationdetails", "failurereason", "executedby",
            "rotationjobid", "jobid", "durationms", "strategy", "secrettype", "secretname",
            "previousversionnumber", "targetversionnumber", "newversionnumber", "triggertype",
            "errormessage", "actor"
    );

    private static final Set<String> LEASE_ALLOWLIST = Set.of(
            "leaseid", "ttlseconds", "expiresat", "renewcount", "reason", "consumerid",
            "remainingseconds", "maxttl"
    );

    private static final Set<String> CONSUMER_ALLOWLIST = Set.of(
            "consumerid", "consumername", "consumertype", "lastseenat", "endpoint",
            "version", "heartbeatinterval"
    );

    private static final Set<String> MACHINE_ALLOWLIST = Set.of(
            "machineid", "machinename", "oidcproviderid", "issuer", "subject", "role",
            "status", "claims", "clientid", "audience"
    );

    private static final Set<String> ACCESS_ALLOWLIST = Set.of(
            "requestid", "targetresource", "targetworkspace", "role", "requesteddurationminutes",
            "approverid", "rejectionreason", "revocationreason", "reviewid", "reviewerid",
            "findingscount", "scopetype"
    );

    private static final Set<String> PROVIDER_ALLOWLIST = Set.of(
            "providerid", "providertype", "syncjobid", "resourcessynced", "driftcount",
            "errorsummary", "resourcetype", "resourcename", "direction", "syncedcount",
            "failedcount", "diffcount"
    );

    private static final Set<String> SECURITY_ALLOWLIST = Set.of(
            "findingid", "incidentid", "ruleid", "severity", "title", "description",
            "anomalyscore", "metricname", "affectedcount", "remediationstatus"
    );

    private static boolean isFieldAllowed(EventType eventType, String lowerKey) {
        if (COMMON_SAFE_KEYS.contains(lowerKey)) return true;
        if (eventType == null) return false;
        String name = eventType.name();
        if (name.startsWith("SECRET_")) return SECRET_ALLOWLIST.contains(lowerKey);
        if (name.startsWith("ROTATION_")) return ROTATION_ALLOWLIST.contains(lowerKey);
        if (name.startsWith("LEASE_")) return LEASE_ALLOWLIST.contains(lowerKey);
        if (name.startsWith("CONSUMER_")) return CONSUMER_ALLOWLIST.contains(lowerKey);
        if (name.startsWith("MACHINE_")) return MACHINE_ALLOWLIST.contains(lowerKey);
        if (name.startsWith("JIT_") || name.startsWith("ACCESS_")) return ACCESS_ALLOWLIST.contains(lowerKey);
        if (name.startsWith("PROVIDER_")) return PROVIDER_ALLOWLIST.contains(lowerKey);
        if (name.startsWith("SECURITY_") || name.startsWith("ANOMALY_")) return SECURITY_ALLOWLIST.contains(lowerKey);
        return false;
    }

    private static boolean isSecretBearingName(String lowerKey) {
        if (lowerKey.equals("key")) return false;
        return lowerKey.contains("secret") || lowerKey.contains("pass") || lowerKey.contains("token")
                || lowerKey.contains("cred") || lowerKey.contains("api_key") || lowerKey.contains("apikey")
                || lowerKey.contains("private_key") || lowerKey.contains("privatekey")
                || lowerKey.contains("signing_key") || lowerKey.contains("signingkey")
                || lowerKey.contains("auth") || lowerKey.contains("cert") || lowerKey.contains("priv")
                || lowerKey.contains("canary") || lowerKey.contains("bearer") || lowerKey.contains("dek")
                || lowerKey.contains("cookie") || lowerKey.contains("jwt") || lowerKey.contains("signature");
    }

    private static final java.util.regex.Pattern CANARY_PATTERN =
            java.util.regex.Pattern.compile("(?i)(sec[a-z]*[_-]?canary[a-zA-Z0-9_\\-]*)");
    private static final java.util.regex.Pattern GITHUB_TOKEN_PATTERN =
            java.util.regex.Pattern.compile("(?i)(gh[pours]_[a-zA-Z0-9]{20,})");
    private static final java.util.regex.Pattern AWS_KEY_PATTERN =
            java.util.regex.Pattern.compile("(?i)(A[SK]IA[0-9A-Z]{16})");
    private static final java.util.regex.Pattern SLACK_TOKEN_PATTERN =
            java.util.regex.Pattern.compile("(?i)(xox[baprs]-[a-zA-Z0-9\\-]{10,})");
    private static final java.util.regex.Pattern GITLAB_TOKEN_PATTERN =
            java.util.regex.Pattern.compile("(?i)(glpat-[a-zA-Z0-9\\-_]{20,})");
    private static final java.util.regex.Pattern STRIPE_KEY_PATTERN =
            java.util.regex.Pattern.compile("(?i)([rs]k_live_[a-zA-Z0-9]{24,})");
    private static final java.util.regex.Pattern GOOGLE_KEY_PATTERN =
            java.util.regex.Pattern.compile("(?i)(AIza[0-9A-Za-z\\-_]{35})");
    private static final java.util.regex.Pattern PEM_KEY_PATTERN =
            java.util.regex.Pattern.compile("-----BEGIN[ A-Z_-]+-----[\\s\\S]*?-----END[ A-Z_-]+-----");
    private static final java.util.regex.Pattern BEARER_PATTERN =
            java.util.regex.Pattern.compile("(?i)(bearer\\s+)[a-zA-Z0-9_\\-\\.~+/]+=*");
    private static final java.util.regex.Pattern AUTH_HEADER_PATTERN =
            java.util.regex.Pattern.compile("(?i)(authorization:\\s*(?:bearer\\s+)?)[^\\s,;]+");
    private static final java.util.regex.Pattern API_KEY_ASSIGNMENT =
            java.util.regex.Pattern.compile("(?i)(api[_-]?key\\s*[=:]\\s*)[^\\s,;]+");
    private static final java.util.regex.Pattern PASSWORD_ASSIGNMENT =
            java.util.regex.Pattern.compile("(?i)(password\\s*[=:]\\s*)[^\\s,;]+");

    public static double calculateShannonEntropy(String input) {
        if (input == null || input.isEmpty()) return 0.0;
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : input.toCharArray()) {
            freq.put(c, freq.getOrDefault(c, 0) + 1);
        }
        double entropy = 0.0;
        double len = input.length();
        for (int count : freq.values()) {
            double p = count / len;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    public static boolean isStandardUuid(String s) {
        if (s == null || s.length() != 36) return false;
        return s.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    public static boolean isIsoTimestamp(String s) {
        if (s == null || s.length() < 19) return false;
        return s.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
    }

    public static boolean isStandardUrl(String s) {
        if (s == null) return false;
        return s.startsWith("http://") || s.startsWith("https://") || s.startsWith("git@") || s.startsWith("ssh://");
    }

    public static boolean isSecretLikeString(String s) {
        if (s == null || s.length() < 20) return false;
        String trimmed = s.trim();

        if (isStandardUuid(trimmed) || isIsoTimestamp(trimmed) || isStandardUrl(trimmed)) {
            return false;
        }

        // Recognizable patterns
        if (CANARY_PATTERN.matcher(trimmed).find() ||
            GITHUB_TOKEN_PATTERN.matcher(trimmed).find() ||
            AWS_KEY_PATTERN.matcher(trimmed).find() ||
            SLACK_TOKEN_PATTERN.matcher(trimmed).find() ||
            GITLAB_TOKEN_PATTERN.matcher(trimmed).find() ||
            STRIPE_KEY_PATTERN.matcher(trimmed).find() ||
            GOOGLE_KEY_PATTERN.matcher(trimmed).find() ||
            PEM_KEY_PATTERN.matcher(trimmed).find() ||
            BEARER_PATTERN.matcher(trimmed).find() ||
            AUTH_HEADER_PATTERN.matcher(trimmed).find() ||
            API_KEY_ASSIGNMENT.matcher(trimmed).find() ||
            PASSWORD_ASSIGNMENT.matcher(trimmed).find()) {
            return true;
        }

        // High entropy hex key: 32+ characters hex, entropy >= 3.0
        if (trimmed.matches("^[0-9a-fA-F]{32,}$")) {
            return calculateShannonEntropy(trimmed) >= 3.0;
        }

        // High entropy base64 / token: 24+ characters alphanumeric with symbols
        if (trimmed.matches("^[a-zA-Z0-9+/=_-]{24,}$")) {
            boolean hasDigit = false;
            boolean hasLetter = false;
            for (char c : trimmed.toCharArray()) {
                if (Character.isDigit(c)) hasDigit = true;
                if (Character.isLetter(c)) hasLetter = true;
            }
            if (hasDigit && hasLetter) {
                return calculateShannonEntropy(trimmed) >= 3.5;
            }
        }

        return false;
    }

    private static boolean containsSecretPattern(Object value) {
        if (value == null) return false;
        if (value instanceof String str) {
            return isSecretLikeString(str);
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String k = String.valueOf(e.getKey()).toLowerCase();
                if (isSecretBearingName(k) || containsSecretPattern(e.getValue())) return true;
            }
        }
        if (value instanceof Iterable<?> iter) {
            for (Object item : iter) {
                if (containsSecretPattern(item)) return true;
            }
        }
        return false;
    }

    private static Map<String, Object> sanitizeMetadata(EventType eventType, Map<String, Object> input) {
        Map<String, Object> clean = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            String lower = key.toLowerCase();

            // 1. Known legacy redact keys are unconditionally masked
            if (KNOWN_LEGACY_REDACT_KEYS.contains(lower)) {
                clean.put(key, "[REDACTED]");
                continue;
            }

            // 2. Schema Allowlist validation: Reject unrecognized secret-bearing fields without logging content
            boolean allowed = isFieldAllowed(eventType, lower);
            if (!allowed) {
                if (isSecretBearingName(lower) || containsSecretPattern(entry.getValue())) {
                    throw new IllegalArgumentException("Unrecognized secret-bearing payload field rejected without exposing content");
                }
            }

            // 3. Recursive sanitization on allowed fields (ensures generic keys like 'key' do not bypass value checks)
            clean.put(key, sanitizeValue(entry.getValue()));
        }
        return clean;
    }

    public static String sanitizeHexTokensInText(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b[0-9a-fA-F]{32,}\\b").matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String token = m.group();
            if (calculateShannonEntropy(token) >= 3.0) {
                m.appendReplacement(sb, "[REDACTED]");
            } else {
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(token));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Object sanitizeValue(Object val) {
        if (val == null) return null;
        if (val instanceof String str) {
            String sanitized = str;
            sanitized = CANARY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_CANARY]");
            sanitized = GITHUB_TOKEN_PATTERN.matcher(sanitized).replaceAll("[REDACTED_TOKEN]");
            sanitized = AWS_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_TOKEN]");
            sanitized = SLACK_TOKEN_PATTERN.matcher(sanitized).replaceAll("[REDACTED_TOKEN]");
            sanitized = GITLAB_TOKEN_PATTERN.matcher(sanitized).replaceAll("[REDACTED_TOKEN]");
            sanitized = STRIPE_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_TOKEN]");
            sanitized = GOOGLE_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_TOKEN]");
            sanitized = PEM_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_KEY]");
            sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
            sanitized = AUTH_HEADER_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
            sanitized = API_KEY_ASSIGNMENT.matcher(sanitized).replaceAll("$1[REDACTED]");
            sanitized = PASSWORD_ASSIGNMENT.matcher(sanitized).replaceAll("$1[REDACTED]");

            // Standalone secret-like strings (e.g. raw 40-char hex key) in allowlisted fields are redacted
            if (isSecretLikeString(sanitized)) {
                return "[REDACTED]";
            }
            // Standalone hex tokens within text
            sanitized = sanitizeHexTokensInText(sanitized);

            return sanitized;
        }
        if (val instanceof Map<?, ?> map) {
            Map<String, Object> sanitizedMap = new HashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String k = String.valueOf(e.getKey());
                String lowerK = k.toLowerCase();
                if (KNOWN_LEGACY_REDACT_KEYS.contains(lowerK) || lowerK.contains("private_key")) {
                    sanitizedMap.put(k, "[REDACTED]");
                } else if (isSecretBearingName(lowerK)) {
                    throw new IllegalArgumentException("Unrecognized secret-bearing payload field rejected without exposing content");
                } else {
                    sanitizedMap.put(k, sanitizeValue(e.getValue()));
                }
            }
            return sanitizedMap;
        }
        if (val instanceof Iterable<?> iter) {
            java.util.List<Object> sanitizedList = new java.util.ArrayList<>();
            for (Object item : iter) {
                sanitizedList.add(sanitizeValue(item));
            }
            return sanitizedList;
        }
        return val;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID eventId = UUID.randomUUID();
        private EventType eventType;
        private int eventVersion = 1;
        private Instant occurredAt = Instant.now();
        private Instant recordedAt = Instant.now();
        private UUID workspaceId;
        private UUID projectId;
        private UUID environmentId;
        private UUID secretId;
        private String actorType = "USER";
        private String actorId;
        private String correlationId;
        private String causationId;
        private String requestId;
        private String source = "secretvault-core";
        private EventSeverity severity = EventSeverity.INFO;
        private String aggregateType = "UNKNOWN";
        private String aggregateId = "UNKNOWN";
        private Map<String, Object> metadata = new HashMap<>();

        public Builder eventId(UUID eventId) { this.eventId = eventId; return this; }
        public Builder eventType(EventType eventType) { this.eventType = eventType; return this; }
        public Builder eventVersion(int eventVersion) { this.eventVersion = eventVersion; return this; }
        public Builder occurredAt(Instant occurredAt) { this.occurredAt = occurredAt; return this; }
        public Builder recordedAt(Instant recordedAt) { this.recordedAt = recordedAt; return this; }
        public Builder workspaceId(UUID workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder projectId(UUID projectId) { this.projectId = projectId; return this; }
        public Builder environmentId(UUID environmentId) { this.environmentId = environmentId; return this; }
        public Builder secretId(UUID secretId) { this.secretId = secretId; return this; }
        public Builder actorType(String actorType) { this.actorType = actorType; return this; }
        public Builder actorId(String actorId) { this.actorId = actorId; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder causationId(String causationId) { this.causationId = causationId; return this; }
        public Builder requestId(String requestId) { this.requestId = requestId; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder severity(EventSeverity severity) { this.severity = severity; return this; }
        public Builder aggregateType(String aggregateType) { this.aggregateType = aggregateType; return this; }
        public Builder aggregateId(String aggregateId) { this.aggregateId = aggregateId; return this; }
        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) this.metadata = new HashMap<>(metadata);
            return this;
        }
        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public BaseDomainEvent build() {
            if (workspaceId == null) {
                throw new IllegalArgumentException("workspaceId is mandatory for domain events");
            }
            if (eventType == null) {
                throw new IllegalArgumentException("eventType is mandatory for domain events");
            }
            return new BaseDomainEvent(
                    eventId, eventType, eventVersion, occurredAt, recordedAt,
                    workspaceId, projectId, environmentId, secretId,
                    actorType, actorId, correlationId, causationId, requestId,
                    source, severity, aggregateType, aggregateId, metadata
            );
        }
    }
}
