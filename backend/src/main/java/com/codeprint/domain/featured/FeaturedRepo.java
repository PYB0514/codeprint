// 오늘의 공개레포 큐레이션 후보 Aggregate Root 엔티티
package com.codeprint.domain.featured;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "featured_repos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeaturedRepo {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "repo_full_name", nullable = false, length = 200)
    private String repoFullName;

    @Column(nullable = false, length = 50)
    private String language;

    @Column(name = "project_id", columnDefinition = "uuid")
    private UUID projectId;

    @Column
    private Integer stars;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "last_featured_at")
    private Instant lastFeaturedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 선정 처리 — 최초 노출이면 projectId를 채우고, 매번 노출 시각·star·description을 갱신
    public void markFeatured(UUID projectId, Integer stars, String description, Instant now) {
        if (this.projectId == null) {
            this.projectId = projectId;
        }
        this.stars = stars;
        this.description = description;
        this.lastFeaturedAt = now;
    }

    // GitHub 클론용 HTTPS URL로 변환
    public String toGithubRepoUrl() {
        return "https://github.com/" + repoFullName;
    }
}
