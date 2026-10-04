package io.secretvault.sdk.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.secretvault.sdk.client.SdkConfig;
import io.secretvault.sdk.client.SecretVaultHttpClient;
import io.secretvault.sdk.model.SecretScope;
import io.secretvault.sdk.model.ai.AiModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Phase 15: SDK AI Diagnostics API Tests")
class AiDiagnosticsApiTest {

    private SecretVaultHttpClient httpClient;
    private SdkConfig config;
    private ObjectMapper mapper;
    private DefaultAiDiagnosticsApi aiApi;
    private final UUID workspaceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        httpClient = mock(SecretVaultHttpClient.class);
        mapper = new ObjectMapper();
        config = SdkConfig.builder()
                .endpoint(URI.create("http://localhost:8080"))
                .allowHttp(true)
                .defaultScope(new SecretScope("prod-ws", "api-proj", "prod"))
                .build();

        when(httpClient.resolveWorkspaceId(anyString())).thenReturn(workspaceId);
        aiApi = new DefaultAiDiagnosticsApi(config, httpClient);
    }

    @Test
    @DisplayName("SDK: Submit natural-language AI inquiry via copilot")
    void testInquire() {
        ObjectNode mockResp = mapper.createObjectNode();
        UUID inqId = UUID.randomUUID();
        mockResp.put("id", inqId.toString());
        mockResp.put("prompt", "Analyze secret drift");
        mockResp.put("responseContent", "No significant drift detected across clusters.");
        mockResp.put("intent", "DRIFT_ANALYSIS");
        mockResp.put("modelUsed", "deterministic-offline-v1");
        mockResp.put("confidenceScore", 0.98);
        mockResp.put("latencyMs", 25L);

        when(httpClient.executeApi(eq("POST"), contains("/ai/chat"), any(), eq(workspaceId)))
                .thenReturn(mockResp);

        AiInquiryResponse response = aiApi.inquire("Analyze secret drift");

        assertNotNull(response);
        assertEquals(inqId, response.id());
        assertEquals("DRIFT_ANALYSIS", response.intent());
        assertEquals("No significant drift detected across clusters.", response.responseContent());
        assertEquals(0.98, response.confidenceScore(), 0.001);
    }

    @Test
    @DisplayName("SDK: Run deployment root cause analysis (RCA)")
    void testRunRca() {
        ObjectNode mockResp = mapper.createObjectNode();
        UUID rcaId = UUID.randomUUID();
        mockResp.put("id", rcaId.toString());
        mockResp.put("primaryRootCause", "Database connection timeout during credential swap");
        mockResp.put("executiveSummary", "Application worker attempted DB connection before replica credentials were fully synchronized.");
        mockResp.put("modelUsed", "deterministic-offline-v1");
        mockResp.put("confidenceScore", 0.96);

        ArrayNode actions = mockResp.putArray("recommendedActions");
        actions.add("Extend lease grace period to 60s");
        actions.add("Re-trigger Kubernetes secret sync");

        when(httpClient.executeApi(eq("POST"), contains("/ai/rca"), any(), eq(workspaceId)))
                .thenReturn(mockResp);

        AiRcaResponse response = aiApi.runRca("DEPLOYMENT", "dep-app-101", "Connection timeout at 10.0.1.5:5432");

        assertNotNull(response);
        assertEquals(rcaId, response.id());
        assertTrue(response.primaryRootCause().contains("connection timeout"));
        assertEquals(2, response.recommendedActions().size());
    }

    @Test
    @DisplayName("SDK: Fetch posture forecast and risk vectors")
    void testGetPostureForecast() {
        ObjectNode mockResp = mapper.createObjectNode();
        mockResp.put("currentPostureScore", 95);
        mockResp.put("projectedScore7Days", 89);
        mockResp.put("projectedScore14Days", 80);
        mockResp.put("driftVelocity", "MODERATE");

        ArrayNode risks = mockResp.putArray("topRiskVectors");
        risks.add("Expiring database credentials");

        ArrayNode recs = mockResp.putArray("proactiveRecommendations");
        recs.add("Trigger zero-downtime rotation");

        when(httpClient.executeApi(eq("GET"), contains("/ai/posture/forecast"), isNull(), eq(workspaceId)))
                .thenReturn(mockResp);

        AiPostureForecast forecast = aiApi.getPostureForecast();

        assertNotNull(forecast);
        assertEquals(95, forecast.currentScore());
        assertEquals(89, forecast.projected7Days());
        assertEquals(80, forecast.projected14Days());
        assertEquals("MODERATE", forecast.driftVelocity());
        assertEquals(1, forecast.riskVectors().size());
        assertEquals(1, forecast.recommendations().size());
    }

    @Test
    @DisplayName("SDK: List and execute remediation plans")
    void testPlansWorkflow() {
        UUID planId = UUID.randomUUID();
        ArrayNode mockList = mapper.createArrayNode();
        ObjectNode planNode = mockList.addObject();
        planNode.put("id", planId.toString());
        planNode.put("title", "DB Credential Rotation");
        planNode.put("description", "Dual-credential rotation for prod-db");
        planNode.put("status", "APPROVED");
        planNode.put("riskLevel", "LOW");
        planNode.put("confidenceScore", 0.95);

        when(httpClient.executeApi(eq("GET"), contains("/ai/plans"), isNull(), eq(workspaceId)))
                .thenReturn(mockList);

        List<AiPlanInfo> plans = aiApi.listPlans();
        assertNotNull(plans);
        assertEquals(1, plans.size());
        assertEquals(planId, plans.get(0).id());

        // Test Execute
        ObjectNode execResp = mapper.createObjectNode();
        execResp.put("id", planId.toString());
        execResp.put("title", "DB Credential Rotation");
        execResp.put("description", "Dual-credential rotation for prod-db");
        execResp.put("status", "EXECUTED");
        execResp.put("riskLevel", "LOW");
        execResp.put("confidenceScore", 0.95);

        when(httpClient.executeApi(eq("POST"), contains("/ai/plans/" + planId + "/execute"), any(), eq(workspaceId)))
                .thenReturn(execResp);

        AiPlanInfo executed = aiApi.executePlan(planId);
        assertNotNull(executed);
        assertEquals("EXECUTED", executed.status());
    }
}
