package com.secretvault.auth.webauthn.config;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * Spring configuration creating the Yubico {@link RelyingParty} instance with configured origins,
 * RP ID, RP Name, and credential repository adapter.
 */
@Configuration
public class WebAuthnConfig {

    @Bean
    public RelyingParty relyingParty(
            WebAuthnProperties properties,
            CredentialRepository credentialRepository
    ) {
        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id(properties.getRpId())
                .name(properties.getRpName())
                .build();

        Set<String> origins = new HashSet<>(properties.getAllowedOrigins());

        return RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(credentialRepository)
                .origins(origins)
                .allowOriginPort(properties.isAllowOriginPort())
                .build();
    }
}
