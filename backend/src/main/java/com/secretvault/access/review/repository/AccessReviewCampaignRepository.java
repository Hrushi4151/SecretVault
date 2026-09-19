package com.secretvault.access.review.repository;

import com.secretvault.access.review.entity.AccessReviewCampaign;
import com.secretvault.access.review.entity.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessReviewCampaignRepository extends JpaRepository<AccessReviewCampaign, UUID> {

    List<AccessReviewCampaign> findByWorkspaceId(UUID workspaceId);

    List<AccessReviewCampaign> findByWorkspaceIdAndStatus(UUID workspaceId, CampaignStatus status);

    Optional<AccessReviewCampaign> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
