package com.secretvault.ai.domain.repository;

import com.secretvault.ai.domain.entity.AiConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    Optional<AiConversation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<AiConversation> findByWorkspaceIdAndStatusOrderByUpdatedAtDesc(UUID workspaceId, String status, Pageable pageable);

    Page<AiConversation> findByWorkspaceIdAndUserIdAndStatusOrderByUpdatedAtDesc(UUID workspaceId, UUID userId, String status, Pageable pageable);

    Page<AiConversation> findByWorkspaceIdOrderByUpdatedAtDesc(UUID workspaceId, Pageable pageable);
}
