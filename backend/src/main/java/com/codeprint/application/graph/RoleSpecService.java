// BYOK로 무주석·고복잡도 함수의 역할 요약(레이어A "역할 명세서")을 생성해 별도 MD 섹션으로 반환
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.EdgeType;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.GraphRepository;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.domain.graph.port.AiKeyPort;
import com.codeprint.domain.graph.port.AnalysisReadPort;
import com.codeprint.domain.graph.port.ProjectAccessPort;
import com.codeprint.shared.ai.AiProvider;
import com.codeprint.infrastructure.ai.AiService;
import com.codeprint.infrastructure.github.GitHubApiClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleSpecService {

    // 한 번 생성 요청당 LLM 호출 상한 — 사용자 BYOK 비용이라도 무제한 반복 호출은 지연·예측불가 과금 리스크
    private static final int MAX_TARGET_NODES = 15;
    // 함수 본문 스니펫에 포함할 대상 줄 앞뒤 여유 줄 수
    private static final int SNIPPET_CONTEXT_LINES = 25;

    private final AiKeyPort aiKeyPort;
    private final GraphQueryService graphQueryService;
    private final GraphRepository graphRepository;
    private final AnalysisReadPort analysisReadPort;
    private final ProjectAccessPort projectAccessPort;
    private final GitHubApiClient gitHubApiClient;
    private final List<AiService> aiServices;

    private Map<AiProvider, AiService> servicesByProvider;

    @PostConstruct
    void init() {
        servicesByProvider = aiServices.stream().collect(Collectors.toMap(AiService::provider, s -> s));
    }

    // 무주석+HIGH_FAN_OUT 노드에 대해 AI 역할 요약 MD 섹션 생성 — 어느 단계든 실패하면 빈 문자열(최선노력, 본문 생성 자체는 항상 성공)
    @Transactional(readOnly = true)
    public String generateSection(UUID projectId, UUID graphId, UUID userId, AiProvider provider) {
        if (provider == null) return "";
        String apiKey = aiKeyPort.findPlainKey(userId, provider).orElse(null);
        if (apiKey == null) return "";
        AiService aiService = servicesByProvider.get(provider);
        if (aiService == null) return "";

        try {
            Graph graph = graphRepository.findById(graphId).orElse(null);
            if (graph == null || !graph.getProjectId().equals(projectId)) return "";
            String sha = analysisReadPort.findCommitSha(graph.getAnalysisId()).orElse(null);
            String repoUrl = projectAccessPort.findGithubRepoUrl(projectId).orElse(null);
            if (sha == null || repoUrl == null) return "";

            List<Node> nodes = graphQueryService.getNodes(graphId);
            List<Edge> edges = graphQueryService.getEdges(graphId);
            List<Node> targets = selectTargetNodes(nodes, graphId);
            if (targets.isEmpty()) return "";

            Map<UUID, Node> nodesById = nodes.stream().collect(Collectors.toMap(Node::getId, n -> n));
            List<String> entries = new ArrayList<>();
            for (Node target : targets) {
                String entry = summarizeOne(target, nodesById, edges, repoUrl, sha, apiKey, aiService);
                if (entry != null) entries.add(entry);
            }
            if (entries.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("\n\n## AI 추정 역할 요약 (미검증)\n\n");
            sb.append("> 아래는 주석이 없는 함수에 대해 사용자 본인의 LLM 키로 생성한 추정 요약입니다. ");
            sb.append("실제 코드로 확정된 구조적 사실이 아니므로 참고용으로만 사용하세요.\n\n");
            entries.forEach(sb::append);
            return sb.toString();
        } catch (Exception e) {
            log.warn("역할 명세서 생성 실패(최선노력, 무시): projectId={} graphId={}", projectId, graphId, e);
            return "";
        }
    }

    // 무주석 + HIGH_FAN_OUT 판정된 FUNCTION 노드만 대상(기존 판정 재사용, 최대 MAX_TARGET_NODES개로 제한)
    private List<Node> selectTargetNodes(List<Node> nodes, UUID graphId) {
        Set<UUID> highFanOutIds = graphQueryService.getWarnings(graphId).stream()
                .filter(w -> "HIGH_FAN_OUT".equals(w.get("type")))
                .flatMap(w -> ((List<?>) w.getOrDefault("nodeIds", List.of())).stream())
                .map(id -> UUID.fromString(id.toString()))
                .collect(Collectors.toSet());

        return nodes.stream()
                .filter(n -> n.getType() == NodeType.FUNCTION)
                .filter(n -> highFanOutIds.contains(n.getId()))
                .filter(n -> isBlank(commentOf(n)))
                .limit(MAX_TARGET_NODES)
                .toList();
    }

    // 함수 본문 스니펫 + 1홉 이웃(호출자/피호출자) 기존 주석을 프롬프트로 조립해 LLM 호출, 실패 시 null(해당 노드만 건너뜀)
    private String summarizeOne(Node target, Map<UUID, Node> nodesById, List<Edge> edges,
                                 String repoUrl, String sha, String apiKey, AiService aiService) {
        Object lineObj = target.getMetadata() != null ? target.getMetadata().get("line") : null;
        if (!(lineObj instanceof Number lineNum)) return null;
        String content = gitHubApiClient.fetchFileContent(repoUrl, target.getFilePath(), sha);
        String snippet = GitHubApiClient.extractSnippet(content, lineNum.intValue(), SNIPPET_CONTEXT_LINES);
        if (snippet == null) return null;

        String neighbors = neighborContext(target, nodesById, edges);
        String prompt = """
                다음은 %s 코드베이스의 함수입니다. 이 함수가 무엇을 하는지 한 문장(한국어, 100자 이내)으로만 답하세요. 설명이나 서론 없이 문장 하나만 출력하세요.

                함수명: %s
                파일: %s
                %s
                코드:
                ```%s
                %s
                ```
                """.formatted(target.getLanguage(), target.getName(), target.getFilePath(), neighbors,
                target.getLanguage() != null ? target.getLanguage() : "", snippet);

        try {
            String summary = aiService.generate(apiKey, prompt).trim();
            if (summary.isBlank()) return null;
            return "- `" + target.getName() + "`(" + target.getFilePath() + ") — " + summary + "\n";
        } catch (Exception e) {
            log.warn("역할 명세서 개별 노드 생성 실패(최선노력, 건너뜀): nodeId={}", target.getId());
            return null;
        }
    }

    // 대상 노드와 직접 연결된(호출/피호출) 이웃 중 기존 주석이 있는 것만 컨텍스트로 제공 — 함수 본문은 포함하지 않음(페이로드 최소화)
    private String neighborContext(Node target, Map<UUID, Node> nodesById, List<Edge> edges) {
        List<String> lines = new ArrayList<>();
        for (Edge e : edges) {
            if (e.getType() != EdgeType.FUNCTION_CALL) continue;
            UUID neighborId = null;
            String direction = null;
            if (e.getSourceNodeId().equals(target.getId())) {
                neighborId = e.getTargetNodeId();
                direction = "호출";
            } else if (e.getTargetNodeId().equals(target.getId())) {
                neighborId = e.getSourceNodeId();
                direction = "피호출(호출자)";
            }
            if (neighborId == null) continue;
            Node neighbor = nodesById.get(neighborId);
            if (neighbor == null) continue;
            String comment = commentOf(neighbor);
            if (isBlank(comment)) continue;
            lines.add("- " + direction + ": " + neighbor.getName() + " — " + comment);
        }
        return lines.isEmpty() ? "" : "이웃 함수 정보:\n" + String.join("\n", lines) + "\n";
    }

    private String commentOf(Node n) {
        return n.getMetadata() != null && n.getMetadata().get("comment") instanceof String s ? s : null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
