package com.secretvault.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.knowledge.AiPlatformKnowledgeService;
import com.secretvault.ai.security.AiSecretFirewall;
import com.secretvault.ai.tool.domain.*;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class AiPlatformKnowledgeAndToolsTest {

    private AiPlatformKnowledgeService knowledgeService;
    private AiToolRegistry toolRegistry;
    private AiToolExecutor toolExecutor;
    private AiSecretFirewall secretFirewall;
    private ObjectMapper objectMapper;

    private WorkspaceRepository workspaceRepository;
    private ProjectRepository projectRepository;

    private UUID workspaceId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        knowledgeService = new AiPlatformKnowledgeService();
        secretFirewall = new AiSecretFirewall();
        objectMapper = new ObjectMapper();

        workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        projectRepository = Mockito.mock(ProjectRepository.class);

        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();

        // Register tools
        WorkspaceTools.WorkspaceGetTool wsTool = new WorkspaceTools.WorkspaceGetTool(workspaceRepository, objectMapper);
        ProjectTools.ProjectListTool projTool = new ProjectTools.ProjectListTool(projectRepository, objectMapper);
        KnowledgeTools.KnowledgeSearchTool knowSearch = new KnowledgeTools.KnowledgeSearchTool(knowledgeService, objectMapper);
        KnowledgeTools.KnowledgeGetTopicTool knowTopic = new KnowledgeTools.KnowledgeGetTopicTool(knowledgeService, objectMapper);

        toolRegistry = new AiToolRegistry(List.of(wsTool, projTool, knowSearch, knowTopic));
        toolExecutor = new AiToolExecutor(toolRegistry, secretFirewall, null, objectMapper);
    }

    @Test
    @DisplayName("Knowledge service retrieves authoritative platform documentation by keyword")
    void testPlatformKnowledgeSearch() {
        List<AiPlatformKnowledgeService.KnowledgeArticle> results = knowledgeService.search("AES-256-GCM encryption", 3);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(a -> a.topic().contains("encryption")));

        Optional<AiPlatformKnowledgeService.KnowledgeArticle> rbac = knowledgeService.getTopic("rbac_jit_access");
        assertTrue(rbac.isPresent());
        assertTrue(rbac.get().content().contains("EffectiveAccessService"));
    }

    @Test
    @DisplayName("Tool executor enforces workspace tenant isolation")
    void testWorkspaceTenantBoundary() {
        Workspace ws = new Workspace(UUID.randomUUID(), "Production Core", "prod-core", false);
        ws.setId(workspaceId);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(ws));

        AiToolInvocationContext context = new AiToolInvocationContext(workspaceId, userId, "dev@test.com", "DEVELOPER", Set.of(), null, "corr-1");

        // Authorized call
        AiToolResult okResult = toolExecutor.executeTool("workspace.get", Map.of(), context);
        assertTrue(okResult.success());
        assertTrue(okResult.outputJson().contains("Production Core"));

        // Cross-tenant unauthorized call (requesting different workspaceId in arguments)
        UUID foreignWs = UUID.randomUUID();
        AiToolResult deniedResult = toolExecutor.executeTool("workspace.get", Map.of("workspaceId", foreignWs.toString()), context);
        assertFalse(deniedResult.success());
        assertTrue(deniedResult.error().contains("Access Denied"));
    }

    @Test
    @DisplayName("Project list tool returns safe project metadata for authorized workspace")
    void testProjectListTool() {
        Project p1 = new Project(workspaceId, "Payments Service", "payments-svc", "Handles payment transactions");
        p1.setId(UUID.randomUUID());
        when(projectRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(p1));

        AiToolInvocationContext context = new AiToolInvocationContext(workspaceId, userId, "dev@test.com", "DEVELOPER", Set.of(), null, "corr-1");
        AiToolResult result = toolExecutor.executeTool("project.list", Map.of(), context);

        assertTrue(result.success());
        assertTrue(result.outputJson().contains("Payments Service"));
        assertTrue(result.outputJson().contains("payments-svc"));
    }

    @Test
    @DisplayName("Knowledge tool returns grounded architecture details through tool calling")
    void testKnowledgeSearchToolExecution() {
        AiToolInvocationContext context = new AiToolInvocationContext(workspaceId, userId, "dev@test.com", "DEVELOPER", Set.of(), null, "corr-1");
        AiToolResult result = toolExecutor.executeTool("knowledge.search", Map.of("query", "Kubernetes mutating webhook"), context);

        assertTrue(result.success());
        assertTrue(result.outputJson().contains("SecretVaultSecret"));
        assertTrue(result.outputJson().contains("Mutating Webhook"));
    }
}
