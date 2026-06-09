// GraphWarningService 단위 테스트 — 순환 의존 및 인터페이스 체인 끊김 감지 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.EdgeType;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GraphWarningServiceTest {

    private final GraphWarningService service = new GraphWarningService();
    private final UUID graphId = UUID.randomUUID();

    private Node fileNode(String name) {
        return Node.create(graphId, NodeType.FILE, name, "/" + name + ".java", "java");
    }

    private Node funcNode(String name, boolean isInterface) {
        Node n = Node.create(graphId, NodeType.FUNCTION, name, "/Svc.java", "java");
        if (isInterface) n.updateMetadata(Map.of("isInterface", true));
        return n;
    }

    private Edge importEdge(UUID src, UUID tgt) {
        return Edge.create(graphId, src + "->" + tgt, EdgeType.IMPORT, src, tgt);
    }

    private Edge callEdge(UUID src, UUID tgt, boolean isInterfaceImpl) {
        Edge e = Edge.create(graphId, src + "->" + tgt, EdgeType.FUNCTION_CALL, src, tgt);
        if (isInterfaceImpl) e.updateMetadata(Map.of("isInterfaceImpl", true));
        return e;
    }

    @Test
    @DisplayName("순환 의존 없음 — 경고 0개")
    void noCycle() {
        Node a = fileNode("A");
        Node b = fileNode("B");
        List<Map<String, Object>> warnings = service.detect(
                List.of(a, b),
                List.of(importEdge(a.getId(), b.getId()))
        );
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("A→B→A IMPORT 순환 의존 감지")
    void cyclicImport_twoNodes() {
        Node a = fileNode("A");
        Node b = fileNode("B");
        List<Map<String, Object>> warnings = service.detect(
                List.of(a, b),
                List.of(importEdge(a.getId(), b.getId()), importEdge(b.getId(), a.getId()))
        );
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).get("type")).isEqualTo("CYCLIC_IMPORT");
    }

    @Test
    @DisplayName("A→B→C→A 3노드 순환 의존 감지")
    void cyclicImport_threeNodes() {
        Node a = fileNode("A");
        Node b = fileNode("B");
        Node c = fileNode("C");
        List<Map<String, Object>> warnings = service.detect(
                List.of(a, b, c),
                List.of(importEdge(a.getId(), b.getId()), importEdge(b.getId(), c.getId()), importEdge(c.getId(), a.getId()))
        );
        assertThat(warnings).isNotEmpty();
        assertThat(warnings.get(0).get("type")).isEqualTo("CYCLIC_IMPORT");
    }

    @Test
    @DisplayName("인터페이스 메서드에 구현체 엣지 있음 — 경고 없음")
    void interfaceChain_ok() {
        Node iface = funcNode("save", true);
        Node impl = funcNode("saveImpl", false);
        List<Map<String, Object>> warnings = service.detect(
                List.of(iface, impl),
                List.of(callEdge(UUID.randomUUID(), iface.getId(), true))
        );
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("인터페이스 메서드에 구현체 엣지 없음 — BROKEN_INTERFACE_CHAIN 경고")
    void interfaceChain_broken() {
        Node iface = funcNode("save", true);
        List<Map<String, Object>> warnings = service.detect(
                List.of(iface),
                List.of()
        );
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).get("type")).isEqualTo("BROKEN_INTERFACE_CHAIN");
    }
}
