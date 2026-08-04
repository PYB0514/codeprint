// FixAttemptService 단위 테스트 — 리컨실러 T1 수직 관통(조립→LLM 호출→트윈 검증) 성공/실패 경로
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
import com.codeprint.infrastructure.analysis.StaticCodeAnalyzer;
import com.codeprint.infrastructure.github.GitHubApiClient;
import com.codeprint.shared.ai.AiProvider;
import com.codeprint.shared.gate.GatePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixAttemptServiceTest {

    private static final String FILE_PATH = "infrastructure/persistence/PostBookmarkJpaRepository.java";
    private static final String ORIGINAL_CONTENT = String.join("\n",
            "package infrastructure.persistence;",
            "",
            "public class PostBookmarkJpaRepository {",
            "    public void deleteByUserIdAndPostId(java.util.UUID userId, java.util.UUID postId) {",
            "    }",
            "}") + "\n";
    private static final String VALID_LLM_RESPONSE = "RATIONALE: 파생 삭제 쿼리에 트랜잭션 경계가 없어 추가합니다.\nDIFF:\n" + String.join("\n",
            "--- a/infrastructure/persistence/PostBookmarkJpaRepository.java",
            "+++ b/infrastructure/persistence/PostBookmarkJpaRepository.java",
            "@@ -1,6 +1,9 @@",
            " package infrastructure.persistence;",
            " ",
            "+import org.springframework.transaction.annotation.Transactional;",
            "+",
            " public class PostBookmarkJpaRepository {",
            "+    @Transactional",
            "     public void deleteByUserIdAndPostId(java.util.UUID userId, java.util.UUID postId) {",
            "     }",
            " }");

    @Mock private AiKeyPort aiKeyPort;
    @Mock private GraphQueryService graphQueryService;
    @Mock private GraphRepository graphRepository;
    @Mock private AnalysisReadPort analysisReadPort;
    @Mock private ProjectAccessPort projectAccessPort;
    @Mock private GitHubApiClient gitHubApiClient;
    @Mock private AiService aiService;

    private FixAttemptService service;

    private final UUID projectId = UUID.randomUUID();
    private final UUID analysisId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Graph graph;
    private Node targetNode;

    @BeforeEach
    void setUp() throws Exception {
        graph = Graph.create(projectId, analysisId);
        targetNode = Node.create(graph.getId(), NodeType.FUNCTION, "deleteByUserIdAndPostId", FILE_PATH, "Java");

        lenient().when(aiService.provider()).thenReturn(AiProvider.ANTHROPIC);
        service = new FixAttemptService(aiKeyPort, graphQueryService, graphRepository, analysisReadPort,
                projectAccessPort, gitHubApiClient, new StaticCodeAnalyzer(), List.of(aiService));
        Method init = FixAttemptService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(service);
    }

    private void stubHappyPathUpTo(String llmResponse) {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of("sk-test"));
        when(projectAccessPort.getOwnedProject(projectId, userId)).thenReturn(
                new ProjectAccessPort.ProjectAccessView(projectId, userId, "proj", "https://github.com/x/y",
                        GatePolicy.AUTO, false));
        when(graphRepository.findById(graph.getId())).thenReturn(Optional.of(graph));
        when(graphQueryService.getNodes(graph.getId())).thenReturn(List.of(targetNode));
        when(graphQueryService.getEdges(graph.getId())).thenReturn(List.<Edge>of());
        when(analysisReadPort.findCommitSha(analysisId)).thenReturn(Optional.of("sha123"));
        when(projectAccessPort.findGithubRepoUrl(projectId)).thenReturn(Optional.of("https://github.com/x/y"));
        when(gitHubApiClient.fetchFileContent("https://github.com/x/y", FILE_PATH, "sha123")).thenReturn(ORIGINAL_CONTENT);
        if (llmResponse != null) {
            when(aiService.generate(eq("sk-test"), anyString())).thenReturn(llmResponse);
        }
    }

    @Test
    @DisplayName("정상 diff는 트윈 검증까지 통과해 SUCCESS")
    void validFix_succeeds() {
        stubHappyPathUpTo(VALID_LLM_RESPONSE);

        FixAttempt result = service.attemptFix(projectId, graph.getId(), targetNode.getId(), userId, AiProvider.ANTHROPIC);

        assertThat(result.outcome()).isEqualTo(FixAttempt.Outcome.SUCCESS);
        assertThat(result.diff()).contains("@Transactional");
        assertThat(result.rationale()).isNotBlank();
    }

    @Test
    @DisplayName("BYOK 키 미등록이면 SKIPPED")
    void noKey_skipped() {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.empty());

        FixAttempt result = service.attemptFix(projectId, graph.getId(), targetNode.getId(), userId, AiProvider.ANTHROPIC);

        assertThat(result.outcome()).isEqualTo(FixAttempt.Outcome.SKIPPED);
        verifyNoInteractions(graphRepository);
    }

    @Test
    @DisplayName("프로젝트 DLP(AI 내보내기 차단) 시 SKIPPED — 신규 AI 진입점도 토글을 존중")
    void dlpDisabled_skipped() {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of("sk-test"));
        when(projectAccessPort.getOwnedProject(projectId, userId)).thenReturn(
                new ProjectAccessPort.ProjectAccessView(projectId, userId, "proj", "https://github.com/x/y",
                        GatePolicy.AUTO, true));

        FixAttempt result = service.attemptFix(projectId, graph.getId(), targetNode.getId(), userId, AiProvider.ANTHROPIC);

        assertThat(result.outcome()).isEqualTo(FixAttempt.Outcome.SKIPPED);
        assertThat(result.reason()).contains("DLP");
        verifyNoInteractions(graphRepository);
    }

    @Test
    @DisplayName("LLM이 실제로는 아무것도 안 고친 diff(오탐용 no-op)를 내면 트윈 검증에서 FAILED")
    void noOpDiff_failsVerification() {
        String noOpResponse = "RATIONALE: 코멘트만 추가\nDIFF:\n" + String.join("\n",
                "@@ -1,6 +1,7 @@",
                " package infrastructure.persistence;",
                " ",
                "+// 코멘트",
                " public class PostBookmarkJpaRepository {",
                "     public void deleteByUserIdAndPostId(java.util.UUID userId, java.util.UUID postId) {",
                "     }",
                " }");
        stubHappyPathUpTo(noOpResponse);

        FixAttempt result = service.attemptFix(projectId, graph.getId(), targetNode.getId(), userId, AiProvider.ANTHROPIC);

        assertThat(result.outcome()).isEqualTo(FixAttempt.Outcome.FAILED);
        assertThat(result.reason()).contains("트윈 검증 실패");
    }

    @Test
    @DisplayName("원문과 컨텍스트가 안 맞는 diff는 패치 적용 실패로 FAILED")
    void badDiff_failsToApply() {
        stubHappyPathUpTo("RATIONALE: 근거\nDIFF:\n" + String.join("\n",
                "@@ -1,3 +1,4 @@",
                " 존재하지않는줄",
                "+import x;",
                " b"));

        FixAttempt result = service.attemptFix(projectId, graph.getId(), targetNode.getId(), userId, AiProvider.ANTHROPIC);

        assertThat(result.outcome()).isEqualTo(FixAttempt.Outcome.FAILED);
        assertThat(result.reason()).contains("패치 적용 실패");
    }

    @Test
    @DisplayName("프로젝트 미소유(IDOR)면 예외가 그대로 전파됨 — 조용히 SKIPPED/FAILED로 흡수하지 않음")
    void notOwner_propagatesException() {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of("sk-test"));
        when(projectAccessPort.getOwnedProject(projectId, userId))
                .thenThrow(new IllegalArgumentException("소유자 아님"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.attemptFix(projectId, graph.getId(), targetNode.getId(), userId, AiProvider.ANTHROPIC))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(graphRepository);
    }

    @Test
    @DisplayName("동명 메서드가 다른 파일에도 있어 fingerprint가 충돌해도 대상 노드 기준으로 정확히 판정(false negative 회귀 방지)")
    void fingerprintCollisionAcrossFiles_stillSucceeds() {
        // 서로 다른 파일에 있지만 이름이 같아 경고 message(따라서 fingerprint)가 동일한 decoy 노드 — 패치 대상이 아님
        Node decoy = Node.create(graph.getId(), NodeType.FUNCTION, "deleteByUserIdAndPostId",
                "infrastructure/persistence/OtherJpaRepository.java", "Java");

        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of("sk-test"));
        when(projectAccessPort.getOwnedProject(projectId, userId)).thenReturn(
                new ProjectAccessPort.ProjectAccessView(projectId, userId, "proj", "https://github.com/x/y",
                        GatePolicy.AUTO, false));
        when(graphRepository.findById(graph.getId())).thenReturn(Optional.of(graph));
        when(graphQueryService.getNodes(graph.getId())).thenReturn(List.of(targetNode, decoy));
        when(graphQueryService.getEdges(graph.getId())).thenReturn(List.<Edge>of());
        when(analysisReadPort.findCommitSha(analysisId)).thenReturn(Optional.of("sha123"));
        when(projectAccessPort.findGithubRepoUrl(projectId)).thenReturn(Optional.of("https://github.com/x/y"));
        when(gitHubApiClient.fetchFileContent("https://github.com/x/y", FILE_PATH, "sha123")).thenReturn(ORIGINAL_CONTENT);
        when(aiService.generate(eq("sk-test"), anyString())).thenReturn(VALID_LLM_RESPONSE);

        FixAttempt result = service.attemptFix(projectId, graph.getId(), targetNode.getId(), userId, AiProvider.ANTHROPIC);

        assertThat(result.outcome()).isEqualTo(FixAttempt.Outcome.SUCCESS);
    }

    @Test
    @DisplayName("대상 경고가 없으면(이미 해소) FAILED")
    void noWarning_failed() {
        Node alreadyFixed = Node.create(graph.getId(), NodeType.FUNCTION, "deleteByUserIdAndPostId", FILE_PATH, "Java");
        alreadyFixed.updateMetadata(java.util.Map.of("isTransactional", true));

        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of("sk-test"));
        when(projectAccessPort.getOwnedProject(projectId, userId)).thenReturn(
                new ProjectAccessPort.ProjectAccessView(projectId, userId, "proj", "https://github.com/x/y",
                        GatePolicy.AUTO, false));
        when(graphRepository.findById(graph.getId())).thenReturn(Optional.of(graph));
        when(graphQueryService.getNodes(graph.getId())).thenReturn(List.of(alreadyFixed));
        when(graphQueryService.getEdges(graph.getId())).thenReturn(List.<Edge>of());

        FixAttempt result = service.attemptFix(projectId, graph.getId(), alreadyFixed.getId(), userId, AiProvider.ANTHROPIC);

        assertThat(result.outcome()).isEqualTo(FixAttempt.Outcome.FAILED);
        assertThat(result.reason()).contains("대상 경고 없음");
    }
}
