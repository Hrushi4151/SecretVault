package com.secretvault.ai.domain.repository;

import com.secretvault.ai.domain.entity.AiTokenBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiTokenBudgetRepository extends JpaRepository<AiTokenBudget, UUID> {
    Optional<AiTokenBudget> findByWorkspaceId(UUID workspaceId);
}
