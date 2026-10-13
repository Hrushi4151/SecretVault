package com.secretvault.repository.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.provider.credential.ProviderCredentialService;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.repository.dto.RepositoryDtos;
import com.secretvault.repository.entity.RepositoryEntity;
import com.secretvault.repository.entity.SecretFinding;
import com.secretvault.repository.model.RepoFindingSeverity;
import com.secretvault.repository.model.RepoFindingStatus;
import com.secretvault.repository.model.RepositoryProvider;
import com.secretvault.repository.model.RepositoryStatus;
import com.secretvault.repository.model.RepositoryVisibility;
import com.secretvault.repository.repository.RepositoryEntityRepository;
import com.secretvault.repository.repository.SecretFindingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class RepositoryService {

    private final RepositoryEntityRepository repositoryRepo;
    private final SecretFindingRepository findingRepo;
    private final EffectiveAccessService accessService;
    private final AuditService auditService;
    private final com.secretvault.repository.provider.RepositoryProviderRegistry providerRegistry;
    private final RepositoryScanService scanService;
    private final ProviderIntegrationRepository providerIntegrationRepo;
    private final ProviderCredentialService providerCredentialService;
    private final ObjectMapper objectMapper;

    public RepositoryService(
            RepositoryEntityRepository repositoryRepo,
            SecretFindingRepository findingRepo,
            EffectiveAccessService accessService,
            AuditService auditService) {
        this(repositoryRepo, findingRepo, accessService, auditService, null, null, null, null, new ObjectMapper());
    }

    public RepositoryService(
            RepositoryEntityRepository repositoryRepo,
            SecretFindingRepository findingRepo,
            EffectiveAccessService accessService,
            AuditService auditService,
            com.secretvault.repository.provider.RepositoryProviderRegistry providerRegistry) {
        this(repositoryRepo, findingRepo, accessService, auditService, providerRegistry, null, null, null, new ObjectMapper());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RepositoryService(
            RepositoryEntityRepository repositoryRepo,
            SecretFindingRepository findingRepo,
            EffectiveAccessService accessService,
            AuditService auditService,
            com.secretvault.repository.provider.RepositoryProviderRegistry providerRegistry,
            @org.springframework.context.annotation.Lazy RepositoryScanService scanService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ProviderIntegrationRepository providerIntegrationRepo,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ProviderCredentialService providerCredentialService,
            ObjectMapper objectMapper) {
        this.repositoryRepo = repositoryRepo;
        this.findingRepo = findingRepo;
        this.accessService = accessService;
        this.auditService = auditService;
        this.providerRegistry = providerRegistry;
        this.scanService = scanService;
        this.providerIntegrationRepo = providerIntegrationRepo;
        this.providerCredentialService = providerCredentialService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public record RepositorySummaryDto(
            UUID id,
            UUID workspaceId,
            RepositoryProvider provider,
            String externalRepositoryId,
            String owner,
            String name,
            String defaultBranch,
            String cloneUrl,
            RepositoryVisibility visibility,
            RepositoryStatus status,
            Instant lastScanAt,
            Instant lastSuccessfulScanAt,
            String lastCommitSha,
            long totalFindings,
            long criticalFindings,
            Instant createdAt,
            Instant updatedAt
    ) {}

    @Transactional(readOnly = true)
    public Page<RepositorySummaryDto> listRepositories(UUID workspaceId, UUID actorId, Pageable pageable) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_VIEW);
        Page<RepositoryEntity> page = repositoryRepo.findByWorkspaceId(workspaceId, pageable);
        return page.map(this::toSummaryDto);
    }

    @Transactional(readOnly = true)
    public RepositorySummaryDto getRepository(UUID workspaceId, UUID repositoryId, UUID actorId) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_VIEW);
        RepositoryEntity repo = repositoryRepo.findByIdAndWorkspaceId(repositoryId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));
        return toSummaryDto(repo);
    }

    @Transactional(readOnly = true)
    public RepositoryDtos.GitHubIntegrationStatusResponse getGitHubIntegrationStatus(UUID workspaceId, UUID actorId) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_VIEW);

        if (providerIntegrationRepo == null) {
            return new RepositoryDtos.GitHubIntegrationStatusResponse(false, null, null, null, List.of(), "NOT_CONNECTED", 0, null, null);
        }

        List<ProviderIntegration> list =
                providerIntegrationRepo.findByWorkspaceIdAndProviderType(workspaceId, ProviderType.GITHUB);
        if (list.isEmpty()) {
            return new RepositoryDtos.GitHubIntegrationStatusResponse(false, null, null, null, List.of(), "NOT_CONNECTED", 0, null, null);
        }

        ProviderIntegration integration = list.get(0);
        String username = null;
        String name = null;
        String avatarUrl = null;
        List<String> scopes = new ArrayList<>();

        try {
            if (integration.getConfigurationJson() != null && !integration.getConfigurationJson().isBlank()) {
                JsonNode cfg = objectMapper.readTree(integration.getConfigurationJson());
                username = cfg.path("username").asText(null);
                name = cfg.path("name").asText(username);
                avatarUrl = cfg.path("avatarUrl").asText(null);
                if (cfg.has("scopes") && cfg.path("scopes").isArray()) {
                    for (JsonNode s : cfg.path("scopes")) {
                        scopes.add(s.asText());
                    }
                }
            }
        } catch (Exception ignored) {}

        long repoCount = repositoryRepo.countByWorkspaceIdAndProvider(workspaceId, RepositoryProvider.GITHUB);

        return new RepositoryDtos.GitHubIntegrationStatusResponse(
                integration.getStatus() == IntegrationStatus.ACTIVE,
                username != null ? username : integration.getDisplayName(),
                name,
                avatarUrl,
                scopes,
                integration.getStatus().name(),
                repoCount,
                integration.getCreatedAt(),
                integration.getLastValidatedAt()
        );
    }

    @Transactional
    public RepositoryDtos.GitHubIntegrationStatusResponse connectGitHubIntegration(
            UUID workspaceId,
            String token,
            UUID actorId) {

        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_MANAGE);

        if (token == null || token.isBlank()) {
            throw ApiException.badRequest("GitHub token or credentials are required");
        }

        if (providerRegistry == null || providerIntegrationRepo == null || providerCredentialService == null) {
            throw new IllegalStateException("Provider integration services not configured");
        }

        com.secretvault.repository.provider.RepositoryProviderAdapter adapter =
                providerRegistry.requireAdapter(RepositoryProvider.GITHUB);

        if (!adapter.validateConnection(token)) {
            throw ApiException.badRequest("Failed to authenticate with GitHub. Please check your token permissions.");
        }

        com.secretvault.repository.provider.RepositoryProviderAdapter.UserProfileDto profile = adapter.getUserProfile(token);
        String username = profile != null ? profile.login() : "GitHub Account";
        String displayName = "GitHub (" + username + ")";

        List<ProviderIntegration> existingList =
                providerIntegrationRepo.findByWorkspaceIdAndProviderType(workspaceId, ProviderType.GITHUB);

        ProviderIntegration integration;
        if (!existingList.isEmpty()) {
            integration = existingList.get(0);
        } else {
            integration = new ProviderIntegration();
            integration.setWorkspaceId(workspaceId);
            integration.setCreatedBy(actorId);
        }

        integration.setProviderType(ProviderType.GITHUB);
        integration.setDisplayName(displayName);
        integration.setStatus(IntegrationStatus.ACTIVE);
        integration.setLastValidatedAt(Instant.now());

        Map<String, Object> configMap = new HashMap<>();
        if (profile != null) {
            configMap.put("username", profile.login());
            configMap.put("name", profile.name());
            configMap.put("avatarUrl", profile.avatarUrl());
            configMap.put("email", profile.email());
            configMap.put("scopes", profile.scopes());
            configMap.put("publicRepos", profile.publicRepos());
            configMap.put("totalPrivateRepos", profile.totalPrivateRepos());
        }
        try {
            integration.setConfigurationJson(objectMapper.writeValueAsString(configMap));
        } catch (Exception e) {
            integration.setConfigurationJson("{}");
        }

        providerCredentialService.encryptAndSetCredentials(integration, token);
        providerIntegrationRepo.save(integration);

        auditService.record(
                workspaceId,
                actorId,
                AuditAction.PROVIDER_INTEGRATION_CREATED,
                "GITHUB_INTEGRATION",
                integration.getId().toString(),
                Map.of("provider", "GITHUB", "username", username, "action", "CONNECTED")
        );

        return getGitHubIntegrationStatus(workspaceId, actorId);
    }

    @Transactional
    public RepositoryDtos.GitHubIntegrationStatusResponse refreshGitHubIntegration(UUID workspaceId, UUID actorId) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_MANAGE);

        if (providerIntegrationRepo == null || providerCredentialService == null || providerRegistry == null) {
            throw new IllegalStateException("Provider integration services not configured");
        }

        List<ProviderIntegration> list =
                providerIntegrationRepo.findByWorkspaceIdAndProviderType(workspaceId, ProviderType.GITHUB);
        if (list.isEmpty()) {
            throw ApiException.notFound("No connected GitHub account found for this workspace");
        }

        ProviderIntegration integration = list.get(0);
        String token = providerCredentialService.decryptCredential(integration);

        com.secretvault.repository.provider.RepositoryProviderAdapter adapter =
                providerRegistry.requireAdapter(RepositoryProvider.GITHUB);

        if (!adapter.validateConnection(token)) {
            integration.setStatus(IntegrationStatus.ERROR);
            integration.setLastErrorAt(Instant.now());
            integration.setLastErrorMessage("GitHub credentials expired or revoked");
            providerIntegrationRepo.save(integration);
            throw ApiException.unauthorized("TOKEN_EXPIRED", "GitHub credentials expired or revoked. Please reauthorize.");
        }

        com.secretvault.repository.provider.RepositoryProviderAdapter.UserProfileDto profile = adapter.getUserProfile(token);
        integration.setStatus(IntegrationStatus.ACTIVE);
        integration.setLastValidatedAt(Instant.now());

        if (profile != null) {
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("username", profile.login());
            configMap.put("name", profile.name());
            configMap.put("avatarUrl", profile.avatarUrl());
            configMap.put("email", profile.email());
            configMap.put("scopes", profile.scopes());
            configMap.put("publicRepos", profile.publicRepos());
            configMap.put("totalPrivateRepos", profile.totalPrivateRepos());
            try {
                integration.setConfigurationJson(objectMapper.writeValueAsString(configMap));
            } catch (Exception ignored) {}
        }

        providerIntegrationRepo.save(integration);

        auditService.record(
                workspaceId,
                actorId,
                AuditAction.PROVIDER_INTEGRATION_VALIDATED,
                "GITHUB_INTEGRATION",
                integration.getId().toString(),
                Map.of("provider", "GITHUB", "action", "REFRESHED")
        );

        return getGitHubIntegrationStatus(workspaceId, actorId);
    }

    @Transactional
    public void disconnectGitHubIntegration(UUID workspaceId, UUID actorId) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_MANAGE);

        if (providerIntegrationRepo == null) return;

        List<ProviderIntegration> list =
                providerIntegrationRepo.findByWorkspaceIdAndProviderType(workspaceId, ProviderType.GITHUB);
        for (ProviderIntegration intg : list) {
            providerIntegrationRepo.delete(intg);
            auditService.record(
                    workspaceId,
                    actorId,
                    AuditAction.PROVIDER_INTEGRATION_DELETED,
                    "GITHUB_INTEGRATION",
                    intg.getId().toString(),
                    Map.of("provider", "GITHUB", "action", "DISCONNECTED")
            );
        }
    }

    @Transactional
    public RepositorySummaryDto setRepositoryMonitoringEnabled(UUID workspaceId, UUID repositoryId, boolean enabled, UUID actorId) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_MANAGE);

        RepositoryEntity repo = repositoryRepo.findByIdAndWorkspaceId(repositoryId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        repo.setStatus(enabled ? RepositoryStatus.ACTIVE : RepositoryStatus.DISABLED);
        RepositoryEntity saved = repositoryRepo.save(repo);

        auditService.record(
                workspaceId,
                actorId,
                enabled ? AuditAction.REPOSITORY_CONNECTED : AuditAction.REPOSITORY_DISCONNECTED,
                "REPOSITORY",
                repo.getId().toString(),
                Map.of("owner", repo.getOwner(), "name", repo.getName(), "monitoringEnabled", enabled)
        );

        return toSummaryDto(saved);
    }

    @Transactional(readOnly = true)
    public List<com.secretvault.repository.provider.RepositoryProviderAdapter.RemoteRepositoryDto> discoverRemoteRepositories(
            UUID workspaceId,
            RepositoryProvider provider,
            String token,
            String username,
            UUID actorId) {

        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_VIEW);

        if (providerRegistry == null) {
            return List.of();
        }

        RepositoryProvider effectiveProvider = provider != null ? provider : RepositoryProvider.GITHUB;
        com.secretvault.repository.provider.RepositoryProviderAdapter adapter = providerRegistry.requireAdapter(effectiveProvider);

        String effectiveToken = token;
        if ((effectiveToken == null || effectiveToken.isBlank()) && (username == null || username.isBlank())) {
            // Automatically use the workspace's connected GitHub integration credentials
            if (providerIntegrationRepo != null && providerCredentialService != null) {
                List<ProviderIntegration> integrations =
                        providerIntegrationRepo.findByWorkspaceIdAndProviderType(workspaceId, ProviderType.GITHUB);
                if (!integrations.isEmpty()) {
                    ProviderIntegration activeInt = integrations.stream()
                            .filter(i -> i.getStatus() == IntegrationStatus.ACTIVE)
                            .findFirst()
                            .orElse(integrations.get(0));
                    try {
                        effectiveToken = providerCredentialService.decryptCredential(activeInt);
                    } catch (Exception e) {
                        org.slf4j.LoggerFactory.getLogger(RepositoryService.class)
                                .warn("Failed to decrypt stored GitHub credentials for workspace {}: {}", workspaceId, e.getMessage());
                    }
                }
            }
        }

        List<com.secretvault.repository.provider.RepositoryProviderAdapter.RemoteRepositoryDto> remoteRepos =
                adapter.listRepositories(effectiveToken, username);

        // Cross-reference with workspace repositories to annotate already connected/monitored repos
        return remoteRepos.stream().map(r -> {
            Optional<RepositoryEntity> existing = repositoryRepo.findByWorkspaceIdAndOwnerAndName(workspaceId, r.owner(), r.name());
            if (existing.isPresent()) {
                RepositoryEntity entity = existing.get();
                String scanStatus = entity.getStatus() == RepositoryStatus.DISABLED ? "DISABLED"
                        : entity.getLastSuccessfulScanAt() != null ? "SCANNED" : "CONNECTED";
                return new com.secretvault.repository.provider.RepositoryProviderAdapter.RemoteRepositoryDto(
                        r.id(),
                        r.owner(),
                        r.name(),
                        r.defaultBranch(),
                        r.cloneUrl(),
                        r.visibility(),
                        r.description(),
                        r.isPrivate(),
                        r.canRead(),
                        r.canAdminHooks(),
                        true,
                        scanStatus,
                        entity.getLastScanAt(),
                        r.permissionWarning(),
                        r.role()
                );
            }
            return r;
        }).toList();
    }

    @Transactional
    public RepositoryEntity connectRepository(
            UUID workspaceId,
            RepositoryProvider provider,
            String externalId,
            String owner,
            String name,
            String defaultBranch,
            String cloneUrl,
            RepositoryVisibility visibility,
            UUID actorId) {

        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_MANAGE);

        if (repositoryRepo.existsByWorkspaceIdAndOwnerAndName(workspaceId, owner, name)) {
            throw new IllegalStateException("Repository " + owner + "/" + name + " is already registered in this workspace");
        }

        RepositoryEntity repo = new RepositoryEntity();
        repo.setWorkspaceId(workspaceId);
        repo.setProvider(provider != null ? provider : RepositoryProvider.GITHUB);
        repo.setExternalRepositoryId(externalId != null ? externalId : owner + "/" + name);
        repo.setOwner(owner);
        repo.setName(name);
        repo.setDefaultBranch(defaultBranch != null ? defaultBranch : "main");
        repo.setCloneUrl(cloneUrl);
        repo.setVisibility(visibility != null ? visibility : RepositoryVisibility.PRIVATE);
        repo.setStatus(RepositoryStatus.ACTIVE);
        repo.setCreatedBy(actorId);

        RepositoryEntity saved = repositoryRepo.save(repo);

        auditService.record(
                workspaceId,
                actorId,
                AuditAction.REPOSITORY_CONNECTED,
                "REPOSITORY",
                saved.getId().toString(),
                Map.of("owner", owner, "name", name, "provider", saved.getProvider().name())
        );

        // Automatically trigger initial full scan upon repository connection
        if (scanService != null) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    scanService.triggerRepositoryScan(
                            workspaceId,
                            saved.getId(),
                            com.secretvault.repository.model.ScanType.FULL,
                            saved.getDefaultBranch(),
                            actorId
                    );
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(RepositoryService.class)
                            .warn("Automatic scan on connect failed for repo {}: {}", saved.getId(), e.getMessage());
                }
            });
        }

        return saved;
    }

    @Transactional
    public void disconnectRepository(UUID workspaceId, UUID repositoryId, UUID actorId) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_MANAGE);

        RepositoryEntity repo = repositoryRepo.findByIdAndWorkspaceId(repositoryId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        repositoryRepo.delete(repo);

        auditService.record(
                workspaceId,
                actorId,
                AuditAction.REPOSITORY_DISCONNECTED,
                "REPOSITORY",
                repo.getId().toString(),
                Map.of("owner", repo.getOwner(), "name", repo.getName(), "action", "DELETED")
        );
    }

    private RepositorySummaryDto toSummaryDto(RepositoryEntity repo) {
        List<RepoFindingStatus> activeStatuses = List.of(
                RepoFindingStatus.DETECTED, RepoFindingStatus.CONFIRMED, RepoFindingStatus.REMEDIATION_PENDING);

        List<SecretFinding> findings = findingRepo.findByWorkspaceIdAndRepositoryIdAndStatusIn(
                repo.getWorkspaceId(), repo.getId(), activeStatuses);

        long critical = findings.stream().filter(f -> f.getSeverity() == RepoFindingSeverity.CRITICAL).count();

        return new RepositorySummaryDto(
                repo.getId(),
                repo.getWorkspaceId(),
                repo.getProvider(),
                repo.getExternalRepositoryId(),
                repo.getOwner(),
                repo.getName(),
                repo.getDefaultBranch(),
                repo.getCloneUrl(),
                repo.getVisibility(),
                repo.getStatus(),
                repo.getLastScanAt(),
                repo.getLastSuccessfulScanAt(),
                repo.getLastCommitSha(),
                findings.size(),
                critical,
                repo.getCreatedAt(),
                repo.getUpdatedAt()
        );
    }
}
