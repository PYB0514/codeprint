// GraphDiffService 단위 테스트 — 두 버전 노드·엣지 diff(added/removed/unchanged) 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.EdgeType;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphDiffServiceTest {

    @Mock
    private GraphQueryService graphQueryService;

    private final UUID fromGraphId = UUID.randomUUID();
    private final UUID toGraphId = UUID.randomUUID();

    // diff 호출 — from/to 그래프의 노드·엣지를 스텁으로 주입
    private GraphDiffService.DiffResult runDiff(List<Node> fromNodes, List<Node> toNodes,
                                                List<Edge> fromEdges, List<Edge> toEdges) {
        when(graphQueryService.getNodes(fromGraphId)).thenReturn(fromNodes);
        when(graphQueryService.getNodes(toGraphId)).thenReturn(toNodes);
        when(graphQueryService.getEdges(fromGraphId)).thenReturn(fromEdges);
        when(graphQueryService.getEdges(toGraphId)).thenReturn(toEdges);
        return new GraphDiffService(graphQueryService).diff(fromGraphId, toGraphId);
    }

    private Node node(NodeType type, String name, String path) {
        return Node.create(toGraphId, type, name, path, "java");
    }

    private Edge edge(String identifier, UUID src, UUID tgt) {
        return Edge.create(toGraphId, identifier, EdgeType.FUNCTION_CALL, src, tgt);
    }

    private String statusOf(List<GraphDiffService.NodeDiff> diffs, String name) {
        return diffs.stream().filter(d -> d.node().getName().equals(name))
                .map(GraphDiffService.NodeDiff::status).findFirst().orElse("(absent)");
    }

    @Test
    @DisplayName("노드 diff — 추가·삭제·불변 분류")
    void nodeDiff_addedRemovedUnchanged() {
        Node a = node(NodeType.FILE, "A", "/A.java");   // 양쪽 → unchanged
        Node b = node(NodeType.FILE, "B", "/B.java");   // from에만 → removed
        Node c = node(NodeType.FILE, "C", "/C.java");   // to에만 → added

        var result = runDiff(List.of(a, b), List.of(a, c), List.of(), List.of());

        assertThat(statusOf(result.nodes(), "A")).isEqualTo("unchanged");
        assertThat(statusOf(result.nodes(), "B")).isEqualTo("removed");
        assertThat(statusOf(result.nodes(), "C")).isEqualTo("added");
        assertThat(result.nodes()).hasSize(3);
    }

    @Test
    @DisplayName("빈 from — 모든 노드 added")
    void emptyFrom_allAdded() {
        Node a = node(NodeType.FILE, "A", "/A.java");
        var result = runDiff(List.of(), List.of(a), List.of(), List.of());
        assertThat(statusOf(result.nodes(), "A")).isEqualTo("added");
        assertThat(result.nodes()).hasSize(1);
    }

    @Test
    @DisplayName("빈 to — 모든 노드 removed")
    void emptyTo_allRemoved() {
        Node a = node(NodeType.FILE, "A", "/A.java");
        var result = runDiff(List.of(a), List.of(), List.of(), List.of());
        assertThat(statusOf(result.nodes(), "A")).isEqualTo("removed");
        assertThat(result.nodes()).hasSize(1);
    }

    @Test
    @DisplayName("nodeKey — 이름 같아도 타입·경로 다르면 별개 노드")
    void nodeKey_distinguishesByTypeAndPath() {
        Node fileX = node(NodeType.FILE, "X", "/X.java");        // from에만
        Node funcX = node(NodeType.FUNCTION, "X", "/X.java");    // to에만 (타입 다름)

        var result = runDiff(List.of(fileX), List.of(funcX), List.of(), List.of());

        // 같은 이름이지만 FILE은 removed, FUNCTION은 added로 별개 처리
        assertThat(result.nodes()).hasSize(2);
        assertThat(result.nodes()).anyMatch(d -> d.node().getType() == NodeType.FILE && d.status().equals("removed"));
        assertThat(result.nodes()).anyMatch(d -> d.node().getType() == NodeType.FUNCTION && d.status().equals("added"));
    }

    @Test
    @DisplayName("엣지 diff — edgeIdentifier 기준 추가·삭제·불변 + 노드 이름 해석")
    void edgeDiff_addedRemovedUnchanged_withNames() {
        Node foo = node(NodeType.FUNCTION, "Foo", "/S.java");
        Node bar = node(NodeType.FUNCTION, "Bar", "/S.java");
        List<Node> nodes = List.of(foo, bar);

        Edge keep = edge("foo->bar", foo.getId(), bar.getId());   // 양쪽 → unchanged
        Edge gone = edge("bar->foo", bar.getId(), foo.getId());   // from에만 → removed
        Edge fresh = edge("foo->bar2", foo.getId(), bar.getId()); // to에만 → added

        var result = runDiff(nodes, nodes, List.of(keep, gone), List.of(keep, fresh));

        var keepDiff = result.edges().stream().filter(e -> e.edge().getEdgeIdentifier().equals("foo->bar")).findFirst().orElseThrow();
        var goneDiff = result.edges().stream().filter(e -> e.edge().getEdgeIdentifier().equals("bar->foo")).findFirst().orElseThrow();
        var freshDiff = result.edges().stream().filter(e -> e.edge().getEdgeIdentifier().equals("foo->bar2")).findFirst().orElseThrow();

        assertThat(keepDiff.status()).isEqualTo("unchanged");
        assertThat(freshDiff.status()).isEqualTo("added");
        assertThat(goneDiff.status()).isEqualTo("removed");
        // 이름 해석 — added/unchanged는 to그래프, removed는 from그래프에서
        assertThat(freshDiff.sourceName()).isEqualTo("Foo");
        assertThat(freshDiff.targetName()).isEqualTo("Bar");
        assertThat(goneDiff.sourceName()).isEqualTo("Bar");
        assertThat(goneDiff.targetName()).isEqualTo("Foo");
    }

    @Test
    @DisplayName("동일 그래프 비교 — 모두 unchanged, added/removed 없음")
    void identicalGraphs_allUnchanged() {
        Node a = node(NodeType.FILE, "A", "/A.java");
        Edge e = edge("a->a", a.getId(), a.getId());
        var result = runDiff(List.of(a), List.of(a), List.of(e), List.of(e));

        assertThat(result.nodes()).singleElement().extracting(GraphDiffService.NodeDiff::status).isEqualTo("unchanged");
        assertThat(result.edges()).singleElement().extracting(GraphDiffService.EdgeDiff::status).isEqualTo("unchanged");
    }
}
