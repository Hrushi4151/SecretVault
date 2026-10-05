package com.secretvault.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.context.AiContextOrchestrator;
import com.secretvault.ai.dto.AiChatRequest;
import com.secretvault.ai.dto.AiChatResponse;
import com.secretvault.ai.knowledge.AiPlatformKnowledgeService;
import com.secretvault.ai.provider.DeterministicOfflineLlmProvider;
import com.secretvault.ai.provider.LlmProviderRegistry;
import com.secretvault.ai.security.AiContextSanitizer;
import com.secretvault.ai.security.AiRateLimiterAndBudgetEnforcer;
import com.secretvault.ai.security.AiSafetyGuardrailValidator;
import com.secretvault.ai.security.AiSecretFirewall;
import com.secretvault.ai.tool.AiToolExecutor;
import com.secretvault.ai.tool.AiToolRegistry;
import com.secretvault.ai.tool.domain.*;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;
import com.secretvault.security.finding.repository.SecurityFindingRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AiArbitraryQuestionsAcceptanceTest {

    private AiContextOrchestrator orchestrator;
    private WorkspaceRepository workspaceRepository;
    private ProjectRepository projectRepository;
    private EnvironmentRepository environmentRepository;
    private SecretRepository secretRepository;
    private SecretVersionRepository versionRepository;
    private SecurityFindingRepository findingRepository;

    private UUID workspaceId;
    private UUID userId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;

    @BeforeEach
    void setUp() {
        workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        projectRepository = Mockito.mock(ProjectRepository.class);
        environmentRepository = Mockito.mock(EnvironmentRepository.class);
        secretRepository = Mockito.mock(SecretRepository.class);
        versionRepository = Mockito.mock(SecretVersionRepository.class);
        findingRepository = Mockito.mock(SecurityFindingRepository.class);

        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();

        // Setup mock data
        Workspace ws = new Workspace(UUID.randomUUID(), "Payments Core Workspace", "payments-core", false);
        ws.setId(workspaceId);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(ws));

        Project proj = new Project(workspaceId, "Billing Engine", "billing-engine", "Core payment processor");
        proj.setId(projectId);
        when(projectRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(proj));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(proj));

        Environment env = new Environment(projectId, "Production", "prod", EnvType.PRODUCTION);
        env.setId(environmentId);
        when(environmentRepository.findByProjectId(projectId)).thenReturn(List.of(env));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(env));

        Secret sec = new Secret();
        sec.setId(secretId);
        sec.setEnvironmentId(environmentId);
        sec.setName("STRIPE_API_KEY");
        sec.setCurrentVersionNumber(3);
        when(secretRepository.findByEnvironmentId(environmentId)).thenReturn(List.of(sec));
        when(secretRepository.findByEnvironmentIdAndName(environmentId, "STRIPE_API_KEY")).thenReturn(Optional.of(sec));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(sec));

        SecurityFinding finding = new SecurityFinding(workspaceId, "Overdue Secret Rotation", FindingSeverity.HIGH, "Key has not rotated in 120 days");
        finding.setId(UUID.randomUUID());
        when(findingRepository.findByWorkspaceIdAndStatusIn(eq(workspaceId), any())).thenReturn(List.of(finding));

        ObjectMapper objectMapper = new ObjectMapper();
        AiSecretFirewall secretFirewall = new AiSecretFirewall();
        AiContextSanitizer sanitizer = new AiContextSanitizer();
        AiSafetyGuardrailValidator guardrailValidator = new AiSafetyGuardrailValidator(sanitizer);
        AiRateLimiterAndBudgetEnforcer budgetEnforcer = Mockito.mock(AiRateLimiterAndBudgetEnforcer.class);
        AiPlatformKnowledgeService knowledgeService = new AiPlatformKnowledgeService();

        // Register tools
        List<com.secretvault.ai.tool.AiTool> tools = List.of(
                new WorkspaceTools.WorkspaceGetTool(workspaceRepository, objectMapper),
                new ProjectTools.ProjectListTool(projectRepository, objectMapper),
                new ProjectTools.ProjectGetTool(projectRepository, objectMapper),
                new EnvironmentTools.EnvironmentListTool(projectRepository, environmentRepository, objectMapper),
                new SecretMetadataTools.SecretListMetadataTool(projectRepository, environmentRepository, secretRepository, objectMapper),
                new SecretMetadataTools.SecretGetMetadataTool(projectRepository, environmentRepository, secretRepository, objectMapper),
                new SecretMetadataTools.SecretBlastRadiusTool(projectRepository, environmentRepository, secretRepository, null, objectMapper),
                new SecurityTools.SecurityFindingsTool(findingRepository, objectMapper),
                new SecurityTools.SecurityPostureTool(findingRepository, objectMapper),
                new KnowledgeTools.KnowledgeSearchTool(knowledgeService, objectMapper)
        );

        AiToolRegistry toolRegistry = new AiToolRegistry(tools);
        AiToolExecutor toolExecutor = new AiToolExecutor(toolRegistry, secretFirewall, null, objectMapper);
        DeterministicOfflineLlmProvider offline = new DeterministicOfflineLlmProvider();
        LlmProviderRegistry registry = new LlmProviderRegistry(offline, "DETERMINISTIC_OFFLINE");

        orchestrator = new AiContextOrchestrator(
                registry,
                toolRegistry,
                toolExecutor,
                secretFirewall,
                sanitizer,
                guardrailValidator,
                budgetEnforcer,
                knowledgeService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Scenario 1: User asks arbitrary workspace question ('Explain my workspace health')")
    void testArbitraryWorkspaceQuestion() {
        AiChatRequest req = new AiChatRequest("Explain my workspace health and overall architecture", "COPILOT_GENERAL", null, null, null, null);
        AiChatResponse resp = orchestrator.orchestrate(workspaceId, userId, "DEVELOPER", Set.of(), req, List.of(), null);

        assertNotNull(resp);
        assertNotNull(resp.responseText());
        assertFalse(resp.sanitizedTelemetryEvidence().isEmpty());
    }

    @Test
    @DisplayName("Scenario 2: User asks project question ('What projects exist here?')")
    void testArbitraryProjectQuestion() {
        AiChatRequest req = new AiChatRequest("Which projects exist in this workspace?", "COPILOT_GENERAL", null, null, null, null);
        AiChatResponse resp = orchestrator.orchestrate(workspaceId, userId, "DEVELOPER", Set.of(), req, List.of(), null);

        assertNotNull(resp);
        assertTrue(resp.confidenceScore() >= 0.7);
    }

    @Test
    @DisplayName("Scenario 3: User asks about secret metadata without revealing plaintext")
    void testSecretMetadataQuestion() {
        AiChatRequest req = new AiChatRequest("Tell me about STRIPE_API_KEY metadata and version", "SECRET_HEALTH", null, null, null, null);
        AiChatResponse resp = orchestrator.orchestrate(workspaceId, userId, "DEVELOPER", Set.of(), req, List.of(), null);

        assertNotNull(resp);
        assertFalse(resp.responseText().contains("sk_live_"));
    }

    @Test
    @DisplayName("Scenario 4: User asks for blast radius analysis on production secret")
    void testBlastRadiusQuestion() {
        AiChatRequest req = new AiChatRequest("What is the blast radius if I rotate STRIPE_API_KEY in production?", "BLAST_RADIUS", null, null, null, null);
        AiChatResponse resp = orchestrator.orchestrate(workspaceId, userId, "DEVELOPER", Set.of(), req, List.of(), null);

        assertNotNull(resp);
        assertTrue(resp.responseText().contains("dual-version") || resp.responseText().contains("tolerance") || resp.responseText().contains("rollover") || !resp.recommendations().isEmpty());
    }

    @Test
    @DisplayName("Scenario 5: User asks why security posture decreased and how to fix it")
    void testSecurityPostureQuestion() {
        AiChatRequest req = new AiChatRequest("Why is our security posture score degraded?", "SECURITY_POSTURE", null, null, null, null);
        AiChatResponse resp = orchestrator.orchestrate(workspaceId, userId, "DEVELOPER", Set.of(), req, List.of(), null);

        assertNotNull(resp);
        assertFalse(resp.sanitizedTelemetryEvidence().isEmpty());
    }

    @Test
    @DisplayName("Scenario 6: User asks conceptual question about SecretVault KMS Envelope encryption")
    void testPlatformKnowledgeQuestion() {
        AiChatRequest req = new AiChatRequest("How does SecretVault KMS Envelope encryption work under the hood?", "HELP", null, null, null, null);
        AiChatResponse resp = orchestrator.orchestrate(workspaceId, userId, "DEVELOPER", Set.of(), req, List.of(), null);

        assertNotNull(resp);
        assertNotNull(resp.responseText());
    }

    @Test
    @DisplayName("Scenario 7: Completely unanticipated question ('How would a solar flare affect my synchronization leases?')")
    void testUnanticipatedQuestionHandledGracefully() {
        AiChatRequest req = new AiChatRequest("How would unexpected network partition or hardware fault affect my synchronization leases?", "COPILOT_GENERAL", null, null, null, null);
        AiChatResponse resp = orchestrator.orchestrate(workspaceId, userId, "DEVELOPER", Set.of(), req, List.of(), null);

        assertNotNull(resp);
        assertNotNull(resp.responseText());
        assertFalse(resp.responseText().isBlank());
    }
}
