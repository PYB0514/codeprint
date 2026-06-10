// 그래프 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.Graph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GraphJpaRepository extends JpaRepository<Graph, UUID> {

    // 프로젝트 ID로 그래프 목록 조회
    List<Graph> findByProjectId(UUID projectId);

    // 프로젝트의 최신 그래프 조회
    Optional<Graph> findTopByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
