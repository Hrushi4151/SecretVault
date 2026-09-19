package com.secretvault.access.review.repository;

import com.secretvault.access.review.entity.AccessReviewItem;
import com.secretvault.access.review.entity.ReviewDecision;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessReviewItemRepository extends JpaRepository<AccessReviewItem, UUID> {

    List<AccessReviewItem> findByCampaignId(UUID campaignId);

    List<AccessReviewItem> findByCampaignIdAndDecision(UUID campaignId, ReviewDecision decision);

    Optional<AccessReviewItem> findByIdAndCampaignId(UUID id, UUID campaignId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM AccessReviewItem i WHERE i.id = :id AND i.campaignId = :campaignId")
    Optional<AccessReviewItem> findByIdAndCampaignIdForUpdate(@Param("id") UUID id, @Param("campaignId") UUID campaignId);

    long countByCampaignId(UUID campaignId);

    long countByCampaignIdAndDecisionNot(UUID campaignId, ReviewDecision decision);
}
