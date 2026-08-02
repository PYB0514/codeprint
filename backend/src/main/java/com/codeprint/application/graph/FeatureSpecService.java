// BYOK로 바운디드 컨텍스트 단위 "기능명세"(레이어B)를 생성해 별도 MD 섹션으로 반환 — 코드 품질(주석 유무)과 무관하게 항상 시도
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.domain.graph.port.AiKeyPort;
import com.codeprint.shared.ai.AiProvider;
import com.codeprint.infrastructure.ai.AiService;
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
public class FeatureSpecService {

    // 컨텍스트 1개짜리(파일 1개) 종합은 성립하지 않아 스코프에서 제외(적대적 검증 지적 반영)
    private static final int MIN_FILES_PER_CONTEXT = 2;
    // 한 번 생성 요청당 LLM 호출 상한 — 레이어A와 동일 취지(무제한 반복 호출 방지)
    private static final int MAX_CONTEXTS = 10;

    private final AiKeyPort aiKeyPort;
    private final GraphQueryService graphQueryService;
    private final RoleSpecService roleSpecService;
    private final List<AiService> aiServices;

    private Map<AiProvider, AiService> servicesByProvider;

    @PostConstruct
    void init() {
        servicesByProvider = aiServices.stream().collect(Collectors.toMap(AiService::provider, s -> s));
    }

