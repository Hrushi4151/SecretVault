package com.secretvault.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.domain.entity.AiToolExecution;
import com.secretvault.ai.domain.repository.AiToolExecutionRepository;
import com.secretvault.ai.security.AiSecretFirewall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Server-side controlled execution engine for AI tools.
 * Enforces RBAC permissions, workspace boundary invariants, latency measurement,
 * telemetry persistence, and secret firewall scrubbing on all outputs.
 */
@Service
public class AiToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiToolExecutor.class);

    private final AiToolRegistry registry;
    private final AiSecretFirewall secretFirewall;
    private final AiToolExecutionRepository toolExecutionRepository;
    private final ObjectMapper objectMapper;

    public AiToolExecutor(
            AiToolRegistry registry,
            AiSecretFirewall secretFirewall,
            @Autowired(required = false) AiToolExecutionRepository toolExecutionRepository,
            ObjectMapper objectMapper
    ) {
        this.registry = registry;
        this.secretFirewall = secretFirewall;
        this.toolExecutionRepository = toolExecutionRepository;
        this.objectMapper = objectMapper;
    }

    public AiToolResult executeTool(String toolName, Map<String, Object> rawArguments, AiToolInvocationContext context) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> arguments = rawArguments != null ? rawArguments : Map.of();

        log.debug("AI tool execution requested: tool={}, workspace={}, user={}", toolName, context.workspaceId(), context.userId());

        Optional<AiTool> toolOpt = registry.getTool(toolName);
        if (toolOpt.isEmpty()) {
            String err = "Tool '" + toolName + "' not found in AI tool registry.";
            log.warn(err);
            return recordAndReturn(toolName, arguments, false, "{\"error\":\"" + err + "\"}", err, System.currentTimeMillis() - startTime, context);
        }

        AiTool tool = toolOpt.get();

        // 1. Server-Side Permission Check
        String reqPerm = tool.getRequiredPermission();
        if (reqPerm != null && !context.hasPermission(reqPerm)) {
            String err = "Access Denied: User does not hold required permission '" + reqPerm + "' to execute tool '" + toolName + "'.";
            log.warn("Permission denied for tool {}: userId={}, requiredPerm={}", toolName, context.userId(), reqPerm);
            return recordAndReturn(toolName, arguments, false, "{\"error\":\"" + err + "\"}", err, System.currentTimeMillis() - startTime, context);
        }

        try {
            // 2. Execute authoritative tool logic
            AiToolResult rawResult = tool.execute(context, arguments);
            long latency = System.currentTimeMillis() - startTime;

            // 3. Absolute Secret Value Firewall filter on output
            String sanitizedOutput = secretFirewall.sanitizeToolOutput(toolName, rawResult.outputJson());
            secretFirewall.assertZeroPlaintext(sanitizedOutput);

            AiToolResult sanitizedResult = rawResult.success()
                    ? AiToolResult.ok(toolName, sanitizedOutput, latency, rawResult.metadata())
                    : AiToolResult.error(toolName, rawResult.error(), latency);

            return recordAndReturn(toolName, arguments, sanitizedResult.success(), sanitizedResult.outputJson(), sanitizedResult.error(), latency, context);

        } catch (SecurityException secEx) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Security violation executing tool {}: {}", toolName, secEx.getMessage());
            String err = "Security Violation: " + secEx.getMessage();
            return recordAndReturn(toolName, arguments, false, "{\"error\":\"" + err + "\"}", err, latency, context);
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Error executing tool {}: {}", toolName, ex.getMessage(), ex);
            String err = "Execution Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            return recordAndReturn(toolName, arguments, false, "{\"error\":\"" + err + "\"}", err, latency, context);
        }
    }

    private AiToolResult recordAndReturn(
            String toolName,
            Map<String, Object> arguments,
            boolean success,
            String outputJson,
            String error,
            long latencyMs,
            AiToolInvocationContext context
    ) {
        if (toolExecutionRepository != null && context.conversationId() != null) {
            try {
                AiToolExecution exec = new AiToolExecution();
                exec.setConversationId(context.conversationId());
                exec.setWorkspaceId(context.workspaceId());
                exec.setUserId(context.userId());
                exec.setToolName(toolName);
                exec.setArgumentsJson(objectMapper.writeValueAsString(arguments));
                exec.setResultSummaryJson(outputJson);
                exec.setSuccess(success);
                exec.setErrorMessage(error);
                exec.setExecutionTimeMs(latencyMs);
                toolExecutionRepository.save(exec);
            } catch (Exception e) {
                log.debug("Failed to record tool execution entity: {}", e.getMessage());
            }
        }

        return success
                ? AiToolResult.ok(toolName, outputJson, latencyMs)
                : AiToolResult.error(toolName, error, latencyMs);
    }
}
