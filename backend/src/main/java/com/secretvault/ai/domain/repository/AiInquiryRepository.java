package com.secretvault.ai.domain.repository;

import com.secretvault.ai.domain.entity.AiInquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiInquiryRepository extends JpaRepository<AiInquiry, UUID> {
    Page<AiInquiry> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);
    Optional<AiInquiry> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
