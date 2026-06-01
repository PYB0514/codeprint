// 프로젝트 Aggregate Root 엔티티
package com.codeprint.domain.project;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "github_repo_url", nullable = false, length = 500)
    private String githubRepoUrl;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Project create(UUID userId, String githubRepoUrl, String name, String description) {
        Project project = new Project();
        project.id = UUID.randomUUID();
        project.userId = userId;
        project.githubRepoUrl = githubRepoUrl;
        project.name = name;
        project.description = description;
        project.isPublic = false;
        project.createdAt = Instant.now();
        project.updatedAt = Instant.now();
        return project;
    }

    public void makePublic() {
        this.isPublic = true;
        this.updatedAt = Instant.now();
    }

    public void makePrivate() {
        this.isPublic = false;
        this.updatedAt = Instant.now();
    }

    public ProjectId getProjectId() {
        return ProjectId.of(id);
    }
}
