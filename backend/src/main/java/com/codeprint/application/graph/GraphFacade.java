// 그래프 컨트롤러가 analysis·project 컨텍스트를 직접 주입하지 않도록 조율하는 Facade
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.port.AnalysisReadPort;
import com.codeprint.domain.graph.port.GraphUserInfoPort;
import com.codeprint.domain.graph.port.ProjectAccessPort;
import com.codeprint.domain.graph.port.ProjectAccessPort.ProjectAccessView;
import com.codeprint.infrastructure.storage.S3Service;
import com.codeprint.shared.gate.GatePolicy;
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
    private final GraphWarningService graphWarningService;
    private final ProjectAccessPort projectAccessPort;
    private final AnalysisReadPort analysisReadPort;
    private final GraphUserInfoPort graphUserInfoPort;
    private final S3Service s3Service;

    // 프로젝트 접근 권한 확인 — 소유자 또는 팀 멤버 아니면 예외
    public void verifyProjectAccess(UUID projectId, UUID userId) {
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

    // 접근 검증 없는 원시 조회 — 호출자가 이미 별도 인가를 검증한 흐름 전용(MCP 팀 교차조회용)
    public java.util.Optional<ProjectAccessView> getProjectById(UUID projectId) {
        return projectAccessPort.getProjectById(projectId);
    }

    // 프로젝트 읽기 접근 허용 — 공개면 누구나, 비공개면 소유자만 (오탐 신고 등 조회 권한만 필요한 API용)
    public void verifyProjectReadAccess(UUID projectId, UUID userId) {
        try {
            projectAccessPort.verifyPublic(projectId);
        } catch (IllegalStateException notPublic) {
            if (userId == null) throw notPublic;
            projectAccessPort.verifyOwnership(projectId, userId);
        }
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

    // 소유권 확인 후 현재 적용 중인 게이트 테마(DDD/LAYERED/GENERIC) + 규칙 목록 조회(1~2단계 표면화)
    public GraphWarningService.ActiveTheme getGateTheme(UUID projectId, UUID userId) {
        ProjectAccessView project = projectAccessPort.getOwnedProject(projectId, userId);
        Graph graph = graphQueryService.findLatestByProject(projectId)
                .orElseThrow(() -> new IllegalArgumentException("No graph found for project: " + projectId));
        List<Node> nodes = graphQueryService.getNodes(graph.getId());
        List<Edge> edges = graphQueryService.getEdges(graph.getId());
        return graphWarningService.detectActiveTheme(nodes, edges, project.gatePolicy());
    }

    // 소유권 확인 후 게이트 정책(AUTO/DDD/LAYERED) 전환 + 경고 캐시 무효화(detect() 결과가 바로 반영되도록) + 갱신된 테마 반환
    public GraphWarningService.ActiveTheme setGatePolicy(UUID projectId, UUID userId, GatePolicy policy) {
        projectAccessPort.verifyOwnership(projectId, userId);
        projectAccessPort.setGatePolicy(projectId, userId, policy);
        graphQueryService.evictWarningsCache();
        return getGateTheme(projectId, userId);
    }
}
