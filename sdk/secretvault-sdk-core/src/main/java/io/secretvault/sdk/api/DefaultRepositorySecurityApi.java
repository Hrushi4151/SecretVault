package io.secretvault.sdk.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.secretvault.sdk.client.SdkConfig;
import io.secretvault.sdk.client.SecretVaultHttpClient;
import io.secretvault.sdk.model.repo.RepoSecurityModels.RepositoryInfo;
import io.secretvault.sdk.model.repo.RepoSecurityModels.ScanStatusInfo;
import io.secretvault.sdk.model.repo.RepoSecurityModels.SecretFindingInfo;
import io.secretvault.sdk.model.repo.RepoSecurityModels.WhyExposedInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DefaultRepositorySecurityApi implements RepositorySecurityApi {

    private final SdkConfig config;
    private final SecretVaultHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public DefaultRepositorySecurityApi(SdkConfig config, SecretVaultHttpClient httpClient) {
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
    public List<RepositoryInfo> listRepositories() {
        UUID wsId = getWorkspaceId();
        JsonNode node = httpClient.executeApi("GET", "/api/v1/workspaces/" + wsId + "/repositories?size=100", null, wsId);
        List<RepositoryInfo> list = new ArrayList<>();
        if (node != null && node.has("content")) {
            for (JsonNode item : node.get("content")) {
                list.add(new RepositoryInfo(
                        UUID.fromString(item.get("id").asText()),
                        wsId,
                        item.has("provider") ? item.get("provider").asText() : "GITHUB",
                        item.has("owner") ? item.get("owner").asText() : "",
                        item.has("name") ? item.get("name").asText() : "",
                        item.has("defaultBranch") ? item.get("defaultBranch").asText() : "main",
                        item.has("visibility") ? item.get("visibility").asText() : "PRIVATE",
                        item.has("status") ? item.get("status").asText() : "ACTIVE",
                        item.has("totalFindings") ? item.get("totalFindings").asLong() : 0L,
                        item.has("criticalFindings") ? item.get("criticalFindings").asLong() : 0L,
                        null
                ));
            }
        }
        return list;
    }

    @Override
    public RepositoryInfo getRepository(UUID repositoryId) {
        UUID wsId = getWorkspaceId();
        JsonNode item = httpClient.executeApi("GET", "/api/v1/workspaces/" + wsId + "/repositories/" + repositoryId, null, wsId);
        return new RepositoryInfo(
                UUID.fromString(item.get("id").asText()),
                wsId,
                item.has("provider") ? item.get("provider").asText() : "GITHUB",
                item.has("owner") ? item.get("owner").asText() : "",
                item.has("name") ? item.get("name").asText() : "",
                item.has("defaultBranch") ? item.get("defaultBranch").asText() : "main",
                item.has("visibility") ? item.get("visibility").asText() : "PRIVATE",
                item.has("status") ? item.get("status").asText() : "ACTIVE",
                item.has("totalFindings") ? item.get("totalFindings").asLong() : 0L,
                item.has("criticalFindings") ? item.get("criticalFindings").asLong() : 0L,
                null
        );
    }

    @Override
    public ScanStatusInfo triggerScan(UUID repositoryId, String scanType) {
        UUID wsId = getWorkspaceId();
        String body = "{\"scanType\":\"" + (scanType != null ? scanType : "INCREMENTAL") + "\"}";
        JsonNode item = httpClient.executeApi("POST", "/api/v1/workspaces/" + wsId + "/repositories/" + repositoryId + "/scans", body, wsId);
        return parseScanNode(item, wsId, repositoryId);
    }

    @Override
    public ScanStatusInfo getScanStatus(UUID scanId) {
        UUID wsId = getWorkspaceId();
        JsonNode item = httpClient.executeApi("GET", "/api/v1/workspaces/" + wsId + "/repository-scans/" + scanId, null, wsId);
        return parseScanNode(item, wsId, null);
    }

    @Override
    public List<SecretFindingInfo> getFindings(UUID repositoryId) {
        UUID wsId = getWorkspaceId();
        String path = "/api/v1/workspaces/" + wsId + "/secret-findings?size=100" +
                (repositoryId != null ? "&repositoryId=" + repositoryId : "");
        JsonNode node = httpClient.executeApi("GET", path, null, wsId);
        List<SecretFindingInfo> list = new ArrayList<>();
        if (node != null && node.has("content")) {
            for (JsonNode item : node.get("content")) {
                list.add(new SecretFindingInfo(
                        UUID.fromString(item.get("id").asText()),
                        wsId,
                        repositoryId,
                        item.has("secretType") ? item.get("secretType").asText() : "UNKNOWN",
                        item.has("severity") ? item.get("severity").asText() : "LOW",
                        item.has("confidence") ? item.get("confidence").asText() : "MEDIUM",
                        item.has("status") ? item.get("status").asText() : "DETECTED",
                        item.has("filePath") ? item.get("filePath").asText() : "",
                        item.has("lineNumber") ? item.get("lineNumber").asInt() : 1,
                        item.has("maskedEvidence") ? item.get("maskedEvidence").asText() : "********",
                        item.has("fingerprint") ? item.get("fingerprint").asText() : "",
                        null, null
                ));
            }
        }
        return list;
    }

    @Override
    public SecretFindingInfo getFinding(UUID findingId) {
        UUID wsId = getWorkspaceId();
        JsonNode item = httpClient.executeApi("GET", "/api/v1/workspaces/" + wsId + "/secret-findings/" + findingId, null, wsId);
        return new SecretFindingInfo(
                UUID.fromString(item.get("id").asText()),
                wsId,
                item.has("repositoryId") && !item.get("repositoryId").isNull() ? UUID.fromString(item.get("repositoryId").asText()) : null,
                item.has("secretType") ? item.get("secretType").asText() : "UNKNOWN",
                item.has("severity") ? item.get("severity").asText() : "LOW",
                item.has("confidence") ? item.get("confidence").asText() : "MEDIUM",
                item.has("status") ? item.get("status").asText() : "DETECTED",
                item.has("filePath") ? item.get("filePath").asText() : "",
                item.has("lineNumber") ? item.get("lineNumber").asInt() : 1,
                item.has("maskedEvidence") ? item.get("maskedEvidence").asText() : "********",
                item.has("fingerprint") ? item.get("fingerprint").asText() : "",
                null, null
        );
    }

    @Override
    public WhyExposedInfo explainFinding(UUID findingId) {
        UUID wsId = getWorkspaceId();
        JsonNode item = httpClient.executeApi("GET", "/api/v1/workspaces/" + wsId + "/secret-findings/" + findingId + "/why-exposed", null, wsId);
        List<String> riskFactors = new ArrayList<>();
        if (item.has("riskFactors") && item.get("riskFactors").isArray()) {
            for (JsonNode f : item.get("riskFactors")) {
                riskFactors.add(f.asText());
            }
        }
        return new WhyExposedInfo(
                findingId,
                item.has("secretType") ? item.get("secretType").asText() : "UNKNOWN",
                item.has("severity") ? item.get("severity").asText() : "LOW",
                item.has("maskedValue") ? item.get("maskedValue").asText() : "********",
                item.has("filePath") ? item.get("filePath").asText() : "",
                item.has("lineNumber") ? item.get("lineNumber").asInt() : 1,
                item.has("repositoryName") ? item.get("repositoryName").asText() : "",
                riskFactors,
                item.has("recommendedRemediation") ? item.get("recommendedRemediation").asText() : ""
        );
    }

    private ScanStatusInfo parseScanNode(JsonNode item, UUID wsId, UUID defaultRepoId) {
        return new ScanStatusInfo(
                UUID.fromString(item.get("id").asText()),
                wsId,
                item.has("repositoryId") && !item.get("repositoryId").isNull() ? UUID.fromString(item.get("repositoryId").asText()) : defaultRepoId,
                item.has("scanType") ? item.get("scanType").asText() : "INCREMENTAL",
                item.has("status") ? item.get("status").asText() : "QUEUED",
                item.has("filesScanned") ? item.get("filesScanned").asInt() : 0,
                item.has("commitsScanned") ? item.get("commitsScanned").asInt() : 0,
                item.has("findingsCount") ? item.get("findingsCount").asInt() : 0,
                item.has("highRiskCount") ? item.get("highRiskCount").asInt() : 0,
                item.has("errorMessage") && !item.get("errorMessage").isNull() ? item.get("errorMessage").asText() : null,
                null, null
        );
    }
}
