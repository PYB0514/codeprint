// 그래프 조회 애플리케이션 서비스 (읽기 전용)
package com.codeprint.application.graph;

import com.codeprint.domain.graph.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GraphQueryService {

    private final GraphRepository graphRepository;
    private final GraphWarningService graphWarningService;

    // 프로젝트의 가장 최근 그래프를 조회
    public Optional<Graph> findLatestByProject(UUID projectId) {
        return graphRepository.findByProjectId(projectId).stream()
                .max(Comparator.comparing(Graph::getCreatedAt));
    }

    // 프로젝트의 모든 그래프를 최신순으로 조회
    public List<Graph> findAllByProject(UUID projectId) {
        return graphRepository.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(Graph::getCreatedAt).reversed())
                .toList();
    }

    // graphId로 특정 그래프를 조회
    public Optional<Graph> findById(UUID graphId) {
        return graphRepository.findById(graphId);
    }

    // 그래프 ID로 노드 목록 조회 — 동일 graphId 재조회 시 캐시 반환
    @Cacheable(value = "graphNodes", key = "#graphId")
    public List<Node> getNodes(UUID graphId) {
        return graphRepository.findNodesByGraphId(graphId);
    }

    // 그래프 ID로 엣지 목록 조회 — 동일 graphId 재조회 시 캐시 반환
    @Cacheable(value = "graphEdges", key = "#graphId")
    public List<Edge> getEdges(UUID graphId) {
        return graphRepository.findEdgesByGraphId(graphId);
    }

    // 그래프 경고 감지 결과 캐싱 — detect()는 CPU 집약적이므로 graphId 기준으로 10분 캐시
    @Cacheable(value = "graphWarnings", key = "#graphId")
    public List<Map<String, Object>> getWarnings(UUID graphId) {
        List<Node> nodes = getNodes(graphId);
        List<Edge> edges = getEdges(graphId);
        return graphWarningService.detect(nodes, edges);
    }

}
