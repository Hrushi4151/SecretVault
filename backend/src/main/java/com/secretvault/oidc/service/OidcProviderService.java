package com.secretvault.oidc.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.oidc.dto.OidcDtos;
import com.secretvault.oidc.entity.OidcProvider;
import com.secretvault.oidc.model.OidcProviderStatus;
import com.secretvault.oidc.model.OidcProviderType;
import com.secretvault.oidc.repository.OidcProviderRepository;
import com.secretvault.oidc.security.JwksKeyProvider;
import com.secretvault.oidc.security.OidcDiscoveryService;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OidcProviderService {

    private static final Logger log = LoggerFactory.getLogger(OidcProviderService.class);

    private final OidcProviderRepository providerRepository;
    private final WorkspaceRepository workspaceRepository;
    private final OidcDiscoveryService discoveryService;
    private final JwksKeyProvider jwksKeyProvider;
    private final AuditService auditService;

    public OidcProviderService(
            OidcProviderRepository providerRepository,
            WorkspaceRepository workspaceRepository,
            OidcDiscoveryService discoveryService,
            JwksKeyProvider jwksKeyProvider,
            AuditService auditService
    ) {
        this.providerRepository = providerRepository;
        this.workspaceRepository = workspaceRepository;
        this.discoveryService = discoveryService;
        this.jwksKeyProvider = jwksKeyProvider;
        this.auditService = auditService;
    }

    @Transactional
    public OidcDtos.OidcProviderResponse createProvider(
            UUID workspaceId,
            OidcDtos.CreateOidcProviderRequest request,
            UUID actorId
    ) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw ApiException.notFound("Workspace not found");
        }

        String name = request.name().trim();
        if (providerRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, name)) {
            throw ApiException.conflict("OIDC provider with name '" + name + "' already exists in this workspace");
        }

        String issuer = OidcDiscoveryService.normalizeIssuer(request.issuer());
        if (providerRepository.existsByWorkspaceIdAndIssuerIgnoreCase(workspaceId, issuer)) {
            throw ApiException.conflict("OIDC provider with issuer '" + issuer + "' already exists in this workspace");
        }

        // Auto-detect provider type if not explicitly set
        OidcProviderType providerType = request.providerType();
        if (providerType == null || providerType == OidcProviderType.GENERIC) {
            if (issuer.contains("token.actions.githubusercontent.com") || issuer.contains("github.com")) {
                providerType = OidcProviderType.GITHUB_ACTIONS;
            } else if (issuer.contains("gitlab.com")) {
                providerType = OidcProviderType.GITLAB_CI;
            } else {
                providerType = OidcProviderType.GENERIC;
            }
        }

        OidcProvider provider = new OidcProvider(
                workspaceId,
                name,
                issuer,
                request.discoveryUrl(),
                request.jwksUrl(),
                request.audience().trim(),
                providerType,
                request.allowedAlgorithms(),
                actorId
        );

        // Attempt initial discovery / JWKS prefetch if discoveryUrl is available
        try {
            String jwksUrl = provider.getJwksUrl();
            if (jwksUrl == null || jwksUrl.isBlank()) {
                OidcDiscoveryService.DiscoveryMetadata discovery = discoveryService.resolveDiscoveryMetadata(
                        provider.getIssuer(), provider.getDiscoveryUrl()
                );
                provider.setJwksUrl(discovery.jwksUri());
                jwksUrl = discovery.jwksUri();
            }
            if (jwksUrl != null && !jwksUrl.isBlank()) {
                jwksKeyProvider.getPublicKey(jwksUrl, null);
                provider.setLastJwksRefreshAt(Instant.now());
            }
        } catch (Exception e) {
            log.warn("Initial discovery/JWKS prefetch warning for provider [{}]: {}", name, e.getMessage());
        }

        OidcProvider saved = providerRepository.save(provider);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.OIDC_PROVIDER_CREATED,
                "OIDC_PROVIDER",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.info("Created OIDC provider [{}] (ID: {}, Type: {}) in workspace [{}]",
                saved.getName(), saved.getId(), saved.getProviderType(), workspaceId);

        return OidcDtos.OidcProviderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public OidcDtos.OidcProviderResponse getProvider(UUID id, UUID workspaceId) {
        OidcProvider provider = providerRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("OIDC provider not found in this workspace"));
        return OidcDtos.OidcProviderResponse.fromEntity(provider);
    }

    @Transactional(readOnly = true)
    public List<OidcDtos.OidcProviderResponse> listProviders(UUID workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw ApiException.notFound("Workspace not found");
        }
        return providerRepository.findByWorkspaceId(workspaceId)
                .stream()
                .map(OidcDtos.OidcProviderResponse::fromEntity)
                .toList();
    }

    @Transactional
    public OidcDtos.OidcProviderResponse updateProvider(
            UUID id,
            UUID workspaceId,
            OidcDtos.UpdateOidcProviderRequest request,
            UUID actorId
    ) {
        OidcProvider provider = providerRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("OIDC provider not found in this workspace"));

        if (request.name() != null && !request.name().isBlank()) {
            String newName = request.name().trim();
            if (!newName.equalsIgnoreCase(provider.getName()) &&
                    providerRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, newName)) {
                throw ApiException.conflict("OIDC provider with name '" + newName + "' already exists");
            }
            provider.setName(newName);
        }

        if (request.issuer() != null && !request.issuer().isBlank()) {
            String newIssuer = OidcDiscoveryService.normalizeIssuer(request.issuer());
            if (!newIssuer.equalsIgnoreCase(provider.getIssuer()) &&
                    providerRepository.existsByWorkspaceIdAndIssuerIgnoreCase(workspaceId, newIssuer)) {
                throw ApiException.conflict("OIDC provider with issuer '" + newIssuer + "' already exists");
            }
            provider.setIssuer(newIssuer);
        }

        if (request.discoveryUrl() != null) {
            provider.setDiscoveryUrl(request.discoveryUrl());
        }
        if (request.jwksUrl() != null) {
            provider.setJwksUrl(request.jwksUrl());
        }
        if (request.audience() != null && !request.audience().isBlank()) {
            provider.setAudience(request.audience().trim());
        }
        if (request.providerType() != null) {
            provider.setProviderType(request.providerType());
        }
        if (request.allowedAlgorithms() != null) {
            provider.setAllowedAlgorithms(request.allowedAlgorithms());
        }

        provider.setUpdatedAt(Instant.now());
        OidcProvider saved = providerRepository.save(provider);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.OIDC_PROVIDER_UPDATED,
                "OIDC_PROVIDER",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        return OidcDtos.OidcProviderResponse.fromEntity(saved);
    }

    @Transactional
    public OidcDtos.OidcProviderResponse disableProvider(UUID id, UUID workspaceId, UUID actorId) {
        OidcProvider provider = providerRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("OIDC provider not found in this workspace"));

        provider.setStatus(OidcProviderStatus.DISABLED);
        provider.setUpdatedAt(Instant.now());
        OidcProvider saved = providerRepository.save(provider);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.OIDC_PROVIDER_DISABLED,
                "OIDC_PROVIDER",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.info("Disabled OIDC provider [{}] in workspace [{}]", saved.getName(), workspaceId);
        return OidcDtos.OidcProviderResponse.fromEntity(saved);
    }

    @Transactional
    public OidcDtos.OidcProviderResponse enableProvider(UUID id, UUID workspaceId, UUID actorId) {
        OidcProvider provider = providerRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("OIDC provider not found in this workspace"));

        provider.setStatus(OidcProviderStatus.ACTIVE);
        provider.setUpdatedAt(Instant.now());
        OidcProvider saved = providerRepository.save(provider);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.OIDC_PROVIDER_ENABLED,
                "OIDC_PROVIDER",
                saved.getId(),
                null,
                null,
                "SUCCESS"
        );

        log.info("Enabled OIDC provider [{}] in workspace [{}]", saved.getName(), workspaceId);
        return OidcDtos.OidcProviderResponse.fromEntity(saved);
    }

    @Transactional
    public OidcDtos.OidcProviderResponse refreshJwks(UUID id, UUID workspaceId, UUID actorId) {
        OidcProvider provider = providerRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("OIDC provider not found in this workspace"));

        String jwksUrl = provider.getJwksUrl();
        if (jwksUrl == null || jwksUrl.isBlank()) {
            OidcDiscoveryService.DiscoveryMetadata discovery = discoveryService.refreshDiscoveryMetadata(
                    provider.getIssuer(), provider.getDiscoveryUrl()
            );
            provider.setJwksUrl(discovery.jwksUri());
            jwksUrl = discovery.jwksUri();
        }

        Map<String, PublicKey> keys = jwksKeyProvider.forceRefreshJwks(jwksUrl);
        provider.setLastJwksRefreshAt(Instant.now());
        provider.setUpdatedAt(Instant.now());
        OidcProvider saved = providerRepository.save(provider);

        log.info("Refreshed JWKS for provider [{}] (Loaded {} keys)", provider.getName(), keys.size());
        return OidcDtos.OidcProviderResponse.fromEntity(saved);
    }

    @Transactional
    public Map<String, Object> testProvider(UUID id, UUID workspaceId) {
        OidcProvider provider = providerRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("OIDC provider not found in this workspace"));

        try {
            OidcDiscoveryService.DiscoveryMetadata discovery = discoveryService.resolveDiscoveryMetadata(
                    provider.getIssuer(), provider.getDiscoveryUrl()
            );

            String jwksUrl = provider.getJwksUrl() != null && !provider.getJwksUrl().isBlank()
                    ? provider.getJwksUrl() : discovery.jwksUri();

            Map<String, PublicKey> keys = jwksKeyProvider.forceRefreshJwks(jwksUrl);

            return Map.of(
                    "status", "SUCCESS",
                    "discoveredIssuer", discovery.issuer(),
                    "jwksUri", jwksUrl,
                    "supportedAlgorithms", discovery.idTokenSigningAlgValuesSupported(),
                    "keysLoadedCount", keys.size()
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            );
        }
    }

    @Transactional
    public void deleteProvider(UUID id, UUID workspaceId, UUID actorId) {
        OidcProvider provider = providerRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> ApiException.notFound("OIDC provider not found in this workspace"));

        providerRepository.delete(provider);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.OIDC_PROVIDER_DISABLED,
                "OIDC_PROVIDER",
                id,
                null,
                null,
                "DELETED"
        );

        log.info("Deleted OIDC provider [{}] from workspace [{}]", provider.getName(), workspaceId);
    }
}
