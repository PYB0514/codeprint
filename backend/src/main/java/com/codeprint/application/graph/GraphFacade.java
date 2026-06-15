// 그래프 컨트롤러가 analysis·project 컨텍스트를 직접 주입하지 않도록 조율하는 Facade
package com.codeprint.application.graph;

import com.codeprint.application.analysis.AnalysisApplicationService;
import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.domain.graph.Graph;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GraphFacade {

    private final GraphQueryService graphQueryService;
    private final ProjectQueryService projectQueryService;
    private final AnalysisApplicationService analysisApplicationService;

    // 프로젝트 소유권 확인 — 소유자 아니면 예외
    public void verifyProjectOwnership(UUID projectId, UUID userId) {
        projectQueryService.getProject(projectId, userId);
    }

    // 그래프 → 프로젝트 소유권 확인
    public void verifyGraphOwnership(UUID graphId, UUID userId) {
        graphQueryService.findById(graphId)
                .ifPresent(graph -> projectQueryService.getProject(graph.getProjectId(), userId));
    }

    // 공개 프로젝트 조회 — 비인증 접근용
    public com.codeprint.domain.project.Project getPublicProject(UUID projectId) {
        return projectQueryService.getPublicProject(projectId);
    }

    // 그래프 버전 목록에 브랜치명을 포함하여 반환
    public List<Map<String, Object>> getGraphVersionsWithBranch(UUID projectId, UUID userId) {
        projectQueryService.getProject(projectId, userId);
        return graphQueryService.findAllByProject(projectId).stream()
                .map(graph -> {
                    String branch = getBranchSafely(graph);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("graphId", graph.getId().toString());
                    item.put("createdAt", graph.getCreatedAt().toString());
                    item.put("branch", branch);
                    return item;
                })
                .toList();
    }

    // analysis에서 브랜치 조회 — 분석 없으면 "default"
    private String getBranchSafely(Graph graph) {
        try {
            String branch = analysisApplicationService.getAnalysis(graph.getAnalysisId()).getBranch();
            return branch != null ? branch : "default";
        } catch (Exception e) {
            return "default";
        }
    }
}
