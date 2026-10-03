package com.secretvault.rotation.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.secretvault.common.exception.ApiException;
import com.secretvault.rotation.model.SecretType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Cryptographically secure secret generator using SecureRandom.
 * Supports passwords, API keys, tokens, SSH keys, random bytes, and DB credentials.
 */
@Component
public class SecretGenerationEngine {

    private static final Logger log = LoggerFactory.getLogger(SecretGenerationEngine.class);

    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?";
    private static final String BASE62 = UPPER + LOWER + DIGITS;

    private final SecureRandom secureRandom;
    private final ObjectMapper objectMapper;

    public SecretGenerationEngine(ObjectMapper objectMapper) {
        this.secureRandom = new SecureRandom();
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a new cryptographically secure secret based on type and configuration JSON.
     */
    public String generateSecret(SecretType type, String configJson) {
        Objects.requireNonNull(type, "SecretType must not be null");

        JsonNode config = parseConfig(configJson);

        return switch (type) {
            case PASSWORD -> generatePassword(config);
            case API_KEY -> generateApiKey(config);
            case TOKEN -> generateToken(config);
            case RANDOM_STRING -> generateRandomString(config);
            case RANDOM_BYTES -> generateRandomBytes(config);
            case SSH_KEY -> generateSshKey(config);
            case DATABASE_CREDENTIAL -> generateDatabaseCredential(config);
            case CERTIFICATE -> generateDummyCertificate(config);
            case PROVIDER_CREDENTIAL, CUSTOM -> generateCustomOrFallback(config);
        };
    }

    public String generatePassword(int length, boolean includeSymbols, boolean includeDigits) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("length", length);
        config.put("includeSymbols", includeSymbols);
        config.put("includeDigits", includeDigits);
        config.put("includeUpper", true);
        return generatePassword(config);
    }

    public String generateApiKey(String prefix, int entropyBytes) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("prefix", prefix != null ? prefix : "sv_live_");
        config.put("entropyBytes", entropyBytes > 0 ? entropyBytes : 32);
        return generateApiKey(config);
    }

    public String generateToken(int length) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("length", length);
        return generateToken(config);
    }

    public String generateRandomString(int length) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("length", length);
        return generateRandomString(config);
    }

    public String generateBase64Bytes(int length) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("length", length);
        return generateRandomBytes(config);
    }

    public String generateRsaKeyPairPem(int keySize) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("keySize", keySize);
        return generateSshKey(config);
    }

    public String generatePassword(JsonNode config) {
        int length = getInt(config, "length", 32);
        boolean includeSymbols = getBoolean(config, "includeSymbols", true);
        boolean includeDigits = getBoolean(config, "includeDigits", true);
        boolean includeUpper = getBoolean(config, "includeUpper", true);

        StringBuilder alphabet = new StringBuilder(LOWER);
        if (includeUpper) alphabet.append(UPPER);
        if (includeDigits) alphabet.append(DIGITS);
        if (includeSymbols) alphabet.append(SYMBOLS);

        char[] chars = new char[Math.max(16, Math.min(length, 256))];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = alphabet.charAt(secureRandom.nextInt(alphabet.length()));
        }

        // Ensure at least one character from each required set
        if (includeUpper) chars[secureRandom.nextInt(chars.length)] = UPPER.charAt(secureRandom.nextInt(UPPER.length()));
        if (includeDigits) chars[secureRandom.nextInt(chars.length)] = DIGITS.charAt(secureRandom.nextInt(DIGITS.length()));
        if (includeSymbols) chars[secureRandom.nextInt(chars.length)] = SYMBOLS.charAt(secureRandom.nextInt(SYMBOLS.length()));

        String result = new String(chars);
        Arrays.fill(chars, '\0'); // Zeroize memory buffer
        return result;
    }

    public String generateApiKey(JsonNode config) {
        String prefix = getString(config, "prefix", "sv_live_");
        int byteLength = getInt(config, "entropyBytes", 32);
        byte[] bytes = new byte[Math.max(16, Math.min(byteLength, 128))];
        secureRandom.nextBytes(bytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Arrays.fill(bytes, (byte) 0);
        return prefix + key;
    }

    public String generateToken(JsonNode config) {
        int length = getInt(config, "length", 48);
        char[] chars = new char[Math.max(24, Math.min(length, 128))];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = BASE62.charAt(secureRandom.nextInt(BASE62.length()));
        }
        String result = new String(chars);
        Arrays.fill(chars, '\0');
        return result;
    }

    public String generateRandomString(JsonNode config) {
        int length = getInt(config, "length", 32);
        char[] chars = new char[Math.max(8, Math.min(length, 512))];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = BASE62.charAt(secureRandom.nextInt(BASE62.length()));
        }
        String result = new String(chars);
        Arrays.fill(chars, '\0');
        return result;
    }

    public String generateRandomBytes(JsonNode config) {
        int count = getInt(config, "length", 32);
        byte[] bytes = new byte[Math.max(8, Math.min(count, 256))];
        secureRandom.nextBytes(bytes);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        Arrays.fill(bytes, (byte) 0);
        return encoded;
    }

    public String generateDatabaseCredential(JsonNode config) {
        int length = getInt(config, "length", 32);
        String safeChars = UPPER + LOWER + DIGITS + "_-!#%";
        char[] chars = new char[Math.max(20, Math.min(length, 64))];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = safeChars.charAt(secureRandom.nextInt(safeChars.length()));
        }
        String result = new String(chars);
        Arrays.fill(chars, '\0');
        return result;
    }

    public String generateSshKey(JsonNode config) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(getInt(config, "keySize", 2048), secureRandom);
            KeyPair kp = kpg.generateKeyPair();
            String b64Priv = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(kp.getPrivate().getEncoded());
            return "-----BEGIN PRIVATE KEY-----\n" + b64Priv + "\n-----END PRIVATE KEY-----";
        } catch (Exception e) {
            throw ApiException.internal("SSH_KEYGEN_ERROR", "Failed to generate RSA SSH key pair: " + e.getMessage());
        }
    }

    public String generateDummyCertificate(JsonNode config) {
        byte[] raw = new byte[128];
        secureRandom.nextBytes(raw);
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(raw);
        return "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----";
    }

    public String generateCustomOrFallback(JsonNode config) {
        String format = getString(config, "format", null);
        if (format != null && !format.isBlank()) {
            return format.replace("{RANDOM}", generateToken(config));
        }
        return generatePassword(config);
    }

    private JsonNode parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(configJson);
        } catch (Exception e) {
            log.warn("Failed to parse generator config JSON, using defaults: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private int getInt(JsonNode node, String field, int defaultValue) {
        return node.has(field) && node.get(field).isInt() ? node.get(field).asInt() : defaultValue;
    }

    private boolean getBoolean(JsonNode node, String field, boolean defaultValue) {
        return node.has(field) && node.get(field).isBoolean() ? node.get(field).asBoolean() : defaultValue;
    }

    private String getString(JsonNode node, String field, String defaultValue) {
        return node.has(field) && node.get(field).isTextual() ? node.get(field).asText() : defaultValue;
    }
}
