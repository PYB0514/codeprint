// 벤치 파이프라인에서 GraphBuilder를 DB 없이 구동하기 위한 더미 — 실제 프로젝트 소유자 개념이 없어 항상 미조회 처리
package com.codeprint.bench;

import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class NoOpProjectRepository implements ProjectRepository {
    @Override public Project save(Project project) { throw new UnsupportedOperationException(); }
    @Override public Optional<Project> findById(UUID id) { return Optional.empty(); }
    @Override public List<Project> findByUserId(UUID userId) { return List.of(); }
    @Override public List<Project> findByRepoUrl(String repoHttpsUrl) { return List.of(); }
    @Override public int countPrivateByUserId(UUID userId) { return 0; }
    @Override public List<Project> findPublicByUserId(UUID userId) { return List.of(); }
    @Override public void deleteById(UUID id) { }
    @Override public long count() { return 0; }
    @Override public List<Project> findAllWithPrGateConnected() { return List.of(); }
}
