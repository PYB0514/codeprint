// community 컨트롤러가 graph·project 컨텍스트를 직접 주입하지 않도록 조율하는 Facade
package com.codeprint.application.community;

import com.codeprint.application.graph.GraphQueryService;
import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Node;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityFacade {

    private final GraphQueryService graphQueryService;
    private final ProjectQueryService projectQueryService;

    // 공개 프로젝트의 레포 URL 반환 — 비공개이면 empty
    public Optional<String> findPublicRepoUrl(UUID graphId, UUID userId) {
        return graphQueryService.findById(graphId)
                .flatMap(graph -> {
                    try {
                        var project = projectQueryService.getProject(graph.getProjectId(), userId);
                        return project.isPublic()
                                ? Optional.ofNullable(project.getGithubRepoUrl())
                                : Optional.empty();
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                });
    }

    // 게시글에 첨부된 그래프의 노드·엣지 반환
    public Optional<GraphSnapshot> getGraphSnapshot(UUID graphId) {
        return graphQueryService.findById(graphId)
                .map(graph -> new GraphSnapshot(
                        graph.getId(),
                        graphQueryService.getNodes(graph.getId()),
                        graphQueryService.getEdges(graph.getId())
                ));
    }

    // 그래프 노드·엣지 스냅샷
    public record GraphSnapshot(UUID graphId, List<Node> nodes, List<Edge> edges) {}
}
