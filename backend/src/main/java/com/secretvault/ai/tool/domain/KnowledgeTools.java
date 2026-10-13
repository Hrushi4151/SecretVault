package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.knowledge.AiPlatformKnowledgeService;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class KnowledgeTools {

    @Component
    public static class KnowledgeSearchTool implements AiTool {
        private final AiPlatformKnowledgeService knowledgeService;
        private final ObjectMapper objectMapper;

        public KnowledgeSearchTool(AiPlatformKnowledgeService knowledgeService, ObjectMapper objectMapper) {
            this.knowledgeService = knowledgeService;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "knowledge.search";
        }

        @Override
        public String getDescription() {
            return "Searches authoritative SecretVault documentation (architecture, KMS envelope encryption, RBAC, JIT, MFA, rotation, sync, Kubernetes, Terraform, CLI, SDK).";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "query", Map.of("type", "string", "description", "Search keywords or conceptual topic (e.g. 'encryption', 'JIT access', 'rotation lease')"),
                            "limit", Map.of("type", "integer", "description", "Max articles to return (default: 3)")
                    ),
                    "required", List.of("query")
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                String query = arguments.containsKey("query") && arguments.get("query") != null ? arguments.get("query").toString() : "";
                int limit = 3;
                if (arguments.containsKey("limit") && arguments.get("limit") instanceof Number n) {
                    limit = Math.min(5, Math.max(1, n.intValue()));
                }

                List<AiPlatformKnowledgeService.KnowledgeArticle> articles = knowledgeService.search(query, limit);

                List<Map<String, Object>> safeArticles = articles.stream()
                        .map(a -> Map.<String, Object>of(
                                "topic", a.topic(),
                                "title", a.title(),
                                "category", a.category(),
                                "content", a.content()
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of(
                        "totalArticlesFound", safeArticles.size(),
                        "articles", safeArticles
                )), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class KnowledgeGetTopicTool implements AiTool {
        private final AiPlatformKnowledgeService knowledgeService;
        private final ObjectMapper objectMapper;

        public KnowledgeGetTopicTool(AiPlatformKnowledgeService knowledgeService, ObjectMapper objectMapper) {
            this.knowledgeService = knowledgeService;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "knowledge.getTopic";
        }

        @Override
        public String getDescription() {
            return "Retrieves the full authoritative documentation for a specific SecretVault topic.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "topic", Map.of("type", "string", "description", "The specific topic key (e.g. 'encryption_kms', 'rbac_jit_access', 'kubernetes_integration')")
                    ),
                    "required", List.of("topic")
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                String topic = arguments.containsKey("topic") && arguments.get("topic") != null ? arguments.get("topic").toString() : "";
                Optional<AiPlatformKnowledgeService.KnowledgeArticle> articleOpt = knowledgeService.getTopic(topic);

                if (articleOpt.isEmpty()) {
                    return AiToolResult.error(getName(), "Topic '" + topic + "' not found.", System.currentTimeMillis() - start);
                }

                AiPlatformKnowledgeService.KnowledgeArticle a = articleOpt.get();
                Map<String, Object> safeDto = Map.of(
                        "topic", a.topic(),
                        "title", a.title(),
                        "category", a.category(),
                        "content", a.content()
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(safeDto), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
