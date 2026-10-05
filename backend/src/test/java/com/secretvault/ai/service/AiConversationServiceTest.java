package com.secretvault.ai.service;

import com.secretvault.ai.domain.entity.AiConversation;
import com.secretvault.ai.domain.entity.AiMessage;
import com.secretvault.ai.domain.repository.AiConversationRepository;
import com.secretvault.ai.domain.repository.AiMessageRepository;
import com.secretvault.ai.security.AiSecretFirewall;
import com.secretvault.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiConversationServiceTest {

    private AiConversationRepository conversationRepository;
    private AiMessageRepository messageRepository;
    private AiSecretFirewall secretFirewall;
    private AiConversationService conversationService;

    private UUID workspaceId;
    private UUID userId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        conversationRepository = Mockito.mock(AiConversationRepository.class);
        messageRepository = Mockito.mock(AiMessageRepository.class);
        secretFirewall = new AiSecretFirewall();
        conversationService = new AiConversationService(conversationRepository, messageRepository, secretFirewall);

        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Create conversation creates sanitized session with active status")
    void testCreateConversation() {
        when(conversationRepository.save(any(AiConversation.class))).thenAnswer(i -> i.getArgument(0));

        AiConversation conv = conversationService.createConversation(workspaceId, userId, "Investigation on DB sync", "PROJECT", null);
        assertNotNull(conv);
        assertEquals("Investigation on DB sync", conv.getTitle());
        assertEquals("ACTIVE", conv.getStatus());
        assertEquals(workspaceId, conv.getWorkspaceId());
    }

    @Test
    @DisplayName("Get conversation enforces workspace tenant boundary")
    void testGetConversationTenantIsolation() {
        when(conversationRepository.findByIdAndWorkspaceId(conversationId, workspaceId)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> conversationService.getConversation(conversationId, workspaceId, userId));
    }

    @Test
    @DisplayName("Save message scrubs secret credentials before persistence")
    void testSaveMessageFirewallSanitization() {
        when(messageRepository.save(any(AiMessage.class))).thenAnswer(i -> i.getArgument(0));

        AiMessage msg = conversationService.saveMessage(
                conversationId,
                workspaceId,
                userId,
                "user",
                "My db password is password: SuperSecretDBPassword! please check",
                null,
                null,
                10
        );

        assertNotNull(msg);
        assertFalse(msg.getContent().contains("SuperSecretDBPassword!"));
        assertTrue(msg.getContent().contains("[SHA256:"));
    }

    @Test
    @DisplayName("Archive and delete update conversation status")
    void testArchiveAndDeleteConversation() {
        AiConversation conv = new AiConversation();
        conv.setId(conversationId);
        conv.setWorkspaceId(workspaceId);
        conv.setStatus("ACTIVE");
        when(conversationRepository.findByIdAndWorkspaceId(conversationId, workspaceId)).thenReturn(Optional.of(conv));
        when(conversationRepository.save(any(AiConversation.class))).thenAnswer(i -> i.getArgument(0));

        conversationService.archiveConversation(conversationId, workspaceId, userId);
        assertEquals("ARCHIVED", conv.getStatus());

        conversationService.deleteConversation(conversationId, workspaceId, userId);
        assertEquals("DELETED", conv.getStatus());
    }
}
