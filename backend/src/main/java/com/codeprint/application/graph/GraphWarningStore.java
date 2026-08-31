// 그래프 구조 경고 사전계산 결과의 영속 저장·무효화를 담당하는 서비스
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.GraphRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GraphWarningStore {

    private final GraphRepository graphRepository;

    // 사전계산된 경고 조회 — 미계산(null)이면 empty. 빈 리스트([])는 "경고 없음"으로 present
    @Transactional(readOnly = true)
    public Optional<List<Map<String, Object>>> load(UUID graphId) {
        return graphRepository.findById(graphId).map(Graph::getWarnings);
    }

    // 계산된 경고를 그래프에 저장 — readOnly 트랜잭션(getWarnings) 내부에서 호출되므로 별도 트랜잭션 강제
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(UUID graphId, List<Map<String, Object>> warnings) {
        graphRepository.findById(graphId).ifPresent(g -> {
            g.cacheWarnings(warnings);
            graphRepository.save(g);
        });
    }

    // 프로젝트의 모든 그래프 경고 캐시 무효화 — 의도 아키텍처 변경 시(호출자 트랜잭션에 합류)
    @Transactional
    public void invalidateProject(UUID projectId) {
        graphRepository.findByProjectId(projectId).forEach(g -> {
            if (g.getWarnings() != null) {
                g.cacheWarnings(null);
                graphRepository.save(g);
            }
        });
    }
}
