package com.secretvault.auth.webauthn.repository;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.auth.webauthn.entity.UserWebAuthnCredential;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter implementing Yubico's {@link CredentialRepository} interface.
 * Bridges WebAuthn verification operations with PostgreSQL persistent storage.
 */
@Component
public class WebAuthnCredentialRepositoryAdapter implements CredentialRepository {

    private static final Logger log = LoggerFactory.getLogger(WebAuthnCredentialRepositoryAdapter.class);

    private final UserWebAuthnCredentialRepository credentialRepository;
    private final UserRepository userRepository;

    public WebAuthnCredentialRepositoryAdapter(
            UserWebAuthnCredentialRepository credentialRepository,
            UserRepository userRepository
    ) {
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        if (username == null || username.isBlank()) {
            return Collections.emptySet();
        }

        Optional<User> userOpt = userRepository.findByEmail(username.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            return Collections.emptySet();
        }

        List<UserWebAuthnCredential> credentials = credentialRepository.findActiveByUserId(userOpt.get().getId());
        return credentials.stream()
                .map(c -> {
                    try {
                        return PublicKeyCredentialDescriptor.builder()
                                .id(ByteArray.fromBase64Url(c.getCredentialId()))
                                .build();
                    } catch (Exception ex) {
                        log.warn("Invalid Base64Url credential ID for credential [{}]: {}", c.getId(), ex.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        return userRepository.findByEmail(username.trim().toLowerCase())
                .map(user -> new ByteArray(user.getId().toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        if (userHandle == null || userHandle.getBytes().length == 0) {
            return Optional.empty();
        }

        try {
            String userUuidStr = new String(userHandle.getBytes(), StandardCharsets.UTF_8);
            UUID userId = UUID.fromString(userUuidStr);
            return userRepository.findById(userId).map(User::getEmail);
        } catch (Exception ex) {
            log.debug("Failed to resolve user handle to email: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        if (credentialId == null) {
            return Optional.empty();
        }

        String base64UrlId = credentialId.getBase64Url();
        Optional<UserWebAuthnCredential> credOpt = credentialRepository.findByCredentialIdAndRevokedAtIsNull(base64UrlId);
        if (credOpt.isEmpty()) {
            return Optional.empty();
        }

        UserWebAuthnCredential cred = credOpt.get();
        ByteArray resolvedUserHandle = new ByteArray(cred.getUserId().toString().getBytes(StandardCharsets.UTF_8));

        // If userHandle is provided, enforce that it matches the stored credential's user handle
        if (userHandle != null && userHandle.getBytes().length > 0 && !userHandle.equals(resolvedUserHandle)) {
            log.warn("User handle mismatch for credential [{}]: expected [{}] but got [{}]",
                    base64UrlId, resolvedUserHandle.getBase64Url(), userHandle.getBase64Url());
            return Optional.empty();
        }

        return Optional.of(RegisteredCredential.builder()
                .credentialId(credentialId)
                .userHandle(resolvedUserHandle)
                .publicKeyCose(new ByteArray(cred.getPublicKeyCose()))
                .signatureCount(cred.getSignCount())
                .build());
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        if (credentialId == null) {
            return Collections.emptySet();
        }

        String base64UrlId = credentialId.getBase64Url();
        Optional<UserWebAuthnCredential> credOpt = credentialRepository.findByCredentialIdAndRevokedAtIsNull(base64UrlId);
        if (credOpt.isEmpty()) {
            return Collections.emptySet();
        }

        UserWebAuthnCredential cred = credOpt.get();
        ByteArray userHandle = new ByteArray(cred.getUserId().toString().getBytes(StandardCharsets.UTF_8));

        RegisteredCredential registeredCredential = RegisteredCredential.builder()
                .credentialId(credentialId)
                .userHandle(userHandle)
                .publicKeyCose(new ByteArray(cred.getPublicKeyCose()))
                .signatureCount(cred.getSignCount())
                .build();

        return Collections.singleton(registeredCredential);
    }
}
