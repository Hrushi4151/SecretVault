package com.secretvault.ai.domain.repository;

import com.secretvault.ai.domain.entity.AiMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    List<AiMessage> findByConversationIdAndWorkspaceIdOrderByCreatedAtAsc(UUID conversationId, UUID workspaceId);

    Page<AiMessage> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    long countByConversationIdAndWorkspaceId(UUID conversationId, UUID workspaceId);
}
