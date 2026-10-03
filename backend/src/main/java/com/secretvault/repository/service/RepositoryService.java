package com.secretvault.repository.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RepositoryService {

    private final RepositoryEntityRepository repositoryRepo;
    private final SecretFindingRepository findingRepo;
    private final EffectiveAccessService accessService;
    private final AuditService auditService;

    public RepositoryService(
            RepositoryEntityRepository repositoryRepo,
            SecretFindingRepository findingRepo,
            EffectiveAccessService accessService,
            AuditService auditService) {
        this.repositoryRepo = repositoryRepo;
        this.findingRepo = findingRepo;
        this.accessService = accessService;
        this.auditService = auditService;
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

        return saved;
    }

    @Transactional
    public void disconnectRepository(UUID workspaceId, UUID repositoryId, UUID actorId) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_MANAGE);

        RepositoryEntity repo = repositoryRepo.findByIdAndWorkspaceId(repositoryId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        repo.setStatus(RepositoryStatus.ARCHIVED);
        repo.setUpdatedAt(Instant.now());
        repositoryRepo.save(repo);

        auditService.record(
                workspaceId,
                actorId,
                AuditAction.REPOSITORY_DISCONNECTED,
                "REPOSITORY",
                repo.getId().toString(),
                Map.of("owner", repo.getOwner(), "name", repo.getName())
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
