package com.secretvault.ai.service;

import com.secretvault.ai.domain.entity.AiConversation;
import com.secretvault.ai.domain.entity.AiMessage;
import com.secretvault.ai.domain.repository.AiConversationRepository;
import com.secretvault.ai.domain.repository.AiMessageRepository;
import com.secretvault.ai.provider.ChatMessage;
import com.secretvault.ai.security.AiSecretFirewall;
import com.secretvault.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service managing persistent multi-turn conversations and message histories for AI Copilot.
 * Enforces strict workspace isolation and zero-plaintext storage invariants.
 */
@Service
public class AiConversationService {

    private static final Logger log = LoggerFactory.getLogger(AiConversationService.class);

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AiSecretFirewall secretFirewall;

    public AiConversationService(
            @Autowired(required = false) AiConversationRepository conversationRepository,
            @Autowired(required = false) AiMessageRepository messageRepository,
            AiSecretFirewall secretFirewall
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.secretFirewall = secretFirewall;
    }

    @Transactional
    public AiConversation createConversation(UUID workspaceId, UUID userId, String title, String scopeType, UUID scopeId) {
        String safeTitle = title != null && !title.isBlank()
                ? secretFirewall.sanitize(title.trim())
                : "Security Inquiry " + Instant.now().toString().substring(0, 19);

        AiConversation conv = new AiConversation();
        conv.setWorkspaceId(workspaceId);
        conv.setUserId(userId);
        conv.setTitle(safeTitle);
        conv.setScopeType(scopeType != null ? scopeType.toUpperCase() : "WORKSPACE");
        conv.setScopeId(scopeId != null ? scopeId.toString() : workspaceId.toString());
        conv.setStatus("ACTIVE");

        if (conversationRepository != null) {
            return conversationRepository.save(conv);
        }
        return conv;
    }

    @Transactional(readOnly = true)
    public Page<AiConversation> listConversations(UUID workspaceId, UUID userId, Pageable pageable) {
        if (conversationRepository == null) {
            return Page.empty(pageable);
        }
        return conversationRepository.findByWorkspaceIdAndStatusOrderByUpdatedAtDesc(workspaceId, "ACTIVE", pageable);
    }

    @Transactional(readOnly = true)
    public AiConversation getConversation(UUID conversationId, UUID workspaceId, UUID userId) {
        if (conversationRepository == null) {
            throw ApiException.notFound("Conversation repository not initialized");
        }
        return conversationRepository.findByIdAndWorkspaceId(conversationId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Conversation not found in authorized workspace"));
    }

    @Transactional
    public AiConversation renameConversation(UUID conversationId, UUID workspaceId, UUID userId, String newTitle) {
        AiConversation conv = getConversation(conversationId, workspaceId, userId);
        String safeTitle = secretFirewall.sanitize(newTitle != null ? newTitle.trim() : "Conversation");
        conv.setTitle(safeTitle);
        conv.setUpdatedAt(Instant.now());
        return conversationRepository.save(conv);
    }

    @Transactional
    public void archiveConversation(UUID conversationId, UUID workspaceId, UUID userId) {
        AiConversation conv = getConversation(conversationId, workspaceId, userId);
        conv.setStatus("ARCHIVED");
        conv.setUpdatedAt(Instant.now());
        conversationRepository.save(conv);
    }

    @Transactional
    public void deleteConversation(UUID conversationId, UUID workspaceId, UUID userId) {
        AiConversation conv = getConversation(conversationId, workspaceId, userId);
        conv.setStatus("DELETED");
        conv.setUpdatedAt(Instant.now());
        conversationRepository.save(conv);
    }

    @Transactional(readOnly = true)
    public List<AiMessage> getMessages(UUID conversationId, UUID workspaceId, UUID userId) {
        if (messageRepository == null) {
            return Collections.emptyList();
        }
        // Verify conversation belongs to workspace
        getConversation(conversationId, workspaceId, userId);
        return messageRepository.findByConversationIdAndWorkspaceIdOrderByCreatedAtAsc(conversationId, workspaceId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getChatMessages(UUID conversationId, UUID workspaceId, UUID userId) {
        List<AiMessage> messages = getMessages(conversationId, workspaceId, userId);
        return messages.stream()
                .map(m -> new ChatMessage(m.getRole(), m.getContent()))
                .toList();
    }

    @Transactional
    public AiMessage saveMessage(
            UUID conversationId,
            UUID workspaceId,
            UUID userId,
            String role,
            String content,
            String modelProvider,
            String modelName,
            Integer tokenCount
    ) {
        String sanitizedContent = secretFirewall.sanitize(content);
        secretFirewall.assertZeroPlaintext(sanitizedContent);

        AiMessage msg = new AiMessage();
        msg.setConversationId(conversationId);
        msg.setWorkspaceId(workspaceId);
        msg.setUserId(userId);
        msg.setRole(role != null ? role.toLowerCase() : "user");
        msg.setContent(sanitizedContent);
        msg.setModelProvider(modelProvider);
        msg.setModelName(modelName);
        msg.setTokensUsed(tokenCount != null ? tokenCount : 0);

        if (messageRepository != null) {
            AiMessage saved = messageRepository.save(msg);
            if (conversationRepository != null) {
                conversationRepository.findByIdAndWorkspaceId(conversationId, workspaceId).ifPresent(c -> {
                    c.setUpdatedAt(Instant.now());
                    conversationRepository.save(c);
                });
            }
            return saved;
        }
        return msg;
    }
}
