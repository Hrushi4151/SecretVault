package com.secretvault.workspace.invitation.repository;

import com.secretvault.workspace.invitation.entity.InvitationStatus;
import com.secretvault.workspace.invitation.entity.WorkspaceInvitation;
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
public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    List<WorkspaceInvitation> findByWorkspaceId(UUID workspaceId);

    List<WorkspaceInvitation> findByWorkspaceIdAndStatus(UUID workspaceId, InvitationStatus status);

    List<WorkspaceInvitation> findByEmailIgnoreCaseAndStatus(String email, InvitationStatus status);

    Optional<WorkspaceInvitation> findByTokenHash(String tokenHash);

    Optional<WorkspaceInvitation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<WorkspaceInvitation> findByWorkspaceIdAndEmailIgnoreCaseAndStatus(UUID workspaceId, String email, InvitationStatus status);

    boolean existsByWorkspaceIdAndEmailIgnoreCaseAndStatus(UUID workspaceId, String email, InvitationStatus status);

    boolean existsByWorkspaceIdAndEmailAndStatus(UUID workspaceId, String email, InvitationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM WorkspaceInvitation i WHERE i.id = :id")
    Optional<WorkspaceInvitation> findWithLockById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM WorkspaceInvitation i WHERE i.tokenHash = :tokenHash")
    Optional<WorkspaceInvitation> findWithLockByTokenHash(@Param("tokenHash") String tokenHash);
}
