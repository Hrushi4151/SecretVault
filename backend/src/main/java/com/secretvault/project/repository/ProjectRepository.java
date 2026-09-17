package com.secretvault.project.repository;

import com.secretvault.project.entity.Project;
import com.secretvault.project.entity.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByWorkspaceId(UUID workspaceId);

    List<Project> findByWorkspaceIdAndStatus(UUID workspaceId, ProjectStatus status);

    Optional<Project> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Project> findByWorkspaceIdAndSlug(UUID workspaceId, String slug);

    boolean existsByWorkspaceIdAndSlug(UUID workspaceId, String slug);

    boolean existsByWorkspaceIdAndSlugAndIdNot(UUID workspaceId, String slug, UUID id);
}
