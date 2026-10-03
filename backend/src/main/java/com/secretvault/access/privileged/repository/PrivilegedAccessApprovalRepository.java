package com.secretvault.access.privileged.repository;

import com.secretvault.access.privileged.entity.PrivilegedAccessApproval;
import com.secretvault.access.privileged.model.ApprovalDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrivilegedAccessApprovalRepository extends JpaRepository<PrivilegedAccessApproval, UUID> {

    List<PrivilegedAccessApproval> findByRequestId(UUID requestId);

    Optional<PrivilegedAccessApproval> findByRequestIdAndApproverId(UUID requestId, UUID approverId);

    @Query("SELECT COUNT(DISTINCT a.approverId) FROM PrivilegedAccessApproval a WHERE a.requestId = :requestId AND a.decision = :decision")
    long countDistinctApproversByDecision(
            @Param("requestId") UUID requestId,
            @Param("decision") ApprovalDecision decision
    );

    @Query("SELECT a.approverId FROM PrivilegedAccessApproval a WHERE a.requestId = :requestId AND a.decision = 'APPROVED'")
    List<UUID> findApprovedApproverIds(@Param("requestId") UUID requestId);
}
