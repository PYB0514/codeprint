// ArchitectureIntentRepository JPA 구현체 — 프로젝트당 의도 아키텍처 upsert
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.ArchitectureIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ArchitectureIntentRepositoryImpl implements ArchitectureIntentRepository {

    private final ArchitectureIntentJpaRepository jpa;

    // 프로젝트 ID로 의도 JSON 조회
    @Override
    public Optional<String> findJsonByProjectId(UUID projectId) {
        return jpa.findById(projectId).map(ArchitectureIntentEntity::getIntentJson);
    }

    // 의도 JSON을 저장 — 없으면 삽입, 있으면 갱신
    @Override
    public void upsert(UUID projectId, String intentJson) {
        jpa.findById(projectId).ifPresentOrElse(
            e -> { e.update(intentJson); jpa.save(e); },
            () -> jpa.save(ArchitectureIntentEntity.of(projectId, intentJson))
        );
    }

    // 프로젝트의 의도 삭제
    @Override
    public void deleteByProjectId(UUID projectId) {
        jpa.deleteById(projectId);
    }
}
