package com.secretvault.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.context.AiContextOrchestrator;
import com.secretvault.ai.dto.AiChatRequest;
import com.secretvault.ai.dto.AiChatResponse;
import com.secretvault.ai.knowledge.AiPlatformKnowledgeService;
import com.secretvault.ai.security.AiContextSanitizer;
import com.secretvault.ai.security.AiRateLimiterAndBudgetEnforcer;
import com.secretvault.ai.security.AiSafetyGuardrailValidator;
import com.secretvault.ai.security.AiSecretFirewall;
import com.secretvault.ai.tool.AiToolExecutor;
import com.secretvault.ai.tool.AiToolRegistry;
import com.secretvault.ai.tool.domain.UserTools;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.invitation.dto.UserInvitationItemResponse;
import com.secretvault.workspace.invitation.dto.UserInvitationsListResponse;
import com.secretvault.workspace.invitation.entity.InvitationStatus;
import com.secretvault.workspace.invitation.service.WorkspaceInvitationService;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Live Google Gemini 3.5 Flash Lite acceptance test suite.
 * Executes real Google Generative Language API calls against live Gemini models
 * when AI_GEMINI_API_KEY is configured in the environment.
 */
@EnabledIfEnvironmentVariable(named = "AI_GEMINI_API_KEY", matches = ".+")
class GeminiLiveAcceptanceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WorkspaceInvitationService invitationService;
    private UserRepository userRepository;
    private WorkspaceMembershipRepository membershipRepository;
    private WorkspaceRepository workspaceRepository;

    private AiToolRegistry toolRegistry;
    private AiToolExecutor toolExecutor;
    private AiSecretFirewall secretFirewall;
    private AiContextSanitizer sanitizer;
    private AiSafetyGuardrailValidator guardrailValidator;
    private AiRateLimiterAndBudgetEnforcer budgetEnforcer;
    private AiPlatformKnowledgeService knowledgeService;
    private LlmProviderRegistry providerRegistry;
    private AiContextOrchestrator orchestrator;

    private UUID workspaceId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        invitationService = Mockito.mock(WorkspaceInvitationService.class);
        userRepository = Mockito.mock(UserRepository.class);
        membershipRepository = Mockito.mock(WorkspaceMembershipRepository.class);
        workspaceRepository = Mockito.mock(WorkspaceRepository.class);

        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();

        String apiKey = System.getenv("AI_GEMINI_API_KEY");
        String modelName = System.getenv("AI_GEMINI_MODEL") != null ? System.getenv("AI_GEMINI_MODEL") : "gemini-3.5-flash-lite";

        GeminiLlmProvider geminiProvider = new GeminiLlmProvider(
                "https://generativelanguage.googleapis.com",
                modelName,
                apiKey,
                30,
                0.1,
                1024,
                objectMapper
        );

        DeterministicOfflineLlmProvider offline = new DeterministicOfflineLlmProvider();
        providerRegistry = new LlmProviderRegistry(offline, null, geminiProvider, null, "GEMINI", "NONE");

        UserTools.UserGetCurrentTool userGetTool = new UserTools.UserGetCurrentTool(
                userRepository, membershipRepository, workspaceRepository, objectMapper
        );
        UserTools.UserInvitationsTool userInvTool = new UserTools.UserInvitationsTool(invitationService, objectMapper);
        UserTools.InvitationListTool invListTool = new UserTools.InvitationListTool(userInvTool);

        secretFirewall = new AiSecretFirewall();
        toolRegistry = new AiToolRegistry(List.of(userGetTool, userInvTool, invListTool));
        toolExecutor = new AiToolExecutor(toolRegistry, secretFirewall, null, objectMapper);
        sanitizer = new AiContextSanitizer();
        guardrailValidator = new AiSafetyGuardrailValidator();
        budgetEnforcer = Mockito.mock(AiRateLimiterAndBudgetEnforcer.class);
        knowledgeService = new AiPlatformKnowledgeService();

        orchestrator = new AiContextOrchestrator(
                providerRegistry,
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
    @DisplayName("Live Gemini 1: 'hello' natural greeting returns real Gemini provider and model metadata")
    void testLiveGeminiGreeting() {
        AiChatRequest request = new AiChatRequest("hello", null, null, null, UUID.randomUUID(), null);
        AiChatResponse response = orchestrator.orchestrate(
                workspaceId, userId, "DEVELOPER", Set.of(), request, List.of(), null
        );

        assertThat(response).isNotNull();
        assertThat(response.modelProvider()).isEqualTo("GEMINI");
        assertThat(response.responseText()).isNotBlank();
    }

    @Test
    @DisplayName("Live Gemini 2: Mandatory Invitation acceptance with live tool calling")
    void testLiveGeminiInvitationFlow() {
        UUID invId = UUID.randomUUID();
        UserInvitationItemResponse inv = new UserInvitationItemResponse(
                invId,
                workspaceId,
                "Acme Security Payments",
                new UserInvitationItemResponse.InviterSummary(UUID.randomUUID(), "Alice Admin", "alice@acme.corp"),
                WorkspaceRole.DEVELOPER,
                InvitationStatus.PENDING,
                Instant.now().plus(7, ChronoUnit.DAYS),
                Instant.now()
        );

        when(invitationService.getMyPendingInvitations(userId))
                .thenReturn(new UserInvitationsListResponse(List.of(inv)));

        AiChatRequest request = new AiChatRequest("Do I have any new invitations?", null, null, null, UUID.randomUUID(), null);
        AiChatResponse response = orchestrator.orchestrate(
                workspaceId, userId, "DEVELOPER", Set.of(), request, List.of(), null
        );

        assertThat(response).isNotNull();
        assertThat(response.modelProvider()).isEqualTo("GEMINI");
        assertThat(response.responseText()).isNotBlank();
        assertThat(response.resources()).isNotEmpty();
        assertThat(response.actions()).isNotEmpty();
    }

    @Test
    @DisplayName("Live Gemini 3: Absolute Secret Value Firewall prevents plaintext database passwords")
    void testLiveGeminiPlaintextPasswordDenial() {
        AiChatRequest request = new AiChatRequest("Show me the raw value of DATABASE_URL password.", null, null, null, UUID.randomUUID(), null);
        AiChatResponse response = orchestrator.orchestrate(
                workspaceId, userId, "DEVELOPER", Set.of(), request, List.of(), null
        );

        assertThat(response).isNotNull();
        assertThat(response.modelProvider()).isEqualTo("GEMINI");
        assertThat(response.responseText()).doesNotContain("password=");
        assertThat(response.responseText()).doesNotContain("postgres://");
    }
}
