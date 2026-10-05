package com.secretvault.ai.domain.repository;

import com.secretvault.ai.domain.entity.AiToolExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiToolExecutionRepository extends JpaRepository<AiToolExecution, UUID> {

    List<AiToolExecution> findByConversationIdAndWorkspaceIdOrderByCreatedAtAsc(UUID conversationId, UUID workspaceId);

    List<AiToolExecution> findByMessageIdOrderByCreatedAtAsc(UUID messageId);

    Page<AiToolExecution> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);
}
