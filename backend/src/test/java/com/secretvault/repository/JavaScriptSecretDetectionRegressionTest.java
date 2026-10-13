package com.secretvault.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.repository.controller.GitHubRepositoryWebhookController;
import com.secretvault.repository.engine.*;
import com.secretvault.repository.entity.RepositoryEntity;
import com.secretvault.repository.entity.RepositoryScan;
import com.secretvault.repository.entity.SecretFinding;
import com.secretvault.repository.entity.SecretFindingOccurrence;
import com.secretvault.repository.model.*;
import com.secretvault.repository.repository.*;
import com.secretvault.repository.service.RepositoryCommitSyncService;
import com.secretvault.repository.service.RepositoryPolicyService;
import com.secretvault.repository.service.RepositoryScanService;
import com.secretvault.repository.service.SecretFindingService;
import com.secretvault.repository.service.SecretInventoryCorrelator;
import com.secretvault.repository.validator.LiveCredentialValidator;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.service.SecurityFindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecretVault — Critical JavaScript Secret Detection Regression Test Suite")
class JavaScriptSecretDetectionRegressionTest {

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
    private RepositorySourceAdapter sourceAdapter;

    private EntropyEvaluator entropyEvaluator;
    private SecretFingerprinter fingerprinter;
    private ContextAnalyzer contextAnalyzer;
    private RegexSecretDetector secretDetector;
    private GitProcessExecutor gitExecutor;
    private FileDiscoveryEngine fileDiscoveryEngine;
    private GitRepositoryScanner gitScanner;
    private RepositoryPolicyService policyService;
    private RepositoryScanService scanService;
    private SecretFindingService secretFindingService;
    private RepositoryCommitSyncService syncService;
    private GitHubRepositoryWebhookController webhookController;
    private ObjectMapper objectMapper;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID repositoryId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        entropyEvaluator = new EntropyEvaluator();
        fingerprinter = new SecretFingerprinter();
        contextAnalyzer = new ContextAnalyzer();
        secretDetector = new RegexSecretDetector(fingerprinter, entropyEvaluator, contextAnalyzer);
        gitExecutor = new GitProcessExecutor();
        fileDiscoveryEngine = new FileDiscoveryEngine();
        gitScanner = new GitRepositoryScanner(gitExecutor, fileDiscoveryEngine, secretDetector);

        policyService = new RepositoryPolicyService(
                policyRepository,
                repoRepository,
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

        secretFindingService = new SecretFindingService(
                findingRepository,
                occurrenceRepository,
                repoRepository,
                allowlistRepository,
                liveValidator,
                accessService,
                auditService
        );

        objectMapper = new ObjectMapper();
        syncService = new RepositoryCommitSyncService(repoRepository, scanService, objectMapper);
        webhookController = new GitHubRepositoryWebhookController(repoRepository, scanService, syncService, objectMapper);
    }

