// 그래프 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.Graph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GraphJpaRepository extends JpaRepository<Graph, UUID> {

    List<Graph> findByProjectId(UUID projectId);

    Optional<Graph> findTopByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
