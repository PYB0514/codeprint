// 프로젝트 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.project;

import com.codeprint.domain.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<Project, UUID> {

    List<Project> findByUserId(UUID userId);

    int countByUserIdAndIsPublicFalse(UUID userId);

    // 특정 유저의 공개 프로젝트 목록 조회
    List<Project> findByUserIdAndIsPublicTrue(UUID userId);

    // GitHub 레포 URL 매칭 — .git 접미사·대소문자 차이를 무시하고 조회
    @Query("select p from Project p where lower(p.githubRepoUrl) = lower(:url) "
            + "or lower(p.githubRepoUrl) = lower(concat(:url, '.git'))")
    List<Project> findByRepoUrlNormalized(@Param("url") String url);

    // PR 게이트 webhook이 연결된(시크릿 발급된) 프로젝트 전체 조회
    List<Project> findByWebhookSecretIsNotNull();
}