    @Test
    @DisplayName("Regression Test: Three synthetic JavaScript assignments are reliably detected")
    void testThreeSyntheticAssignmentsDetected() {
        String jsContent = """
                // API Client Configuration
                const AI_GEMINI_API_KEY = "FAKE_TEST_CREDENTIAL_7a9f3c2e";
                const API_SECRET = "FAKE_TEST_SECRET_8b2d4e6f";
                const DB_PASSWORD = "FAKE_TEST_PASSWORD_5c7a1d";
                """;

        List<SecretDetectionResult> findings = secretDetector.scanContent(
                jsContent, "src/config/api.js", "a1b2c3d4e5", "main", "dev-engineer"
        );

        assertEquals(3, findings.size(), "All three synthetic JS assignments must be detected as findings");

        // Verify finding 1: AI_GEMINI_API_KEY
        SecretDetectionResult geminiFinding = findings.stream()
                .filter(f -> f.secretType() == SecretType.GENERIC_API_KEY && f.lineNumber() == 2)
                .findFirst()
                .orElse(null);
        assertNotNull(geminiFinding, "AI_GEMINI_API_KEY must be detected on line 2");
        assertEquals(RepoFindingSeverity.HIGH, geminiFinding.severity());
        assertTrue(geminiFinding.confidence() == RepoFindingConfidence.HIGH || geminiFinding.confidence() == RepoFindingConfidence.VERY_HIGH);
        assertEquals("src/config/api.js", geminiFinding.filePath());
        assertFalse(geminiFinding.maskedValue().contains("FAKE_TEST_CREDENTIAL_7a9f3c2e"));
        assertTrue(geminiFinding.maskedValue().contains("************"));
        assertEquals(64, geminiFinding.fingerprint().length());

        // Verify finding 2: API_SECRET
        SecretDetectionResult secretFinding = findings.stream()
                .filter(f -> f.lineNumber() == 3)
                .findFirst()
                .orElse(null);
        assertNotNull(secretFinding, "API_SECRET must be detected on line 3");
        assertEquals(RepoFindingSeverity.HIGH, secretFinding.severity());
        assertTrue(secretFinding.confidence() == RepoFindingConfidence.HIGH || secretFinding.confidence() == RepoFindingConfidence.VERY_HIGH);
        assertFalse(secretFinding.maskedValue().contains("FAKE_TEST_SECRET_8b2d4e6f"));

        // Verify finding 3: DB_PASSWORD
        SecretDetectionResult passwordFinding = findings.stream()
                .filter(f -> (f.secretType() == SecretType.DATABASE_PASSWORD || f.secretType() == SecretType.GENERIC_PASSWORD) && f.lineNumber() == 4)
                .findFirst()
                .orElse(null);
        assertNotNull(passwordFinding, "DB_PASSWORD must be detected on line 4");
        assertEquals(RepoFindingSeverity.HIGH, passwordFinding.severity());
        assertFalse(passwordFinding.maskedValue().contains("FAKE_TEST_PASSWORD_5c7a1d"));
    }

    @Test
    @DisplayName("Regression Test: Valid provider tokens, access tokens, and credential-bearing URLs are detected")
    void testValidProviderTokensAndCredentialBearingUrls() {
        String jsContent = """
                export const AWS_ACCESS_KEY = "AKIAIOSFODNN7EXAMP12";
                export const GITHUB_TOKEN = "ghp_1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R";
                export const STRIPE_KEY = "sk_live_51Mzxyz123456789012345678";
                export const SLACK_TOKEN = "xoxb-123456789012-123456789012-abcdefghijklmnopqrstuvwx";
                export const ACCESS_TOKEN = "FAKE_TEST_TOKEN_3a7b9c1d5e2f";
                export const DATABASE_URL = "postgres://db_admin:SecretPass123%21@postgres.prod.internal:5432/secrets_db";
                """;

        List<SecretDetectionResult> findings = secretDetector.scanContent(
                jsContent, "src/security/tokens.ts", "sha123", "main", "alice"
        );

        assertTrue(findings.size() >= 6, "Must detect all provider tokens and connection strings");

        assertTrue(findings.stream().anyMatch(f -> f.secretType() == SecretType.AWS_ACCESS_KEY && f.severity() == RepoFindingSeverity.HIGH));
        assertTrue(findings.stream().anyMatch(f -> f.secretType() == SecretType.GITHUB_TOKEN && f.severity() == RepoFindingSeverity.CRITICAL));
        assertTrue(findings.stream().anyMatch(f -> f.secretType() == SecretType.STRIPE_KEY && f.severity() == RepoFindingSeverity.CRITICAL));
        assertTrue(findings.stream().anyMatch(f -> f.secretType() == SecretType.SLACK_TOKEN && f.severity() == RepoFindingSeverity.HIGH));
        assertTrue(findings.stream().anyMatch(f -> f.secretType() == SecretType.GENERIC_TOKEN && f.severity() == RepoFindingSeverity.HIGH));
        assertTrue(findings.stream().anyMatch(f -> f.secretType() == SecretType.DATABASE_CONNECTION_STRING && f.severity() == RepoFindingSeverity.CRITICAL));
    }

