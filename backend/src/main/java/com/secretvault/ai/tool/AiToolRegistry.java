package com.secretvault.ai.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry managing all registered AI Copilot tools across SecretVault domains.
 */
@Component
public class AiToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiToolRegistry.class);

    private final Map<String, AiTool> tools = new ConcurrentHashMap<>();

    public AiToolRegistry(@Autowired(required = false) List<AiTool> injectedTools) {
        if (injectedTools != null) {
            for (AiTool tool : injectedTools) {
                registerTool(tool);
            }
        }
        log.info("AiToolRegistry initialized with {} tools: {}", tools.size(), tools.keySet());
    }

    public void registerTool(AiTool tool) {
        if (tool != null && tool.getName() != null) {
            tools.put(tool.getName(), tool);
            log.debug("Registered AI tool: {}", tool.getName());
        }
    }

    public Optional<AiTool> getTool(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(tools.get(name));
    }

    public boolean hasTool(String name) {
        return name != null && tools.containsKey(name);
    }

    public List<AiTool> getTools() {
        return new ArrayList<>(tools.values());
    }

    public List<AiToolDefinition> getToolDefinitions() {
        return tools.values().stream()
                .map(AiTool::getDefinition)
                .sorted(Comparator.comparing(AiToolDefinition::name))
                .toList();
    }
}
