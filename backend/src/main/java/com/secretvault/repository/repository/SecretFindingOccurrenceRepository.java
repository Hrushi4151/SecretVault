package com.secretvault.repository.repository;

import com.secretvault.repository.entity.SecretFindingOccurrence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecretFindingOccurrenceRepository extends JpaRepository<SecretFindingOccurrence, UUID> {

    List<SecretFindingOccurrence> findByFindingId(UUID findingId);

    Page<SecretFindingOccurrence> findByFindingId(UUID findingId, Pageable pageable);

    List<SecretFindingOccurrence> findByScanId(UUID scanId);

    boolean existsByFindingIdAndCommitShaAndFilePathAndLineNumber(
            UUID findingId, String commitSha, String filePath, Integer lineNumber);

    long countByFindingId(UUID findingId);
}