    @Test
    @DisplayName("Regression Test: Placeholders and harmless configuration strings are safely ignored")
    void testPlaceholdersAndHarmlessStringsIgnored() {
        String harmlessContent = """
                // Safe placeholders and templates
                const API_KEY = "YOUR_API_KEY_HERE";
                const API_SECRET = "REPLACE_ME_WITH_REAL_SECRET";
                const DB_PASSWORD = "dummy-password-sample";
                const ACCESS_TOKEN = "CHANGEME";
                const TOKEN = "0000000000000000";
                
                // Normal non-secret code and configuration
                const USERNAME = "administrator";
                const API_URL = "https://api.github.com/v1";
                const DB_NAME = "customer_database";
                const APP_STATUS = "ACTIVE";
                const TIMEOUT_MS = "30000";
                const IS_PRODUCTION = true;
                """;

        List<SecretDetectionResult> findings = secretDetector.scanContent(
                harmlessContent, "src/config/defaults.js", "sha456", "main", "bob"
        );

        assertTrue(findings.isEmpty(), "Placeholders and harmless ordinary strings must produce 0 findings");
    }

    @Test
    @DisplayName("Regression Test: Documentation examples and comment placeholders produce no false alarms")
    void testCommentsAndDocumentationExamples() {
        String docContent = """
                /**
                 * Set your credentials in .env file:
                 * AI_GEMINI_API_KEY=YOUR_API_KEY_HERE
                 * DB_PASSWORD=dummy_password
                 * DATABASE_URL=postgres://user:password@localhost:5432/test
                 */
                function initClient() {
                    return { initialized: true };
                }
                """;

        List<SecretDetectionResult> findings = secretDetector.scanContent(
                docContent, "docs/setup.js", "sha789", "main", "docs-team"
        );

        assertTrue(findings.isEmpty(), "Documentation with placeholders must produce 0 findings");
    }

