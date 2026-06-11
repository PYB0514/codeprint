// 프로젝트 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.project;

import com.codeprint.domain.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<Project, UUID> {

    List<Project> findByUserId(UUID userId);

    int countByUserId(UUID userId);

    // 특정 유저의 공개 프로젝트 목록 조회
    List<Project> findByUserIdAndIsPublicTrue(UUID userId);
}
