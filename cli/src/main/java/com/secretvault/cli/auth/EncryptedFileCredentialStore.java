package com.secretvault.cli.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Robust Encrypted File Credential Store utilizing AES-256-GCM encryption with PBKDF2 key derivation.
 * Tokens are NEVER persisted in plaintext.
 */
public class EncryptedFileCredentialStore implements CredentialStore {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int KEY_LENGTH_BITS = 256;

    private final Path storagePath;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;

    public EncryptedFileCredentialStore(Path storageDirectory) {
        this.storagePath = storageDirectory.resolve(".credentials.enc");
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.secureRandom = new SecureRandom();
    }

    @Override
    public synchronized void save(String profile, String serverUrl, StoredCredentials credentials) {
        Map<String, StoredCredentials> map = loadAll();
        String key = buildStoreKey(profile, serverUrl);
        map.put(key, credentials);
        saveAll(map);
    }

    @Override
    public synchronized Optional<StoredCredentials> load(String profile, String serverUrl) {
        Map<String, StoredCredentials> map = loadAll();
        String key = buildStoreKey(profile, serverUrl);
        return Optional.ofNullable(map.get(key));
    }

    @Override
    public synchronized void delete(String profile, String serverUrl) {
        Map<String, StoredCredentials> map = loadAll();
        String key = buildStoreKey(profile, serverUrl);
        if (map.remove(key) != null) {
            saveAll(map);
        }
    }

    @Override
    public synchronized void clearAll() {
        try {
            if (Files.exists(storagePath)) {
                Files.delete(storagePath);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            Path parent = storagePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildStoreKey(String profile, String serverUrl) {
        String p = (profile == null || profile.isBlank()) ? "default" : profile.trim();
        String s = (serverUrl == null || serverUrl.isBlank()) ? "http://localhost:8080" : serverUrl.trim();
        return p + "@" + s;
    }

    private Map<String, StoredCredentials> loadAll() {
        if (!Files.exists(storagePath)) {
            return new HashMap<>();
        }
        try {
            byte[] fileBytes = Files.readAllBytes(storagePath);
            if (fileBytes.length < SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES) {
                return new HashMap<>();
            }

            ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
            byte[] salt = new byte[SALT_LENGTH_BYTES];
            buffer.get(salt);

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            SecretKey secretKey = deriveKey(salt);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] decrypted = cipher.doFinal(ciphertext);
            return objectMapper.readValue(decrypted, new TypeReference<Map<String, StoredCredentials>>() {});
        } catch (Exception e) {
            // In case of corruption or altered master password, return empty map
            return new HashMap<>();
        }
    }

    private void saveAll(Map<String, StoredCredentials> map) {
        try {
            Path parent = storagePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            byte[] jsonBytes = objectMapper.writeValueAsBytes(map);
            byte[] salt = new byte[SALT_LENGTH_BYTES];
            secureRandom.nextBytes(salt);

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            SecretKey secretKey = deriveKey(salt);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(jsonBytes);

            ByteBuffer buffer = ByteBuffer.allocate(salt.length + iv.length + ciphertext.length);
            buffer.put(salt);
            buffer.put(iv);
            buffer.put(ciphertext);

            Files.write(storagePath, buffer.array());
            applySecurePermissions(storagePath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist secure credentials: " + e.getMessage(), e);
        }
    }

    private SecretKey deriveKey(byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String baseSecret = getMachineUniqueSeed();
        KeySpec spec = new PBEKeySpec(baseSecret.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private String getMachineUniqueSeed() {
        String user = System.getProperty("user.name", "sv-user");
        String home = System.getProperty("user.home", "/var/secretvault");
        String os = System.getProperty("os.name", "generic-os");
        return "SecretVault-CLI-MasterSeed:" + user + ":" + home + ":" + os;
    }

    private void applySecurePermissions(Path path) {
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows or non-POSIX filesystem, file is in user home which is private by default
            File file = path.toFile();
            file.setReadable(true, true);
            file.setWritable(true, true);
            file.setExecutable(false, false);
        } catch (Exception ignored) {
        }
    }
}
