package com.secretvault.secret.service;

import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.secret.dto.SecretDiffResponse;
import com.secretvault.secret.dto.SecretValueDiffResponse;
import com.secretvault.secret.dto.SecretVersionResponse;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class SecretDiffService {

    private static final int MAX_DIFF_BYTES = 65536; // 64 KB DoS protection limit

    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final EncryptionService encryptionService;
    private final SecretAuthorizationHelper authHelper;

    public SecretDiffService(
            SecretRepository secretRepository,
            SecretVersionRepository secretVersionRepository,
            EncryptionService encryptionService,
            SecretAuthorizationHelper authHelper
    ) {
        this.secretRepository = secretRepository;
        this.secretVersionRepository = secretVersionRepository;
        this.encryptionService = encryptionService;
        this.authHelper = authHelper;
    }

    @Transactional(readOnly = true)
    public SecretDiffResponse compareVersions(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer fromVersionNumber,
            Integer toVersionNumber,
            UUID userId
    ) {
        authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, environmentId, userId);

        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        SecretVersion fromVer = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, fromVersionNumber)
                .orElseThrow(() -> ApiException.notFound("Source version " + fromVersionNumber + " not found"));

        SecretVersion toVer = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, toVersionNumber)
                .orElseThrow(() -> ApiException.notFound("Target version " + toVersionNumber + " not found"));

        // In-memory decrypt to compute cryptographic comparison without exposing plaintext
        byte[] fromBytes = decryptVersion(secret.getId(), environmentId, fromVer);
        byte[] toBytes = decryptVersion(secret.getId(), environmentId, toVer);

        boolean isEqual = Arrays.equals(fromBytes, toBytes);
        double entropyA = calculateShannonEntropy(fromBytes);
        double entropyB = calculateShannonEntropy(toBytes);

        String diffType;
        if (isEqual) {
            diffType = "IDENTICAL";
        } else if (fromBytes.length == 0 && toBytes.length > 0) {
            diffType = "EMPTY_TO_VALUE";
        } else if (fromBytes.length > 0 && toBytes.length == 0) {
            diffType = "VALUE_TO_EMPTY";
        } else {
            diffType = "MODIFIED";
        }

        Arrays.fill(fromBytes, (byte) 0);
        Arrays.fill(toBytes, (byte) 0);

        SecretVersionResponse fromMeta = SecretVersionResponse.fromEntity(
                fromVer, Collections.emptyList(), fromVer.getVersionNumber().equals(secret.getCurrentVersionNumber())
        );
        SecretVersionResponse toMeta = SecretVersionResponse.fromEntity(
                toVer, Collections.emptyList(), toVer.getVersionNumber().equals(secret.getCurrentVersionNumber())
        );

        String message = isEqual
                ? "Versions " + fromVersionNumber + " and " + toVersionNumber + " have identical secret payloads"
                : "Versions " + fromVersionNumber + " and " + toVersionNumber + " diverge (" + diffType + ")";

        return new SecretDiffResponse(
                secretId,
                fromVersionNumber,
                toVersionNumber,
                isEqual,
                diffType,
                fromMeta,
                toMeta,
                entropyA,
                entropyB,
                message
        );
    }

    @Transactional(readOnly = true)
    public SecretValueDiffResponse computeValueDiff(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer fromVersionNumber,
            Integer toVersionNumber,
            UUID userId
    ) {
        authHelper.verifyHierarchyAndRevealAccess(workspaceId, projectId, environmentId, userId);

        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        SecretVersion fromVer = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, fromVersionNumber)
                .orElseThrow(() -> ApiException.notFound("Source version " + fromVersionNumber + " not found"));

        SecretVersion toVer = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, toVersionNumber)
                .orElseThrow(() -> ApiException.notFound("Target version " + toVersionNumber + " not found"));

        byte[] fromBytes = decryptVersion(secret.getId(), environmentId, fromVer);
        byte[] toBytes = decryptVersion(secret.getId(), environmentId, toVer);

        if (fromBytes.length > MAX_DIFF_BYTES || toBytes.length > MAX_DIFF_BYTES) {
            Arrays.fill(fromBytes, (byte) 0);
            Arrays.fill(toBytes, (byte) 0);
            throw ApiException.badRequest("PAYLOAD_TOO_LARGE", "Secret payload exceeds maximum diff limit (64KB)");
        }

        String fromText = new String(fromBytes, StandardCharsets.UTF_8);
        String toText = new String(toBytes, StandardCharsets.UTF_8);

        Arrays.fill(fromBytes, (byte) 0);
        Arrays.fill(toBytes, (byte) 0);

        boolean isEqual = Objects.equals(fromText, toText);
        List<SecretValueDiffResponse.DiffLine> lines = computeLineDiff(fromText, toText);

        return new SecretValueDiffResponse(
                secretId,
                fromVersionNumber,
                toVersionNumber,
                isEqual,
                lines,
                Instant.now()
        );
    }

    private byte[] decryptVersion(UUID secretId, UUID environmentId, SecretVersion version) {
        EncryptedPayload payload = new EncryptedPayload(
                version.getCiphertext(),
                version.getEncryptedDek(),
                version.getIv(),
                version.getAuthTag(),
                version.getKeyReference()
        );
        String aad = SecretAuthorizationHelper.buildAad(secretId, environmentId, version.getVersionNumber());
        return encryptionService.decrypt(payload, aad);
    }

    private double calculateShannonEntropy(byte[] data) {
        if (data == null || data.length == 0) return 0.0;
        Map<Byte, Integer> freq = new HashMap<>();
        for (byte b : data) {
            freq.put(b, freq.getOrDefault(b, 0) + 1);
        }
        double entropy = 0.0;
        double len = data.length;
        for (int count : freq.values()) {
            double p = count / len;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return Math.round(entropy * 100.0) / 100.0;
    }

    private List<SecretValueDiffResponse.DiffLine> computeLineDiff(String textA, String textB) {
        List<SecretValueDiffResponse.DiffLine> result = new ArrayList<>();
        String[] linesA = textA.isEmpty() ? new String[0] : textA.split("\\r?\\n");
        String[] linesB = textB.isEmpty() ? new String[0] : textB.split("\\r?\\n");

        int max = Math.max(linesA.length, linesB.length);
        for (int i = 0; i < max; i++) {
            String a = i < linesA.length ? linesA[i] : null;
            String b = i < linesB.length ? linesB[i] : null;

            if (Objects.equals(a, b)) {
                result.add(new SecretValueDiffResponse.DiffLine(SecretValueDiffResponse.DiffLineType.EQUAL, a != null ? a : ""));
            } else {
                if (a != null) {
                    result.add(new SecretValueDiffResponse.DiffLine(SecretValueDiffResponse.DiffLineType.REMOVED, a));
                }
                if (b != null) {
                    result.add(new SecretValueDiffResponse.DiffLine(SecretValueDiffResponse.DiffLineType.ADDED, b));
                }
            }
        }
        return result;
    }
}
