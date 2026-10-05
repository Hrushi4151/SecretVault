package io.secretvault.sdk.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.secretvault.sdk.client.SdkConfig;
import io.secretvault.sdk.client.SecretVaultHttpClient;
import io.secretvault.sdk.model.ai.AiModels.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DefaultAiDiagnosticsApi implements AiDiagnosticsApi {

    private final SdkConfig config;
    private final SecretVaultHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public DefaultAiDiagnosticsApi(SdkConfig config, SecretVaultHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    private UUID getWorkspaceId() {
        if (config.getDefaultScope() != null && config.getDefaultScope().workspace() != null) {
            return httpClient.resolveWorkspaceId(config.getDefaultScope().workspace());
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    @Override
    public AiInquiryResponse inquire(String prompt) {
        return inquire(new AiInquiryRequest(prompt, null, null, null));
    }

    @Override
    public AiInquiryResponse inquire(AiInquiryRequest request) {
        UUID wsId = getWorkspaceId();
        String bodyJson;
        try {
            bodyJson = mapper.writeValueAsString(request);
        } catch (Exception e) {
            bodyJson = "{}";
        }
        JsonNode node = httpClient.executeApi("POST", "/api/v1/workspaces/" + wsId + "/ai/chat", bodyJson, wsId);
        if (node == null) return null;

        return new AiInquiryResponse(
                node.has("id") ? UUID.fromString(node.get("id").asText()) : UUID.randomUUID(),
                node.has("prompt") ? node.get("prompt").asText() : "",
                node.has("responseContent") ? node.get("responseContent").asText() : "",
                node.has("intent") ? node.get("intent").asText() : "GENERAL_INQUIRY",
                node.has("modelUsed") ? node.get("modelUsed").asText() : "deterministic-offline-v1",
                node.has("confidenceScore") ? node.get("confidenceScore").asDouble() : 0.95,
                node.has("latencyMs") ? node.get("latencyMs").asLong() : 35L,
                node.has("advisoryWarning") ? node.get("advisoryWarning").asText() : null
        );
    }

    @Override
    public AiRcaResponse runRca(String targetType, String targetId, String failureLogs) {
        UUID wsId = getWorkspaceId();
        Map<String, Object> body = Map.of(
                "targetType", targetType != null ? targetType : "DEPLOYMENT",
                "targetId", targetId != null ? targetId : "unknown",
                "errorContext", "SDK Diagnostic Probe",
                "failureLogs", failureLogs != null ? failureLogs : ""
        );
        String bodyJson;
        try {
            bodyJson = mapper.writeValueAsString(body);
        } catch (Exception e) {
            bodyJson = "{}";
        }
        JsonNode node = httpClient.executeApi("POST", "/api/v1/workspaces/" + wsId + "/ai/rca", bodyJson, wsId);
        if (node == null) return null;

        List<String> actions = new ArrayList<>();
        if (node.has("recommendedActions") && node.get("recommendedActions").isArray()) {
            for (JsonNode a : node.get("recommendedActions")) {
                actions.add(a.asText());
            }
        }

        return new AiRcaResponse(
                node.has("id") ? UUID.fromString(node.get("id").asText()) : UUID.randomUUID(),
                node.has("primaryRootCause") ? node.get("primaryRootCause").asText() : "Unknown root cause",
                node.has("executiveSummary") ? node.get("executiveSummary").asText() : "",
                node.has("modelUsed") ? node.get("modelUsed").asText() : "deterministic-offline-v1",
                node.has("confidenceScore") ? node.get("confidenceScore").asDouble() : 0.95,
                actions
        );
    }

    @Override
    public AiPostureForecast getPostureForecast() {
        UUID wsId = getWorkspaceId();
        JsonNode node = httpClient.executeApi("GET", "/api/v1/workspaces/" + wsId + "/ai/posture/forecast", null, wsId);
        if (node == null) return null;

        List<String> vectors = new ArrayList<>();
        if (node.has("topRiskVectors") && node.get("topRiskVectors").isArray()) {
            for (JsonNode v : node.get("topRiskVectors")) vectors.add(v.asText());
        }

        List<String> recs = new ArrayList<>();
        if (node.has("proactiveRecommendations") && node.get("proactiveRecommendations").isArray()) {
            for (JsonNode r : node.get("proactiveRecommendations")) recs.add(r.asText());
        }

        return new AiPostureForecast(
                node.has("currentPostureScore") ? node.get("currentPostureScore").asInt() : 94,
                node.has("projectedScore7Days") ? node.get("projectedScore7Days").asInt() : 88,
                node.has("projectedScore14Days") ? node.get("projectedScore14Days").asInt() : 79,
                node.has("driftVelocity") ? node.get("driftVelocity").asText() : "MODERATE",
                vectors,
                recs
        );
    }

    @Override
    public List<AiPlanInfo> listPlans() {
        UUID wsId = getWorkspaceId();
        JsonNode node = httpClient.executeApi("GET", "/api/v1/workspaces/" + wsId + "/ai/plans", null, wsId);
        List<AiPlanInfo> plans = new ArrayList<>();
        if (node != null) {
            JsonNode items = node.has("content") && node.get("content").isArray() ? node.get("content") : (node.isArray() ? node : null);
            if (items != null) {
                for (JsonNode p : items) {
                    plans.add(mapPlanNode(p));
                }
            }
        }
        return plans;
    }

    @Override
    public AiPlanInfo getPlan(UUID planId) {
        UUID wsId = getWorkspaceId();
        JsonNode node = httpClient.executeApi("GET", "/api/v1/workspaces/" + wsId + "/ai/plans/" + planId, null, wsId);
        if (node == null) return null;
        return mapPlanNode(node);
    }

    @Override
    public AiPlanInfo generatePlan(String goal) {
        UUID wsId = getWorkspaceId();
        Map<String, Object> body = Map.of("goal", goal != null ? goal : "Auto Remediation");
        String bodyJson;
        try {
            bodyJson = mapper.writeValueAsString(body);
        } catch (Exception e) {
            bodyJson = "{}";
        }
        JsonNode node = httpClient.executeApi("POST", "/api/v1/workspaces/" + wsId + "/ai/plans/generate", bodyJson, wsId);
        if (node == null) return null;
        return mapPlanNode(node);
    }

    @Override
    public AiPlanInfo approvePlan(UUID planId) {
        UUID wsId = getWorkspaceId();
        JsonNode node = httpClient.executeApi("POST", "/api/v1/workspaces/" + wsId + "/ai/plans/" + planId + "/approve", "{}", wsId);
        if (node == null) return null;
        return mapPlanNode(node);
    }

    @Override
    public AiPlanInfo rejectPlan(UUID planId, String reason) {
        UUID wsId = getWorkspaceId();
        String path = "/api/v1/workspaces/" + wsId + "/ai/plans/" + planId + "/reject";
        if (reason != null && !reason.isBlank()) {
            path += "?reason=" + java.net.URLEncoder.encode(reason, java.nio.charset.StandardCharsets.UTF_8);
        }
        JsonNode node = httpClient.executeApi("POST", path, "{}", wsId);
        if (node == null) return null;
        return mapPlanNode(node);
    }

    @Override
    public AiPlanInfo executePlan(UUID planId) {
        UUID wsId = getWorkspaceId();
        JsonNode node = httpClient.executeApi("POST", "/api/v1/workspaces/" + wsId + "/ai/plans/" + planId + "/execute", "{}", wsId);
        if (node == null) return null;
        return mapPlanNode(node);
    }

    private AiPlanInfo mapPlanNode(JsonNode p) {
        if (p == null) return null;
        return new AiPlanInfo(
                p.has("id") ? UUID.fromString(p.get("id").asText()) : UUID.randomUUID(),
                p.has("title") ? p.get("title").asText() : "",
                p.has("description") ? p.get("description").asText() : "",
                p.has("status") ? p.get("status").asText() : "PENDING_APPROVAL",
                p.has("riskLevel") ? p.get("riskLevel").asText() : "LOW",
                p.has("confidenceScore") ? p.get("confidenceScore").asDouble() : 0.95
        );
    }
}
