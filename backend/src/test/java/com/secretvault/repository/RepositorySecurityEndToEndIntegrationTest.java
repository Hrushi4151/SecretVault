package com.secretvault.repository;

import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.repository.engine.*;
import com.secretvault.repository.entity.FindingRemediationJob;
import com.secretvault.repository.entity.RepositoryEntity;
import com.secretvault.repository.entity.RepositoryScan;
import com.secretvault.repository.entity.SecretFinding;
import com.secretvault.repository.model.*;
import com.secretvault.repository.repository.*;
import com.secretvault.repository.service.*;
import com.secretvault.repository.validator.LiveCredentialValidator;
import com.secretvault.rotation.dto.RotationDtos.TriggerRotationRequest;
import com.secretvault.rotation.service.RotationService;
import com.secretvault.security.finding.service.SecurityFindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Repo Security: Full Production Lifecycle & Security Center Integration")
class RepositorySecurityEndToEndIntegrationTest {

    @Mock
    private RepositoryEntityRepository repoRepository;
    @Mock
    private RepositoryScanRepository scanRepository;
    @Mock
    private SecretFindingRepository findingRepository;
    @Mock
    private SecretFindingOccurrenceRepository occurrenceRepository;
    @Mock
    private FindingAllowlistRepository allowlistRepository;
    @Mock
    private FindingRemediationJobRepository remediationJobRepository;
    @Mock
    private RepositorySecurityPolicyRepository policyRepository;
    @Mock
    private SecretInventoryCorrelator correlator;
    @Mock
    private SecurityFindingService securityFindingService;
    @Mock
    private EffectiveAccessService accessService;
    @Mock
    private AuditService auditService;
    @Mock
    private LiveCredentialValidator liveValidator;
    @Mock
    private RotationService rotationService;

    private RepositoryScanService scanService;
    private SecretFindingService findingService;
    private FindingRemediationService remediationService;
    private RepositoryService repositoryService;
    private RepositoryPolicyService policyService;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID repositoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        EntropyEvaluator entropyEvaluator = new EntropyEvaluator();
        SecretFingerprinter fingerprinter = new SecretFingerprinter();
        ContextAnalyzer contextAnalyzer = new ContextAnalyzer();
        RegexSecretDetector detector = new RegexSecretDetector(fingerprinter, entropyEvaluator, contextAnalyzer);
        FileDiscoveryEngine fileEngine = new FileDiscoveryEngine();
        GitProcessExecutor gitExecutor = new GitProcessExecutor();
        GitRepositoryScanner gitScanner = new GitRepositoryScanner(gitExecutor, fileEngine, detector);
        PathTraversalGuard pathGuard = new PathTraversalGuard();
        ArchiveScanner archiveScanner = new ArchiveScanner(pathGuard);
        GitCloneUrlValidator urlValidator = new GitCloneUrlValidator();
        RepositorySourceAdapter sourceAdapter = new RepositorySourceAdapter(gitExecutor, archiveScanner, pathGuard, urlValidator);

        policyService = new RepositoryPolicyService(
                policyRepository,
                repoRepository,
                accessService,
                auditService
        );

        findingService = new SecretFindingService(
                findingRepository,
                occurrenceRepository,
                repoRepository,
                allowlistRepository,
                liveValidator,
                accessService,
                auditService
        );

        scanService = new RepositoryScanService(
                scanRepository,
                repoRepository,
                findingRepository,
                occurrenceRepository,
                allowlistRepository,
                policyService,
                sourceAdapter,
                gitScanner,
                correlator,
                securityFindingService,
                accessService,
                auditService
        );

        repositoryService = new RepositoryService(
                repoRepository,
                findingRepository,
                accessService,
                auditService
        );

        remediationService = new FindingRemediationService(
                findingRepository,
                remediationJobRepository,
                rotationService,
                findingService,
                accessService,
                auditService
        );
    }

    @Test
    @DisplayName("End-to-End: Scanning test fixture directory detects secrets, creates findings, and mirrors to Security Center")
    void testEndToEndLocalDirectoryScanAndSecurityCenterMirror() {
        File fixtureDir = new File("src/test/resources/test-fixtures/repository-security");
        assertTrue(fixtureDir.exists() && fixtureDir.isDirectory(), "Test fixtures directory must exist");

        RepositoryEntity repo = new RepositoryEntity();
        repo.setId(repositoryId);
        repo.setWorkspaceId(workspaceId);
        repo.setOwner("secretvault");
        repo.setName("sample-leaks");
        repo.setDefaultBranch("main");

        when(repoRepository.findByIdAndWorkspaceId(repositoryId, workspaceId)).thenReturn(Optional.of(repo));
        when(scanRepository.save(any(RepositoryScan.class))).thenAnswer(inv -> {
            RepositoryScan s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        when(scanRepository.findById(any())).thenAnswer(inv -> Optional.of(new RepositoryScan()));
        when(allowlistRepository.findActiveAllowlists(any(), any(), any())).thenReturn(List.of());
        when(correlator.correlateByFingerprint(anyString())).thenReturn(SecretInventoryCorrelator.CorrelationResult.noMatch());
        when(findingRepository.save(any(SecretFinding.class))).thenAnswer(inv -> {
            SecretFinding f = inv.getArgument(0);
            if (f.getId() == null) f.setId(UUID.randomUUID());
            return f;
        });

        RepositoryScan scan = scanService.executeLocalDirectoryScan(
                workspaceId, repositoryId, fixtureDir.getAbsolutePath(), false, actorId
        );

        assertNotNull(scan);

        // Verify findings were saved
        ArgumentCaptor<SecretFinding> findingCaptor = ArgumentCaptor.forClass(SecretFinding.class);
        verify(findingRepository, atLeastOnce()).save(findingCaptor.capture());

        List<SecretFinding> capturedFindings = findingCaptor.getAllValues();
        assertFalse(capturedFindings.isEmpty());

        // Verify Zero Plaintext Invariant on all captured findings
        for (SecretFinding f : capturedFindings) {
            assertNotNull(f.getMaskedValue());
            assertNotNull(f.getFingerprint());
            assertEquals(64, f.getFingerprint().length());
            assertFalse(f.getMaskedValue().contains("AKIAIOSFODNN7EXAMP12"));
            assertFalse(f.getMaskedValue().contains("ghp_1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R"));
            assertFalse(f.getMaskedValue().contains("sk_live_51Mzxyz123456789012345678"));
        }

        // Verify Security Center draft creation
        verify(securityFindingService, atLeastOnce()).upsertFinding(any());
    }
}
