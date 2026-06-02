// 노드 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.Node;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NodeJpaRepository extends JpaRepository<Node, UUID> {

    List<Node> findByGraphId(UUID graphId);
}
