// 리컨실러 T1 수직 관통 — MISSING_TRANSACTIONAL_DELETE 한 룰 전용: 조립→LLM 호출→트윈 검증까지
// (§17.10/§19.4 2막 설계, 사용자 승인으로 "런칭 완료" 게이트 예외 착수. 실제 GitHub PR 오픈 API·자동 머지는 범위 밖 —
// PR 초안 재료(diff+근거)만 반환한다. 클래스 레벨 @Transactional을 두지 않는다 — 트윈 검증이 조회한 Node의 메타데이터를
// 임시로 갱신해 재탐지하는데, 영속성 컨텍스트가 열려 있으면 그 임시 변경이 flush될 위험이 있다(이번 세션 pinGraph 사고와
// 같은 클래스의 위험). 각 조회를 별도 짧은 트랜잭션으로 끝내 반환 시점엔 detached 상태를 보장한다.
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.GraphRepository;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.domain.graph.port.AiKeyPort;
import com.codeprint.domain.graph.port.AnalysisReadPort;
import com.codeprint.domain.graph.port.ProjectAccessPort;
import com.codeprint.infrastructure.ai.AiService;
import com.codeprint.infrastructure.analysis.ParsedFile;
import com.codeprint.infrastructure.analysis.StaticCodeAnalyzer;
import com.codeprint.infrastructure.github.GitHubApiClient;
import com.codeprint.shared.ai.AiProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixAttemptService {

    // 이번 T1 수직 관통 대상은 이 룰 하나뿐(§17.10 "구현 순서 권고" — 전 룰 동시 착수 금지)
    static final String SUPPORTED_RULE_TYPE = "MISSING_TRANSACTIONAL_DELETE";

    private static final Pattern RATIONALE_DIFF = Pattern.compile(
            "RATIONALE:\\s*(.*?)\\s*DIFF:\\s*(.*)", Pattern.DOTALL);

    private final AiKeyPort aiKeyPort;
    private final GraphQueryService graphQueryService;
    private final GraphRepository graphRepository;
    private final AnalysisReadPort analysisReadPort;
    private final ProjectAccessPort projectAccessPort;
    private final GitHubApiClient gitHubApiClient;
    private final StaticCodeAnalyzer staticCodeAnalyzer;
    private final List<AiService> aiServices;

    private Map<AiProvider, AiService> servicesByProvider;

    @PostConstruct
    void init() {
        servicesByProvider = aiServices.stream().collect(Collectors.toMap(AiService::provider, s -> s));
    }

    // 그래프의 특정 FUNCTION 노드에 대한 MISSING_TRANSACTIONAL_DELETE 경고를 자동수정 시도
    public FixAttempt attemptFix(UUID projectId, UUID graphId, UUID targetNodeId, UUID userId, AiProvider provider) {
        if (provider == null) return FixAttempt.skipped("프로바이더 미지정");
        if (aiKeyPort.findPlainKey(userId, provider).isEmpty()) return FixAttempt.skipped("BYOK 키 미등록");

        Optional<ProjectAccessPort.ProjectAccessView> projectView = projectAccessPort.getProjectById(projectId);
        if (projectView.isEmpty()) return FixAttempt.failed("프로젝트 없음");
        if (projectView.get().aiExportDisabled()) return FixAttempt.skipped("프로젝트가 AI 내보내기를 차단(DLP)");

        Graph graph = graphRepository.findById(graphId).orElse(null);
        if (graph == null || !graph.getProjectId().equals(projectId)) return FixAttempt.failed("그래프 없음 또는 프로젝트 불일치");

        List<Node> nodes = graphQueryService.getNodes(graphId);
        List<Edge> edges = graphQueryService.getEdges(graphId);
        Node target = nodes.stream().filter(n -> n.getId().equals(targetNodeId)).findFirst().orElse(null);
        if (target == null || target.getType() != NodeType.FUNCTION) return FixAttempt.failed("대상 노드 없음 또는 FUNCTION 아님");

        List<Map<String, Object>> before = new GraphWarningService().detect(nodes, edges);
        Map<String, Object> warning = findWarningForNode(before, target.getId());
        if (warning == null) return FixAttempt.failed("대상 경고 없음(이미 해소됐거나 재분석 필요)");
        if (!SUPPORTED_RULE_TYPE.equals(warning.get("type"))) {
            return FixAttempt.skipped("이번 T1 범위는 " + SUPPORTED_RULE_TYPE + "뿐 — 대상 룰: " + warning.get("type"));
        }

        String sha = analysisReadPort.findCommitSha(graph.getAnalysisId()).orElse(null);
        String repoUrl = projectAccessPort.findGithubRepoUrl(projectId).orElse(null);
        if (sha == null || repoUrl == null) return FixAttempt.failed("커밋 SHA 또는 레포 URL 조회 실패");
        String content = gitHubApiClient.fetchFileContent(repoUrl, target.getFilePath(), sha);
        if (content == null) return FixAttempt.failed("파일 원문 조회 실패");

        FixPromptBundle bundle = FixPromptBundleAssembler.assemble(warning, target, content);
        String prompt = FixPromptBundleAssembler.renderPrompt(bundle);

        AiFailoverClient failover = AiFailoverClient.forUser(provider, userId, aiKeyPort, servicesByProvider);
        String response;
        try {
            response = failover.generate(prompt);
        } catch (Exception e) {
            log.warn("리컨실러 T1 LLM 호출 실패: projectId={} nodeId={} 원인={}", projectId, targetNodeId, e.getMessage());
            return FixAttempt.failed("LLM 호출 실패: " + e.getMessage());
        }

        Matcher m = RATIONALE_DIFF.matcher(response);
        if (!m.find()) return FixAttempt.failed("LLM 응답이 RATIONALE/DIFF 형식이 아님");
        String rationale = m.group(1).trim();
        String diff = m.group(2).trim();

        String patchedContent;
        try {
            patchedContent = UnifiedDiffUtil.apply(content, diff);
        } catch (Exception e) {
            return FixAttempt.failed("패치 적용 실패: " + e.getMessage());
        }

        String verifyFailure = verifyTwin(target, patchedContent, nodes, edges, before, warning);
        if (verifyFailure != null) return FixAttempt.failed("트윈 검증 실패: " + verifyFailure);

        return FixAttempt.success(diff, rationale);
    }

    // 패치 결과를 격리된 임시 파일로 재파싱→재탐지해 verify 4개 기준 중 3개 확인(컴파일 제외 — 사용자 레포 빌드스크립트를
    // 서버에서 실행하는 건 임의 코드실행 표면이라 이번 범위에서 제외, §17.10 대비 의도적 축소)
    private String verifyTwin(Node target, String patchedContent, List<Node> nodes, List<Edge> edges,
                               List<Map<String, Object>> before, Map<String, Object> targetWarning) {
        ParsedFile reparsed;
        try {
            reparsed = reparse(target, patchedContent);
        } catch (Exception e) {
            return "패치 후 구문 분석 실패(유효하지 않은 코드): " + e.getMessage();
        }
        if (reparsed.transactionalMethods() == null || !reparsed.transactionalMethods().contains(target.getName())) {
            return "패치 후에도 @Transactional 미검출";
        }

        Node patched = Node.create(target.getGraphId(), target.getType(), target.getName(),
                target.getFilePath(), target.getLanguage());
        Map<String, Object> mergedMeta = target.getMetadata() != null ? new HashMap<>(target.getMetadata()) : new HashMap<>();
        mergedMeta.put("isTransactional", true);
        patched.updateMetadata(mergedMeta);

        List<Node> patchedNodes = nodes.stream().map(n -> n.getId().equals(target.getId()) ? patched : n).toList();
        List<Map<String, Object>> after = new GraphWarningService().detect(patchedNodes, edges);

        String targetFingerprint = String.valueOf(targetWarning.get("fingerprint"));
        Set<String> beforeFp = before.stream().map(w -> String.valueOf(w.get("fingerprint"))).collect(Collectors.toSet());
        Set<String> afterFp = after.stream().map(w -> String.valueOf(w.get("fingerprint"))).collect(Collectors.toSet());

        if (afterFp.contains(targetFingerprint)) return "대상 경고가 여전히 발생";
        Set<String> newlyIntroduced = new HashSet<>(afterFp);
        newlyIntroduced.removeAll(beforeFp);
        if (!newlyIntroduced.isEmpty()) return "신규 경고 발생: " + newlyIntroduced;
        Set<String> expectedAfter = new HashSet<>(beforeFp);
        expectedAfter.remove(targetFingerprint);
        if (!expectedAfter.equals(afterFp)) return "기존 경고 집합이 대상 외에도 변경됨";
        return null;
    }

    // 패치 결과를 임시 파일에 써서 프로덕션과 동일한 StaticCodeAnalyzer 경로로 재분석(B-13 교훈 — 별도 경로는 오탐 재현 못 함)
    private ParsedFile reparse(Node target, String patchedContent) throws IOException {
        Path tempDir = Files.createTempDirectory("codeprint-fix-verify-");
        try {
            Path tempFile = tempDir.resolve(target.getFilePath());
            Files.createDirectories(tempFile.getParent());
            Files.writeString(tempFile, patchedContent, StandardCharsets.UTF_8);
            return staticCodeAnalyzer.analyze(tempFile, tempDir, target.getLanguage());
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private Map<String, Object> findWarningForNode(List<Map<String, Object>> warnings, UUID nodeId) {
        String idStr = nodeId.toString();
        return warnings.stream()
                .filter(w -> ((List<?>) w.getOrDefault("nodeIds", List.of())).stream().anyMatch(id -> idStr.equals(String.valueOf(id))))
                .findFirst().orElse(null);
    }
}
