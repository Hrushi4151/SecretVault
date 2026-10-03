package com.secretvault.repository.repository;

import com.secretvault.repository.entity.SecretFinding;
import com.secretvault.repository.model.RepoFindingSeverity;
import com.secretvault.repository.model.RepoFindingStatus;
import com.secretvault.repository.model.SecretType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecretFindingRepository extends JpaRepository<SecretFinding, UUID>, JpaSpecificationExecutor<SecretFinding> {

    Optional<SecretFinding> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<SecretFinding> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Page<SecretFinding> findByWorkspaceIdAndRepositoryId(UUID workspaceId, UUID repositoryId, Pageable pageable);

    Page<SecretFinding> findByWorkspaceIdAndStatus(UUID workspaceId, RepoFindingStatus status, Pageable pageable);

    Page<SecretFinding> findByWorkspaceIdAndSeverity(UUID workspaceId, RepoFindingSeverity severity, Pageable pageable);

    Optional<SecretFinding> findByWorkspaceIdAndRepositoryIdAndFingerprint(
            UUID workspaceId, UUID repositoryId, String fingerprint);

    List<SecretFinding> findByWorkspaceIdAndFingerprint(UUID workspaceId, String fingerprint);

    List<SecretFinding> findByFingerprint(String fingerprint);

    long countByWorkspaceId(UUID workspaceId);

    long countByWorkspaceIdAndStatus(UUID workspaceId, RepoFindingStatus status);

    long countByWorkspaceIdAndSeverity(UUID workspaceId, RepoFindingSeverity severity);

    long countByWorkspaceIdAndStatusIn(UUID workspaceId, List<RepoFindingStatus> statuses);

    long countByWorkspaceIdAndSeverityAndStatusIn(
            UUID workspaceId, RepoFindingSeverity severity, List<RepoFindingStatus> statuses);

    @Query("SELECT f.secretType, COUNT(f) FROM SecretFinding f WHERE f.workspaceId = :workspaceId GROUP BY f.secretType")
    List<Object[]> countBySecretTypeForWorkspace(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT f.severity, COUNT(f) FROM SecretFinding f WHERE f.workspaceId = :workspaceId AND f.status IN :statuses GROUP BY f.severity")
    List<Object[]> countBySeverityAndActiveStatuses(
            @Param("workspaceId") UUID workspaceId, @Param("statuses") List<RepoFindingStatus> statuses);

    List<SecretFinding> findByWorkspaceIdAndRepositoryIdAndStatusIn(
            UUID workspaceId, UUID repositoryId, List<RepoFindingStatus> statuses);
}
