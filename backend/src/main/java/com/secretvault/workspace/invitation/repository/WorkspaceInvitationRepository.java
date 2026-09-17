package com.secretvault.workspace.invitation.repository;

import com.secretvault.workspace.invitation.entity.InvitationStatus;
import com.secretvault.workspace.invitation.entity.WorkspaceInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    List<WorkspaceInvitation> findByWorkspaceId(UUID workspaceId);

    List<WorkspaceInvitation> findByWorkspaceIdAndStatus(UUID workspaceId, InvitationStatus status);

    Optional<WorkspaceInvitation> findByTokenHash(String tokenHash);

    Optional<WorkspaceInvitation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndEmailAndStatus(UUID workspaceId, String email, InvitationStatus status);
}
