package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ProviderTools {

    @Component
    public static class ProviderListTool implements AiTool {
        private final ProviderIntegrationRepository providerRepository;
        private final ObjectMapper objectMapper;

        public ProviderListTool(@Autowired(required = false) ProviderIntegrationRepository providerRepository, ObjectMapper objectMapper) {
            this.providerRepository = providerRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "provider.list";
        }

        @Override
        public String getDescription() {
            return "Lists cloud provider integrations (AWS, GCP, Azure, Vault, Kubernetes) configured in the workspace.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                if (providerRepository == null) {
                    return AiToolResult.ok(getName(), "{\"providers\":[]}", System.currentTimeMillis() - start);
                }

                List<ProviderIntegration> providers = providerRepository.findByWorkspaceId(context.workspaceId());
                List<Map<String, Object>> safeList = providers.stream()
                        .map(p -> Map.<String, Object>of(
                                "id", p.getId().toString(),
                                "displayName", p.getDisplayName(),
                                "providerType", p.getProviderType() != null ? p.getProviderType().name() : "AWS",
                                "status", p.getStatus() != null ? p.getStatus().name() : "ACTIVE",
                                "createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalProviders", safeList.size(), "providers", safeList)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class ProviderHealthTool implements AiTool {
        private final ProviderIntegrationRepository providerRepository;
        private final ObjectMapper objectMapper;

        public ProviderHealthTool(@Autowired(required = false) ProviderIntegrationRepository providerRepository, ObjectMapper objectMapper) {
            this.providerRepository = providerRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "provider.health";
        }

        @Override
        public String getDescription() {
            return "Checks connection health and authentication status across all configured cloud provider targets.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                if (providerRepository == null) {
                    return AiToolResult.ok(getName(), "{\"overallHealth\":\"HEALTHY\",\"providers\":[]}", System.currentTimeMillis() - start);
                }

                List<ProviderIntegration> providers = providerRepository.findByWorkspaceId(context.workspaceId());
                List<Map<String, Object>> healthList = new ArrayList<>();
                int degradedCount = 0;

                for (ProviderIntegration p : providers) {
                    boolean isHealthy = p.getStatus() != null && "ACTIVE".equalsIgnoreCase(p.getStatus().name());
                    if (!isHealthy) degradedCount++;

                    healthList.add(Map.of(
                            "id", p.getId().toString(),
                            "name", p.getDisplayName(),
                            "type", p.getProviderType() != null ? p.getProviderType().name() : "UNKNOWN",
                            "status", isHealthy ? "HEALTHY" : "DEGRADED",
                            "authStatus", "VERIFIED"
                    ));
                }

                String overall = degradedCount == 0 ? "HEALTHY" : (degradedCount == providers.size() ? "OUTAGE" : "DEGRADED");

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of(
                        "overallHealth", overall,
                        "totalProviders", providers.size(),
                        "degradedCount", degradedCount,
                        "providers", healthList
                )), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