    // 바운디드 컨텍스트별 기능명세 MD 섹션 생성 — 어느 단계든 실패하면 빈 문자열(최선노력)
    @Transactional(readOnly = true)
    public String generateSection(UUID graphId, UUID userId, AiProvider provider) {
        if (provider == null) return "";
        if (aiKeyPort.findPlainKey(userId, provider).isEmpty()) return "";
        AiFailoverClient failover = AiFailoverClient.forUser(provider, userId, aiKeyPort, servicesByProvider);

        try {
            List<Node> nodes = graphQueryService.getNodes(graphId);
            List<Edge> edges = graphQueryService.getEdges(graphId);
            Map<String, List<Node>> byContext = groupFilesByContext(nodes);
            List<Map.Entry<String, List<Node>>> targets = byContext.entrySet().stream()
                    .filter(e -> !"(미분류)".equals(e.getKey()))
                    .filter(e -> e.getValue().size() >= MIN_FILES_PER_CONTEXT)
                    .limit(MAX_CONTEXTS)
                    .toList();
            if (targets.isEmpty()) return "";

            Set<UUID> layerAFlaggedIds = roleSpecService.selectTargetNodeIds(nodes, graphId);

            List<String> entries = new ArrayList<>();
            for (Map.Entry<String, List<Node>> entry : targets) {
                String section = summarizeContext(entry.getKey(), entry.getValue(), nodes, edges, layerAFlaggedIds, failover);
                if (section != null) entries.add(section);
            }
            if (entries.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("\n\n## AI 추정 기능명세 (미검증)\n\n");
            sb.append("> 아래는 각 컨텍스트를 구성하는 파일들을 종합해 사용자 본인의 LLM 키로 생성한 추정 설명입니다. ");
            sb.append("실제 코드로 확정된 구조적 사실이 아니므로 참고용으로만 사용하세요.\n\n");
            if (failover.switched()) {
                sb.append("> ⚠️ ").append(failover.originalProvider()).append(" 호출 실패로 ")
                        .append(failover.currentProvider()).append("로 전환해 생성했습니다.\n\n");
            }
            entries.forEach(sb::append);
            return sb.toString();
        } catch (Exception e) {
            log.warn("기능명세 생성 실패(최선노력, 무시): graphId={}", graphId, e);
            return "";
        }
    }

    // FILE 노드를 바운디드 컨텍스트별로 그룹핑 — BoundedContextResolver 기본 판정 실패 시 featureOf(피처슬라이스)로 폴백(1차 스코프는 이 둘만)
    private Map<String, List<Node>> groupFilesByContext(List<Node> nodes) {
        List<Node> fileNodes = nodes.stream()
                .filter(n -> n.getType() == NodeType.FILE)
                .filter(n -> !isTestOrFixturePath(n.getFilePath()))
                .toList();
        List<String> allPaths = fileNodes.stream().map(this::normalize).toList();
        Set<String> cfContexts = BoundedContextResolver.detectContextFirstContexts(allPaths);

        Map<String, List<Node>> byContext = new TreeMap<>();
        for (Node f : fileNodes) {
            String path = normalize(f.getFilePath());
            String ctx = BoundedContextResolver.functionContextOf(path, cfContexts);
            if (ctx == null) ctx = BoundedContextResolver.featureOf(path);
            byContext.computeIfAbsent(ctx != null ? ctx : "(미분류)", k -> new ArrayList<>()).add(f);
        }
        return byContext;
    }

    // 컨텍스트 내 파일들의 기존 주석+구조적 신호(API/DB 존재, 레이어A 플래그 노드 수)를 종합해 LLM 호출
    private String summarizeContext(String contextName, List<Node> files, List<Node> allNodes, List<Edge> edges,
                                     Set<UUID> layerAFlaggedIds, AiFailoverClient failover) {
        Set<String> filePaths = files.stream().map(Node::getFilePath).collect(Collectors.toSet());

        List<String> fileLines = new ArrayList<>();
        int flaggedCount = 0;
        boolean hasApi = false;
        boolean hasDb = false;
        for (Node n : allNodes) {
            if (n.getFilePath() == null || !filePaths.contains(n.getFilePath())) continue;
            if (n.getType() == NodeType.FILE) {
                String comment = commentOf(n);
                fileLines.add(comment != null ? n.getFilePath() + " — " + comment : n.getFilePath());
            }
            if (n.getType() == NodeType.API_ENDPOINT) hasApi = true;
            if (n.getType() == NodeType.DB_TABLE) hasDb = true;
            if (layerAFlaggedIds.contains(n.getId())) flaggedCount++;
        }
        if (fileLines.isEmpty()) return null;

        String prompt = """
                다음은 한 코드베이스에서 같은 바운디드 컨텍스트("%s")로 묶인 파일 목록입니다. \
                이 파일들이 합쳐서 어떤 기능을 담당하는지 한 문단(한국어, 200자 이내)으로 설명하세요. \
                파일별 주석을 그대로 나열하지 말고, 데이터 흐름·책임 경계·외부 연동 관점에서 종합해서 서술하세요. 설명이나 서론 없이 문단 하나만 출력하세요.

                파일 목록(있는 경우 기존 주석 포함):
                %s
                외부 연동: API 엔드포인트 %s, DB 테이블 %s
                """.formatted(contextName, String.join("\n", fileLines),
                hasApi ? "있음" : "없음", hasDb ? "있음" : "없음");

        try {
            String summary = failover.generate(prompt).trim();
            if (summary.isBlank()) return null;
            String badge = flaggedCount > 0 ? "(레이어A 플래그 노드 " + flaggedCount + "개 포함)" : "";
            return "### " + contextName + " 컨텍스트 " + badge + "\n\n" + summary + "\n\n";
        } catch (Exception e) {
            log.warn("기능명세 개별 컨텍스트 생성 실패(최선노력, 건너뜀): context={}, 원인={}", contextName, e.getMessage());
            return null;
        }
    }

    // 테스트·픽스처 경로 여부 — RepoMapService.isTestOrFixturePath와 같은 기준(레이어 분리로 별도 구현)
    private boolean isTestOrFixturePath(String path) {
        if (path == null) return false;
        String p = normalize(path);
        return p.contains("src/test/") || p.contains("__tests__/") || p.contains(".test.") || p.contains(".spec.");
    }

    private String normalize(Node n) {
        return normalize(n.getFilePath());
    }

    private String normalize(String path) {
        return path == null ? "" : path.replace("\\", "/");
    }

    private String commentOf(Node n) {
        return n.getMetadata() != null && n.getMetadata().get("comment") instanceof String s && !s.isBlank() ? s : null;
    }
}
