// FeatureSpecService 단위 테스트 — 컨텍스트 그룹핑·1파일 제외·A/B 교차배지·최선노력 실패 처리 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.domain.graph.port.AiKeyPort;
import com.codeprint.shared.ai.AiProvider;
import com.codeprint.infrastructure.ai.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureSpecServiceTest {

    @Mock private AiKeyPort aiKeyPort;
    @Mock private GraphQueryService graphQueryService;
    @Mock private RoleSpecService roleSpecService;
    @Mock private AiService aiService;

    private FeatureSpecService service;

    private final UUID graphId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(aiService.provider()).thenReturn(AiProvider.ANTHROPIC);
        service = new FeatureSpecService(aiKeyPort, graphQueryService, roleSpecService, List.of(aiService));
        Method init = FeatureSpecService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(service);
    }

    @Test
    @DisplayName("provider가 null이면 빈 문자열")
    void nullProvider_returnsEmpty() {
        String result = service.generateSection(graphId, userId, null);

        assertThat(result).isEmpty();
        verifyNoInteractions(aiKeyPort);
    }

    @Test
    @DisplayName("BYOK 키 미등록이면 빈 문자열")
    void noKey_returnsEmpty() {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.empty());

        String result = service.generateSection(graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).isEmpty();
        verifyNoInteractions(graphQueryService);
    }

    @Test
    @DisplayName("컨텍스트 구조 감지 실패(전부 미분류)면 빈 문자열")
    void noContextDetected_returnsEmpty() {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of("sk-test"));
        // domain/application 레이어 마커가 없는 플랫 경로 — 전부 (미분류)
        Node flat = fileNode("src/Foo.java", null);
        when(graphQueryService.getNodes(graphId)).thenReturn(List.of(flat));

        String result = service.generateSection(graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).isEmpty();
        verify(aiService, never()).generate(anyString(), anyString());
    }

    @Test
    @DisplayName("파일 1개짜리 컨텍스트는 정보이득 없어 스코프에서 제외")
    void singleFileContext_excluded() {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of("sk-test"));
        Node single = fileNode("src/main/java/com/example/domain/order/Order.java", null);
        when(graphQueryService.getNodes(graphId)).thenReturn(List.of(single));

        String result = service.generateSection(graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).isEmpty();
        verify(aiService, never()).generate(anyString(), anyString());
    }

    @Test
    @DisplayName("파일 2개 이상인 컨텍스트는 종합 요약을 생성한다")
    void multiFileContext_generatesSummary() {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of("sk-test"));
        Node f1 = fileNode("src/main/java/com/example/domain/order/Order.java", "주문 엔티티");
        Node f2 = fileNode("src/main/java/com/example/domain/order/OrderService.java", null);
        when(graphQueryService.getNodes(graphId)).thenReturn(List.of(f1, f2));
        when(graphQueryService.getEdges(graphId)).thenReturn(List.of());
        when(roleSpecService.selectTargetNodeIds(any(), eq(graphId))).thenReturn(Set.of());
        when(aiService.generate(anyString(), anyString())).thenReturn("주문 생성·조회를 담당하는 컨텍스트입니다.");

        String result = service.generateSection(graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).contains("order").contains("주문 생성·조회를 담당하는 컨텍스트입니다").contains("AI 추정 기능명세");
    }

    @Test
    @DisplayName("레이어A 플래그 노드가 포함된 컨텍스트는 교차배지를 표시한다")
    void contextWithLayerAFlaggedNode_showsCrossBadge() {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of("sk-test"));
        Node f1 = fileNode("src/main/java/com/example/domain/order/Order.java", null);
        Node f2 = fileNode("src/main/java/com/example/domain/order/OrderService.java", null);
        UUID flaggedFuncId = UUID.randomUUID();
        Node flaggedFunc = functionNode(flaggedFuncId, "process", "src/main/java/com/example/domain/order/OrderService.java");
        when(graphQueryService.getNodes(graphId)).thenReturn(List.of(f1, f2, flaggedFunc));
        when(graphQueryService.getEdges(graphId)).thenReturn(List.of());
        when(roleSpecService.selectTargetNodeIds(any(), eq(graphId))).thenReturn(Set.of(flaggedFuncId));
        when(aiService.generate(anyString(), anyString())).thenReturn("요약");

        String result = service.generateSection(graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).contains("레이어A 플래그 노드 1개 포함");
    }

    @Test
    @DisplayName("LLM 호출 예외 발생 시 해당 컨텍스트만 건너뛰고 예외를 전파하지 않는다")
    void aiCallThrows_skipsContextGracefully() {
        when(aiKeyPort.findPlainKey(userId, AiProvider.ANTHROPIC)).thenReturn(Optional.of("sk-test"));
        Node f1 = fileNode("src/main/java/com/example/domain/order/Order.java", null);
        Node f2 = fileNode("src/main/java/com/example/domain/order/OrderService.java", null);
        when(graphQueryService.getNodes(graphId)).thenReturn(List.of(f1, f2));
        when(graphQueryService.getEdges(graphId)).thenReturn(List.of());
        when(roleSpecService.selectTargetNodeIds(any(), eq(graphId))).thenReturn(Set.of());
        when(aiService.generate(anyString(), anyString())).thenThrow(new RuntimeException("API 오류"));

        String result = service.generateSection(graphId, userId, AiProvider.ANTHROPIC);

        assertThat(result).isEmpty();
    }

    private Node fileNode(String path, String comment) {
        Node n = mock(Node.class);
        lenient().when(n.getId()).thenReturn(UUID.randomUUID());
        lenient().when(n.getType()).thenReturn(NodeType.FILE);
        lenient().when(n.getFilePath()).thenReturn(path);
        Map<String, Object> metadata = new java.util.HashMap<>();
        if (comment != null) metadata.put("comment", comment);
        lenient().when(n.getMetadata()).thenReturn(metadata);
        return n;
    }

    private Node functionNode(UUID id, String name, String filePath) {
        Node n = mock(Node.class);
        lenient().when(n.getId()).thenReturn(id);
        lenient().when(n.getType()).thenReturn(NodeType.FUNCTION);
        lenient().when(n.getName()).thenReturn(name);
        lenient().when(n.getFilePath()).thenReturn(filePath);
        lenient().when(n.getMetadata()).thenReturn(Map.of());
        return n;
    }
}
