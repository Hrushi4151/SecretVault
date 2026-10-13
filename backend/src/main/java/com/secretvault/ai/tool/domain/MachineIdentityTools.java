package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.repository.MachineIdentityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MachineIdentityTools {

    @Component
    public static class MachineIdentityListTool implements AiTool {
        private final MachineIdentityRepository machineRepository;
        private final ObjectMapper objectMapper;

        public MachineIdentityListTool(@Autowired(required = false) MachineIdentityRepository machineRepository, ObjectMapper objectMapper) {
            this.machineRepository = machineRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "machineIdentity.list";
        }

        @Override
        public String getDescription() {
            return "Lists registered machine identities, service accounts, and SPIFFE IDs in the authorized workspace.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                if (machineRepository == null) {
                    return AiToolResult.ok(getName(), "{\"machineIdentities\":[]}", System.currentTimeMillis() - start);
                }

                List<MachineIdentity> identities = machineRepository.findByWorkspaceIdAndDeletedAtIsNull(context.workspaceId());
                List<Map<String, Object>> safeList = identities.stream()
                        .map(m -> Map.<String, Object>of(
                                "id", m.getId().toString(),
                                "name", m.getName(),
                                "status", m.getStatus() != null ? m.getStatus().name() : "ACTIVE",
                                "description", m.getDescription() != null ? m.getDescription() : "",
                                "expiresAt", m.getExpiresAt() != null ? m.getExpiresAt().toString() : "NEVER",
                                "createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalIdentities", safeList.size(), "machineIdentities", safeList)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
