// AiGraphAnalysisService 단위 테스트 — 키 미등록 예외·AI 응답 JSON 파싱 분기(매핑·기본값·malformed) 회귀 방지
package com.codeprint.application.ai;

import com.codeprint.domain.ai.AiProvider;
import com.codeprint.domain.ai.UserAiKey;
import com.codeprint.domain.ai.UserAiKeyRepository;
import com.codeprint.infrastructure.ai.ClaudeAiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGraphAnalysisServiceTest {

    @Mock
    private UserAiKeyRepository aiKeyRepository;
    @Mock
    private ClaudeAiService claudeAiService;

    private AiGraphAnalysisService service() {
        return new AiGraphAnalysisService(aiKeyRepository, claudeAiService);
    }

    private final UUID userId = UUID.randomUUID();

    private void stubKey() {
        when(aiKeyRepository.findByUserIdAndProvider(userId, AiProvider.CLAUDE))
                .thenReturn(Optional.of(UserAiKey.of(userId, AiProvider.CLAUDE, "sk-test")));
    }

    private GraphNodeDto fnNode(UUID id, String name) {
        return new GraphNodeDto(id, "FUNCTION", name, Map.of());
    }

    @Test
    @DisplayName("Claude 키가 없으면 IllegalStateException을 던진다")
    void 키_미등록_예외() {
        when(aiKeyRepository.findByUserIdAndProvider(userId, AiProvider.CLAUDE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().analyze(userId, List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("키");
    }

    @Test
    @DisplayName("유효한 JSON 응답의 nodeName을 nodeId로 매핑하고 이슈를 파싱한다")
    void 유효_JSON_파싱_매핑() {
        stubKey();
        UUID payId = UUID.randomUUID();
        List<GraphNodeDto> nodes = List.of(fnNode(payId, "confirmPayment"));
        when(claudeAiService.explain(anyString(), anyString())).thenReturn("""
                여기 결과입니다:
                [{"nodeName":"confirmPayment","issueType":"MISSING_LOGGING","message":"로그 누락","suggestion":"로그 추가"}]
                """);

        List<AiGraphAnalysisService.DetectedIssue> issues = service().analyze(userId, nodes, List.of());

        assertThat(issues).hasSize(1);
        AiGraphAnalysisService.DetectedIssue issue = issues.get(0);
        assertThat(issue.nodeName()).isEqualTo("confirmPayment");
        assertThat(issue.nodeId()).isEqualTo(payId.toString());
        assertThat(issue.issueType()).isEqualTo("MISSING_LOGGING");
    }

    @Test
    @DisplayName("매칭되는 노드가 없으면 nodeId는 빈 문자열이다")
    void 노드명_미매칭_nodeId_빈문자열() {
        stubKey();
        List<GraphNodeDto> nodes = List.of(fnNode(UUID.randomUUID(), "knownFn"));
        when(claudeAiService.explain(anyString(), anyString())).thenReturn(
                "[{\"nodeName\":\"ghostFn\",\"issueType\":\"MISSING_TEST\",\"message\":\"m\",\"suggestion\":\"s\"}]");

        List<AiGraphAnalysisService.DetectedIssue> issues = service().analyze(userId, nodes, List.of());

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).nodeId()).isEmpty();
        assertThat(issues.get(0).nodeName()).isEqualTo("ghostFn");
    }

    @Test
    @DisplayName("JSON 배열이 없는 응답은 빈 목록을 반환한다")
    void 배열_없는_응답_빈목록() {
        stubKey();
        when(claudeAiService.explain(anyString(), anyString())).thenReturn("문제를 찾지 못했습니다.");

        assertThat(service().analyze(userId, List.of(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("malformed JSON은 예외 없이 빈 목록을 반환한다")
    void malformed_JSON_빈목록() {
        stubKey();
        when(claudeAiService.explain(anyString(), anyString())).thenReturn("[{\"nodeName\": broken json ]");

        assertThat(service().analyze(userId, List.of(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("issueType/message/suggestion 누락 시 기본값(UNKNOWN·빈문자열)으로 채운다")
    void 필드_누락_기본값() {
        stubKey();
        when(claudeAiService.explain(anyString(), anyString())).thenReturn(
                "[{\"nodeName\":\"x\"}]");

        List<AiGraphAnalysisService.DetectedIssue> issues = service().analyze(userId, List.of(), List.of());

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).issueType()).isEqualTo("UNKNOWN");
        assertThat(issues.get(0).message()).isEmpty();
        assertThat(issues.get(0).suggestion()).isEmpty();
    }
}
