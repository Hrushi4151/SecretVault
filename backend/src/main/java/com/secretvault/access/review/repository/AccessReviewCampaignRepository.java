package com.secretvault.access.review.repository;

import com.secretvault.access.review.entity.AccessReviewCampaign;
import com.secretvault.access.review.entity.CampaignStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessReviewCampaignRepository extends JpaRepository<AccessReviewCampaign, UUID>, JpaSpecificationExecutor<AccessReviewCampaign> {

    List<AccessReviewCampaign> findByWorkspaceId(UUID workspaceId);

    List<AccessReviewCampaign> findByWorkspaceIdAndStatus(UUID workspaceId, CampaignStatus status);

    Optional<AccessReviewCampaign> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AccessReviewCampaign c WHERE c.id = :id AND c.workspaceId = :workspaceId")
    Optional<AccessReviewCampaign> findByIdAndWorkspaceIdForUpdate(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    @Query("SELECT c FROM AccessReviewCampaign c WHERE c.workspaceId = :workspaceId " +
           "AND (:status IS NULL OR c.status = :status)")
    Page<AccessReviewCampaign> findFilteredCampaigns(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") CampaignStatus status,
            Pageable pageable
    );
}
