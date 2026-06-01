// 프로젝트 도메인 Repository 인터페이스
package com.codeprint.domain.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findById(UUID id);

    List<Project> findByUserId(UUID userId);

    int countByUserId(UUID userId);

    void deleteById(UUID id);
}
