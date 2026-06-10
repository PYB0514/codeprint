// NodeStyle JPA Repository — node_styles 테이블 접근
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.NodeStyle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeStyleJpaRepository extends JpaRepository<NodeStyle, UUID> {

    List<NodeStyle> findByGraphId(UUID graphId);

    Optional<NodeStyle> findByGraphIdAndNodeId(UUID graphId, UUID nodeId);

    void deleteByGraphIdAndNodeId(UUID graphId, UUID nodeId);
}