    @Test
    @DisplayName("Regression Test: Incremental scan detects secrets in newly added commits")
    void testIncrementalJavaScriptScan() {
        File fixtureDir = new File("src/test/resources/test-fixtures/repository-security");
        assertTrue(fixtureDir.exists());

        RepositoryEntity repo = new RepositoryEntity();
        repo.setId(repositoryId);
        repo.setWorkspaceId(workspaceId);
        repo.setOwner("secretvault");
        repo.setName("repo-incremental");
        repo.setDefaultBranch("main");
        repo.setLastCommitSha("c111111111111111111111111111111111111111");

        when(repoRepository.findByIdAndWorkspaceId(repositoryId, workspaceId)).thenReturn(Optional.of(repo));
        when(scanRepository.save(any(RepositoryScan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(allowlistRepository.findActiveAllowlists(any(), any(), any())).thenReturn(List.of());
        when(correlator.correlateByFingerprint(anyString())).thenReturn(SecretInventoryCorrelator.CorrelationResult.noMatch());
        when(sourceAdapter.acquireLocalDirectory(anyString())).thenReturn(new RepositorySourceAdapter.SourceContext(fixtureDir.toPath(), false));
        when(findingRepository.save(any(SecretFinding.class))).thenAnswer(inv -> {
            SecretFinding f = inv.getArgument(0);
            if (f.getId() == null) f.setId(UUID.randomUUID());
            return f;
        });

        RepositoryScan scan = scanService.executeLocalDirectoryScan(
                workspaceId, repositoryId, fixtureDir.getAbsolutePath(), false, actorId
        );

        assertNotNull(scan);
        verify(findingRepository, atLeastOnce()).save(any(SecretFinding.class));
        verify(occurrenceRepository, atLeastOnce()).save(any(SecretFindingOccurrence.class));
    }

    @Test
    @DisplayName("Regression Test: Finding creation, deduplication, and zero-plaintext redaction")
    void testFindingCreationDeduplicationAndRedaction() {
        String repeatedSecretJs = """
                const API_SECRET = "FAKE_TEST_SECRET_8b2d4e6f";
                const SAME_SECRET = "FAKE_TEST_SECRET_8b2d4e6f";
                """;

        List<SecretDetectionResult> results = secretDetector.scanContent(
                repeatedSecretJs, "src/auth.js", "commit999", "feature/auth", "security-audit"
        );

        assertFalse(results.isEmpty());
        // Both lines have same fingerprint
        String expectedFingerprint = fingerprinter.computeFingerprint("FAKE_TEST_SECRET_8b2d4e6f");
        for (SecretDetectionResult res : results) {
            assertEquals(expectedFingerprint, res.fingerprint());
            assertEquals(64, res.fingerprint().length());
            assertFalse(res.maskedValue().contains("FAKE_TEST_SECRET_8b2d4e6f"), "Masked value must not leak secret");
            assertTrue(res.maskedValue().contains("************"), "Masked value must contain mask asterisks");
        }
    }

    @Test
    @DisplayName("Regression Test: Strict workspace multi-tenancy and finding isolation")
    void testWorkspaceIsolation() {
        UUID otherWorkspaceId = UUID.randomUUID();
        UUID otherFindingId = UUID.randomUUID();

        when(findingRepository.findByIdAndWorkspaceId(otherFindingId, otherWorkspaceId))
                .thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                secretFindingService.getFinding(otherWorkspaceId, otherFindingId, actorId)
        );

        assertTrue(ex.getMessage().contains("Secret finding not found"));
        verify(accessService).requireWorkspacePermission(actorId, otherWorkspaceId, AccessPermission.REPOSITORY_VIEW);
    }

    @Test
    @DisplayName("Regression Test: GitHub push webhook triggers scan and mirrors finding to Security Center")
    void testPushedCommitTriggersScanAndSecurityCenterMirror() {
        File fixtureDir = new File("src/test/resources/test-fixtures/repository-security");
        assertTrue(fixtureDir.exists());

        RepositoryEntity repo = new RepositoryEntity();
        repo.setId(repositoryId);
        repo.setWorkspaceId(workspaceId);
        repo.setOwner("Hrushi4151");
        repo.setName("SecretVault");
        repo.setDefaultBranch("main");
        repo.setCloneUrl("https://github.com/Hrushi4151/SecretVault.git");

        when(repoRepository.findByOwnerIgnoreCaseAndNameIgnoreCase("Hrushi4151", "SecretVault"))
                .thenReturn(List.of(repo));
        when(repoRepository.findByIdAndWorkspaceId(repositoryId, workspaceId))
                .thenReturn(Optional.of(repo));
        List<RepositoryScan> queuedScans = new ArrayList<>();
        when(scanRepository.save(any(RepositoryScan.class))).thenAnswer(inv -> {
            RepositoryScan s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            queuedScans.add(s);
            return s;
        });
        when(scanRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return queuedScans.stream().filter(s -> s.getId().equals(id)).findFirst()
                    .or(() -> Optional.of(new RepositoryScan()));
        });
        when(scanRepository.findByStatusOrderByCreatedAtAsc(ScanStatus.QUEUED)).thenAnswer(inv ->
                queuedScans.stream().filter(s -> s.getStatus() == ScanStatus.QUEUED).toList()
        );
        when(allowlistRepository.findActiveAllowlists(any(), any(), any())).thenReturn(List.of());
        when(correlator.correlateByFingerprint(anyString())).thenReturn(SecretInventoryCorrelator.CorrelationResult.noMatch());
        when(sourceAdapter.cloneGitRepository(anyString(), any())).thenReturn(new RepositorySourceAdapter.SourceContext(fixtureDir.toPath(), false));
        when(findingRepository.save(any(SecretFinding.class))).thenAnswer(inv -> {
            SecretFinding f = inv.getArgument(0);
            if (f.getId() == null) f.setId(UUID.randomUUID());
            return f;
        });

        String pushPayload = """
                {
                    "ref": "refs/heads/main",
                    "after": "pushcommit99999999999999999999999999999999999999",
                    "repository": {
                        "name": "SecretVault",
                        "owner": {
                            "login": "Hrushi4151"
                        },
                        "clone_url": "https://github.com/Hrushi4151/SecretVault.git"
                    }
                }
                """;

        // Fast webhook acknowledgement: delivery is accepted and durable scan is queued (< 50ms)
        ResponseEntity<Map<String, Object>> response = webhookController.handleGitHubWebhook("push", null, pushPayload);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("pushcommit99999999999999999999999999999999999999", response.getBody().get("commitSha"));
        assertNotNull(repo.getLastCommitSha());

        // Eventual scan execution: background worker processes queued scan
        scanService.processQueuedScans();

        // Verify findings were saved and mirrored to Security Center
        verify(findingRepository, atLeastOnce()).save(any(SecretFinding.class));
        verify(securityFindingService, atLeastOnce()).upsertFinding(any(SecurityFindingDraft.class));
    }
}
