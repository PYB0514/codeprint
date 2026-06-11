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

    // 프로젝트 엔티티를 저장하고 반환
    @Override
    public Project save(Project project) {
        return jpa.save(project);
    }

    // UUID로 프로젝트 조회
    @Override
    public Optional<Project> findById(UUID id) {
        return jpa.findById(id);
    }

    // 사용자 ID로 프로젝트 목록 조회
    @Override
    public List<Project> findByUserId(UUID userId) {
        return jpa.findByUserId(userId);
    }

    // 사용자의 프로젝트 수 조회
    @Override
    public int countByUserId(UUID userId) {
        return jpa.countByUserId(userId);
    }

    // UUID로 프로젝트 삭제
    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    // 전체 프로젝트 수 반환
    @Override
    public long count() {
        return jpa.count();
    }

    // 특정 유저의 공개 프로젝트 목록 조회
    @Override
    public List<Project> findPublicByUserId(UUID userId) {
        return jpa.findByUserIdAndIsPublicTrue(userId);
    }
}
