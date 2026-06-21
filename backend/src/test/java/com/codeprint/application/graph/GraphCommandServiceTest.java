// GraphCommandService 단위 테스트 — pin IDOR 소속 검증·미존재 분기·annotation 정규화 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.EdgeType;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.GraphRepository;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphCommandServiceTest {

    @Mock
    private GraphRepository repository;

    @InjectMocks
    private GraphCommandService service;

    private final UUID projectId = UUID.randomUUID();
    private final UUID otherProjectId = UUID.randomUUID();
    private final UUID analysisId = UUID.randomUUID();
    private final UUID graphId = UUID.randomUUID();
    private final UUID nodeId = UUID.randomUUID();

    @Test
    @DisplayName("그래프 생성 — projectId/analysisId로 생성해 저장")
    void createGraph_saves() {
        when(repository.save(any(Graph.class))).thenAnswer(inv -> inv.getArgument(0));

        Graph g = service.createGraph(projectId, analysisId);

        assertThat(g.getProjectId()).isEqualTo(projectId);
        assertThat(g.getAnalysisId()).isEqualTo(analysisId);
        verify(repository).save(any(Graph.class));
    }

    @Test
    @DisplayName("노드 추가 — 그래프 없으면 예외, 저장 안 함")
    void addNode_graphNotFound_throws() {
        when(repository.findById(graphId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addNode(graphId, NodeType.FILE, "A", "/A.java", "java"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveNode(any());
    }

    @Test
    @DisplayName("엣지 추가 — 그래프 없으면 예외, 저장 안 함")
    void addEdge_graphNotFound_throws() {
        when(repository.findById(graphId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addEdge(graphId, "a->b", EdgeType.FUNCTION_CALL,
                UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveEdge(any());
    }

    @Test
    @DisplayName("노드 위치 갱신 — 노드 없으면 예외")
    void updateNodePosition_nodeNotFound_throws() {
        when(repository.findNodeById(nodeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateNodePosition(nodeId, 10, 20))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveNode(any());
    }

    @Test
    @DisplayName("노드 위치 갱신 — 좌표 반영 후 저장")
    void updateNodePosition_updatesAndSaves() {
        Node node = Node.create(graphId, NodeType.FUNCTION, "foo", "/S.java", "java");
        when(repository.findNodeById(node.getId())).thenReturn(Optional.of(node));

        service.updateNodePosition(node.getId(), 12.5, 34.5);

        ArgumentCaptor<Node> captor = ArgumentCaptor.forClass(Node.class);
        verify(repository).saveNode(captor.capture());
        assertThat(captor.getValue().getPosX()).isEqualTo(12.5);
        assertThat(captor.getValue().getPosY()).isEqualTo(34.5);
    }

    @Test
    @DisplayName("노드 주석 갱신 — 공백 레이블/메모는 null로 정규화")
    void updateNodeAnnotation_blankBecomesNull() {
        Node node = Node.create(graphId, NodeType.FUNCTION, "foo", "/S.java", "java");
        when(repository.findNodeById(node.getId())).thenReturn(Optional.of(node));

        service.updateNodeAnnotation(node.getId(), "  ", "메모");

        ArgumentCaptor<Node> captor = ArgumentCaptor.forClass(Node.class);
        verify(repository).saveNode(captor.capture());
        assertThat(captor.getValue().getUserLabel()).isNull();
        assertThat(captor.getValue().getUserNote()).isEqualTo("메모");
    }

    @Test
    @DisplayName("그래프 고정 — 소속 검증 통과 시 기존 슬롯 비우고 고정 저장")
    void pinGraph_inProject_clearsSlotThenPins() {
        Graph graph = graphInProject(projectId);
        when(repository.findById(graph.getId())).thenReturn(Optional.of(graph));
        when(repository.save(any(Graph.class))).thenAnswer(inv -> inv.getArgument(0));

        service.pinGraph(projectId, graph.getId(), 3);

        verify(repository).clearPinnedSlot(projectId, 3);
        ArgumentCaptor<Graph> captor = ArgumentCaptor.forClass(Graph.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isPinned()).isTrue();
        assertThat(captor.getValue().getPinnedSlot()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("그래프 고정 — 다른 프로젝트 그래프면 IDOR 차단(슬롯 비우기·저장 안 함)")
    void pinGraph_notInProject_throwsBeforeMutation() {
        Graph graph = graphInProject(otherProjectId);
        when(repository.findById(graph.getId())).thenReturn(Optional.of(graph));

        assertThatThrownBy(() -> service.pinGraph(projectId, graph.getId(), 3))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).clearPinnedSlot(any(), anyInt());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("그래프 고정 해제 — 소속 검증 통과 시 unpin 저장")
    void unpinGraph_inProject_unpinsAndSaves() {
        Graph graph = graphInProject(projectId);
        graph.pin(2);
        when(repository.findById(graph.getId())).thenReturn(Optional.of(graph));
        when(repository.save(any(Graph.class))).thenAnswer(inv -> inv.getArgument(0));

        service.unpinGraph(projectId, graph.getId());

        ArgumentCaptor<Graph> captor = ArgumentCaptor.forClass(Graph.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isPinned()).isFalse();
    }

    @Test
    @DisplayName("그래프 고정 해제 — 다른 프로젝트면 IDOR 차단(저장 안 함)")
    void unpinGraph_notInProject_throws() {
        Graph graph = graphInProject(otherProjectId);
        when(repository.findById(graph.getId())).thenReturn(Optional.of(graph));

        assertThatThrownBy(() -> service.unpinGraph(projectId, graph.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    private Graph graphInProject(UUID owningProjectId) {
        return Graph.create(owningProjectId, analysisId);
    }
}
