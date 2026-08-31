// 그래프 조회 애플리케이션 서비스 (읽기 전용)
package com.codeprint.application.graph;

import com.codeprint.domain.graph.*;
import com.codeprint.domain.graph.port.ProjectAccessPort;
import com.codeprint.shared.gate.GatePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
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
    private final GraphWarningStore graphWarningStore;
    private final ArchitectureIntentService architectureIntentService;
    private final ProjectAccessPort projectAccessPort;

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

    // 그래프 경고 감지 결과 — 인메모리 캐시(10분) + AUTO 정책이면 graphs.warnings 컬럼에 영속화(콜드스타트 재계산 회피)
    // 의도 아키텍처가 있으면 INTENT_DRIFT까지 함께 검사
    @Cacheable(value = "graphWarnings", key = "#graphId")
    public List<Map<String, Object>> getWarnings(UUID graphId) {
        List<Node> nodes = getNodes(graphId);
        List<Edge> edges = getEdges(graphId);
        UUID projectId = graphRepository.findById(graphId).map(Graph::getProjectId).orElse(null);
        ArchitectureIntent intent = projectId == null ? null
                : architectureIntentService.findByProjectId(projectId).orElse(null);
        GatePolicy gatePolicy = projectId == null ? GatePolicy.AUTO : projectAccessPort.getProjectById(projectId)
                .map(ProjectAccessPort.ProjectAccessView::gatePolicy)
                .orElse(GatePolicy.AUTO);
        // 명시 override(DDD/LAYERED)는 온디맨드 계산 — 영속 컬럼은 AUTO 기준으로만 저장·신뢰
        if (gatePolicy != GatePolicy.AUTO) {
            return graphWarningService.detect(nodes, edges, intent, gatePolicy);
        }
        return graphWarningStore.load(graphId).orElseGet(() -> {
            List<Map<String, Object>> computed = graphWarningService.detect(nodes, edges, intent, GatePolicy.AUTO);
            graphWarningStore.save(graphId, computed);
            return computed;
        });
    }

    // 경고 캐시 전체 무효화 — 프로젝트 설정(게이트 정책 등)이 바뀌어 detect() 결과가 달라질 때 사용
    // (ArchitectureIntentService의 기존 전체무효화 관례와 동일)
    @CacheEvict(value = "graphWarnings", allEntries = true)
    public void evictWarningsCache() {
    }

}
