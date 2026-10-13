package com.secretvault.repository;

import com.secretvault.common.exception.ApiException;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.repository.entity.RepositoryEntity;
import com.secretvault.repository.entity.RepositoryScan;
import com.secretvault.repository.entity.RepositorySecurityPolicy;
import com.secretvault.repository.entity.SecretFinding;
import com.secretvault.repository.model.*;
import com.secretvault.repository.repository.*;
import com.secretvault.repository.service.*;
import com.secretvault.repository.validator.LiveCredentialValidator;
import com.secretvault.rotation.service.RotationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Repo Security: Strict Workspace Multi-Tenancy & IDOR Defense Test Suite")
class WorkspaceIsolationAndIdorAttackTest {

    @Mock
    private RepositoryEntityRepository repositoryRepo;
    @Mock
    private RepositoryScanRepository scanRepo;
    @Mock
    private SecretFindingRepository findingRepo;
    @Mock
    private SecretFindingOccurrenceRepository occurrenceRepo;
    @Mock
    private FindingAllowlistRepository allowlistRepo;
    @Mock
    private FindingRemediationJobRepository remediationJobRepo;
    @Mock
    private RepositorySecurityPolicyRepository policyRepo;
    @Mock
    private EffectiveAccessService accessService;
    @Mock
    private AuditService auditService;
    @Mock
    private LiveCredentialValidator liveValidator;
    @Mock
    private RotationService rotationService;

    private RepositoryService repositoryService;
    private SecretFindingService findingService;
    private FindingRemediationService remediationService;
    private RepositoryPolicyService policyService;

    private final UUID workspaceA = UUID.randomUUID();
    private final UUID workspaceB = UUID.randomUUID();
    private final UUID userInWorkspaceA = UUID.randomUUID();
    private final UUID userInWorkspaceB = UUID.randomUUID();

    private final UUID repoIdA = UUID.randomUUID();
    private final UUID repoIdB = UUID.randomUUID();
    private final UUID scanIdA = UUID.randomUUID();
    private final UUID scanIdB = UUID.randomUUID();
    private final UUID findingIdA = UUID.randomUUID();
    private final UUID findingIdB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repositoryService = new RepositoryService(
                repositoryRepo,
                findingRepo,
                accessService,
                auditService
        );

        findingService = new SecretFindingService(
                findingRepo,
                occurrenceRepo,
                repositoryRepo,
                allowlistRepo,
                liveValidator,
                accessService,
                auditService
        );

        remediationService = new FindingRemediationService(
                findingRepo,
                remediationJobRepo,
                rotationService,
                findingService,
                accessService,
                auditService
        );

        policyService = new RepositoryPolicyService(
                policyRepo,
                repositoryRepo,
                accessService,
                auditService
        );
    }

    @Test
    @DisplayName("Cross-Workspace IDOR: Workspace B user querying Repository A by ID is strictly blocked")
    void testCrossWorkspaceRepositoryAccessBlocked() {
        // When Workspace B queries repoIdA, repositoryRepo returns empty for (repoIdA, workspaceB)
        when(repositoryRepo.findByIdAndWorkspaceId(repoIdA, workspaceB)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                repositoryService.getRepository(workspaceB, repoIdA, userInWorkspaceB)
        );

        assertTrue(ex.getMessage().contains("Repository not found"));
        verify(accessService).requireWorkspacePermission(userInWorkspaceB, workspaceB, AccessPermission.REPOSITORY_VIEW);
    }

    @Test
    @DisplayName("Cross-Workspace IDOR: Workspace B user querying Finding A by ID is strictly blocked")
    void testCrossWorkspaceFindingAccessBlocked() {
        when(findingRepo.findByIdAndWorkspaceId(findingIdA, workspaceB)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                findingService.getFinding(workspaceB, findingIdA, userInWorkspaceB)
        );

        assertTrue(ex.getMessage().contains("Secret finding not found"));
        verify(accessService).requireWorkspacePermission(userInWorkspaceB, workspaceB, AccessPermission.REPOSITORY_VIEW);
    }

    @Test
    @DisplayName("Cross-Workspace IDOR: Workspace B user attempting to remediate Finding A is strictly blocked")
    void testCrossWorkspaceRemediationBlocked() {
        when(findingRepo.findByIdAndWorkspaceId(findingIdA, workspaceB)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                remediationService.remediateFinding(
                        workspaceB, findingIdA, RemediationAction.MARK_FALSE_POSITIVE, "Attack", userInWorkspaceB)
        );

        assertTrue(ex.getMessage().contains("Secret finding not found"));
        verify(accessService).requireWorkspacePermission(userInWorkspaceB, workspaceB, AccessPermission.REPOSITORY_REMEDIATE);
    }

    @Test
    @DisplayName("Cross-Workspace IDOR: Workspace B user requesting policy for Repository A is strictly blocked")
    void testCrossWorkspacePolicyAccessBlocked() {
        when(repositoryRepo.findByIdAndWorkspaceId(repoIdA, workspaceB)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                policyService.getEffectivePolicy(workspaceB, repoIdA, userInWorkspaceB)
        );

        assertTrue(ex.getMessage().contains("Repository not found"));
    }

    @Test
    @DisplayName("Multi-Tenancy: Workspace A and Workspace B can independently connect the same repository name without conflicts")
    void testSameRepositoryNameInDifferentWorkspaces() {
        // Workspace A has not yet registered owner/repo1
        when(repositoryRepo.existsByWorkspaceIdAndOwnerAndName(workspaceA, "acme", "payment-service")).thenReturn(false);
        // Workspace B has not yet registered owner/repo1
        when(repositoryRepo.existsByWorkspaceIdAndOwnerAndName(workspaceB, "acme", "payment-service")).thenReturn(false);

        when(repositoryRepo.save(any(RepositoryEntity.class))).thenAnswer(inv -> {
            RepositoryEntity r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        RepositoryEntity repoA = repositoryService.connectRepository(
                workspaceA, RepositoryProvider.GITHUB, "101", "acme", "payment-service",
                "main", "https://github.com/acme/payment-service.git", RepositoryVisibility.PRIVATE, userInWorkspaceA
        );

        RepositoryEntity repoB = repositoryService.connectRepository(
                workspaceB, RepositoryProvider.GITHUB, "101", "acme", "payment-service",
                "main", "https://github.com/acme/payment-service.git", RepositoryVisibility.PRIVATE, userInWorkspaceB
        );

        assertNotNull(repoA);
        assertNotNull(repoB);
        assertEquals(workspaceA, repoA.getWorkspaceId());
        assertEquals(workspaceB, repoB.getWorkspaceId());
        assertEquals(repoA.getName(), repoB.getName());
    }

    @Test
    @DisplayName("Pagination Isolation: Listing repositories in Workspace A returns only Workspace A repositories")
    void testRepositoryListPaginationIsolation() {
        RepositoryEntity repo1 = new RepositoryEntity();
        repo1.setId(repoIdA);
        repo1.setWorkspaceId(workspaceA);
        repo1.setName("repo-a");
        repo1.setOwner("owner-a");
        repo1.setProvider(RepositoryProvider.GITHUB);
        repo1.setStatus(RepositoryStatus.ACTIVE);

        when(repositoryRepo.findByWorkspaceId(eq(workspaceA), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(repo1)));

        Page<RepositoryService.RepositorySummaryDto> result = repositoryService.listRepositories(
                workspaceA, userInWorkspaceA, PageRequest.of(0, 20));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(workspaceA, result.getContent().get(0).workspaceId());
    }
}
