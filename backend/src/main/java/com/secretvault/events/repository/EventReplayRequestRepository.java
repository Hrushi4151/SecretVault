package com.secretvault.events.repository;

import com.secretvault.events.entity.EventReplayRequest;
import com.secretvault.events.entity.ReplayStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventReplayRequestRepository extends JpaRepository<EventReplayRequest, UUID> {

    Page<EventReplayRequest> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    List<EventReplayRequest> findByStatusOrderByCreatedAtAsc(ReplayStatus status);
}
