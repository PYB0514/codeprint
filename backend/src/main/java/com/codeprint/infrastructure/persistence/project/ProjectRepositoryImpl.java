// 프로젝트 도메인 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.project;

import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProjectRepositoryImpl implements ProjectRepository {

    private final ProjectJpaRepository jpa;

    @Override
    public Project save(Project project) {
        return jpa.save(project);
    }

    @Override
    public Optional<Project> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<Project> findByUserId(UUID userId) {
        return jpa.findByUserId(userId);
    }

    @Override
    public int countByUserId(UUID userId) {
        return jpa.countByUserId(userId);
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }
}
