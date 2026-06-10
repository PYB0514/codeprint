// 엣지 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.Edge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EdgeJpaRepository extends JpaRepository<Edge, UUID> {

    // 그래프 ID로 엣지 조회
    List<Edge> findByGraphId(UUID graphId);
}
