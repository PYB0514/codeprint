// 그래프 조회 애플리케이션 서비스 (읽기 전용)
package com.codeprint.application.graph;

import com.codeprint.domain.graph.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GraphQueryService {

    private final GraphRepository graphRepository;

    // 프로젝트의 가장 최근 그래프를 조회
    public Optional<Graph> findLatestByProject(UUID projectId) {
        List<Graph> graphs = graphRepository.findByProjectId(projectId);
        if (graphs.isEmpty()) return Optional.empty();
        return graphs.stream()
                .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
    }

    // 그래프 ID로 노드 목록 조회
    public List<Node> getNodes(UUID graphId) {
        return graphRepository.findNodesByGraphId(graphId);
    }

    // 그래프 ID로 엣지 목록 조회
    public List<Edge> getEdges(UUID graphId) {
        return graphRepository.findEdgesByGraphId(graphId);
    }
}
