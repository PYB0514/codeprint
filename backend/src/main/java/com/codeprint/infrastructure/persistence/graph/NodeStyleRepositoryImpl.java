// NodeStyleRepository 구현체 — JPA 위임
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.NodeStyle;
import com.codeprint.domain.graph.NodeStyleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NodeStyleRepositoryImpl implements NodeStyleRepository {

    private final NodeStyleJpaRepository jpa;

    @Override
    public List<NodeStyle> findByGraphId(UUID graphId) {
        return jpa.findByGraphId(graphId);
    }

    @Override
    public Optional<NodeStyle> findByGraphIdAndNodeId(UUID graphId, UUID nodeId) {
        return jpa.findByGraphIdAndNodeId(graphId, nodeId);
    }

    @Override
    public NodeStyle save(NodeStyle style) {
        return jpa.save(style);
    }

    @Override
    @Transactional
    public void deleteByGraphIdAndNodeId(UUID graphId, UUID nodeId) {
        jpa.deleteByGraphIdAndNodeId(graphId, nodeId);
    }
}
