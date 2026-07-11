// 그래프 컨트롤러가 analysis·project 컨텍스트를 직접 주입하지 않도록 조율하는 Facade
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.port.AnalysisReadPort;
import com.codeprint.domain.graph.port.GraphUserInfoPort;
import com.codeprint.domain.graph.port.ProjectAccessPort;
import com.codeprint.domain.graph.port.ProjectAccessPort.ProjectAccessView;
import com.codeprint.infrastructure.storage.S3Service;
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
    private final ProjectAccessPort projectAccessPort;
    private final AnalysisReadPort analysisReadPort;
    private final GraphUserInfoPort graphUserInfoPort;
    private final S3Service s3Service;

    // 프로젝트 소유권 확인 — 소유자 아니면 예외
    public void verifyProjectOwnership(UUID projectId, UUID userId) {
        projectAccessPort.verifyOwnership(projectId, userId);
    }

    // 프로젝트 소유권 확인 + view 반환 (소유자 아니면 예외) — 레포 소유 뱃지 판정 등 project 데이터가 필요한 호출용
    public ProjectAccessView getOwnedProject(UUID projectId, UUID userId) {
        return projectAccessPort.getOwnedProject(projectId, userId);
    }

    // 그래프 → 프로젝트 소유권 확인
    public void verifyGraphOwnership(UUID graphId, UUID userId) {
        graphQueryService.findById(graphId)
                .ifPresent(graph -> projectAccessPort.verifyOwnership(graph.getProjectId(), userId));
    }

    // 그래프 읽기 접근 허용 — 프로젝트가 공개면 누구나, 비공개면 소유자만 (노드 코멘트 등 읽기 전용 API용)
    public void verifyGraphReadAccess(UUID graphId, UUID userId) {
        UUID projectId = graphQueryService.findById(graphId)
                .map(Graph::getProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Graph not found: " + graphId));
        try {
            projectAccessPort.verifyPublic(projectId);
        } catch (IllegalStateException notPublic) {
            if (userId == null) throw notPublic;
            projectAccessPort.verifyOwnership(projectId, userId);
        }
    }

    // 공개 프로젝트 조회 — 비인증 접근용
    public ProjectAccessView getPublicProject(UUID projectId) {
        return projectAccessPort.getPublicProject(projectId);
    }

    // 공개 그래프 소유자의 배경 이미지 presigned URL + 내 레포 여부
    public record PublicOwnerInfo(String bgUrl, boolean ownRepo) {}

    // 공개 그래프 응답에 표시할 소유자 배경이미지·소유 여부 조회 — 소유자 조회 실패 시 기본값
    public PublicOwnerInfo getPublicOwnerInfo(ProjectAccessView project) {
        return graphUserInfoPort.findUserInfo(project.userId())
                .map(info -> new PublicOwnerInfo(s3Service.toPresignedUrl(info.graphBgUrl()), project.isOwnRepo(info.username())))
                .orElse(new PublicOwnerInfo(null, false));
    }

    // 그래프 버전 목록에 브랜치명을 포함하여 반환
    public List<Map<String, Object>> getGraphVersionsWithBranch(UUID projectId, UUID userId) {
        projectAccessPort.verifyOwnership(projectId, userId);
        return graphQueryService.findAllByProject(projectId).stream()
                .map(graph -> {
                    String branch = getBranchSafely(graph);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("graphId", graph.getId().toString());
                    item.put("createdAt", graph.getCreatedAt().toString());
                    item.put("branch", branch);
                    item.put("pinnedSlot", graph.getPinnedSlot());
                    return item;
                })
                .toList();
    }

    // analysis에서 브랜치 조회 — 분석 없으면 "default"
    private String getBranchSafely(Graph graph) {
        return analysisReadPort.findBranch(graph.getAnalysisId()).orElse("default");
    }
}
