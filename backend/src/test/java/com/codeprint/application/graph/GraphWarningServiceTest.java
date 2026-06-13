// GraphWarningService 단위 테스트 — 순환 의존·인터페이스 체인·비동기 자기호출·DB 레이어 우회 감지 회귀 방지
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

    private Node asyncFuncNode(String name, String filePath) {
        Node n = Node.create(graphId, NodeType.FUNCTION, name, filePath, "java");
        n.updateMetadata(Map.of("isAsync", true));
        return n;
    }

    private Node funcNodeWithPath(String name, String filePath) {
        return Node.create(graphId, NodeType.FUNCTION, name, filePath, "java");
    }

    private Edge importEdgeForPath(UUID src, UUID tgt) {
        return Edge.create(graphId, src + "->imp->" + tgt, EdgeType.IMPORT, src, tgt);
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

    @Test
    @DisplayName("같은 파일 내 @Async 메서드 직접 호출 — ASYNC_SELF_CALL 경고")
    void asyncSelfCall_sameFile() {
        String file = "/com/example/MyService.java";
        Node caller = funcNodeWithPath("doWork", file);
        Node asyncTarget = asyncFuncNode("sendEmail", file);
        Edge call = callEdge(caller.getId(), asyncTarget.getId(), false);

        List<Map<String, Object>> warnings = service.detect(
                List.of(caller, asyncTarget),
                List.of(call)
        );
        // DEAD_CODE 등 다른 타입 경고가 함께 감지될 수 있으므로 타입 필터 후 검증
        List<Map<String, Object>> asyncWarnings = warnings.stream()
                .filter(w -> "ASYNC_SELF_CALL".equals(w.get("type"))).toList();
        assertThat(asyncWarnings).hasSize(1);
    }

    @Test
    @DisplayName("다른 파일에서 @Async 메서드 호출 — 경고 없음")
    void asyncSelfCall_differentFile_noWarning() {
        Node caller = funcNodeWithPath("doWork", "/com/example/CallerService.java");
        Node asyncTarget = asyncFuncNode("sendEmail", "/com/example/EmailService.java");
        Edge call = callEdge(caller.getId(), asyncTarget.getId(), false);

        List<Map<String, Object>> warnings = service.detect(
                List.of(caller, asyncTarget),
                List.of(call)
        );
        assertThat(warnings.stream().filter(w -> "ASYNC_SELF_CALL".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("application 레이어가 persistence를 직접 IMPORT — DB_LAYER_BYPASS 경고")
    void dbLayerBypass_applicationToPersistence() {
        Node appNode = funcNodeWithPath("createProject", "/com/example/application/project/ProjectService.java");
        Node repoNode = funcNodeWithPath("save", "/com/example/infrastructure/persistence/JpaProjectRepo.java");
        Edge imp = importEdgeForPath(appNode.getId(), repoNode.getId());

        List<Map<String, Object>> warnings = service.detect(
                List.of(appNode, repoNode),
                List.of(imp)
        );
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).get("type")).isEqualTo("DB_LAYER_BYPASS");
    }

    @Test
    @DisplayName("FUNCTION_CALL만 있으면 Tree-sitter 오추적 — DB_LAYER_BYPASS 경고 없음")
    void dbLayerBypass_functionCallOnly_noWarning() {
        Node appNode = funcNodeWithPath("createProject", "/com/example/application/project/ProjectService.java");
        Node repoNode = funcNodeWithPath("save", "/com/example/infrastructure/persistence/JpaProjectRepo.java");
        Edge call = callEdge(appNode.getId(), repoNode.getId(), false);

        List<Map<String, Object>> warnings = service.detect(
                List.of(appNode, repoNode),
                List.of(call)
        );
        assertThat(warnings.stream().filter(w -> "DB_LAYER_BYPASS".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("isInterfaceImpl 엣지는 DB_LAYER_BYPASS에서 제외")
    void dbLayerBypass_interfaceImpl_noWarning() {
        Node appNode = funcNodeWithPath("createProject", "/com/example/application/project/ProjectService.java");
        Node repoNode = funcNodeWithPath("save", "/com/example/infrastructure/persistence/JpaProjectRepo.java");
        Edge call = callEdge(appNode.getId(), repoNode.getId(), true);

        List<Map<String, Object>> warnings = service.detect(
                List.of(appNode, repoNode),
                List.of(call)
        );
        assertThat(warnings.stream().filter(w -> "DB_LAYER_BYPASS".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("application/project 가 domain/user 를 직접 IMPORT — CROSS_CONTEXT_IMPORT 경고")
    void crossContextImport_detected() {
        Node appNode = funcNodeWithPath("createProject", "/com/example/application/project/ProjectService.java");
        Node domainNode = funcNodeWithPath("UserPlan", "/com/example/domain/user/UserPlan.java");
        Edge imp = importEdgeForPath(appNode.getId(), domainNode.getId());

        List<Map<String, Object>> warnings = service.detect(
                List.of(appNode, domainNode),
                List.of(imp)
        );
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).get("type")).isEqualTo("CROSS_CONTEXT_IMPORT");
    }

    @Test
    @DisplayName("같은 컨텍스트 내 application → domain IMPORT — 경고 없음")
    void crossContextImport_sameContext_noWarning() {
        Node appNode = funcNodeWithPath("createProject", "/com/example/application/project/ProjectService.java");
        Node domainNode = funcNodeWithPath("Project", "/com/example/domain/project/Project.java");
        Edge imp = importEdgeForPath(appNode.getId(), domainNode.getId());

        List<Map<String, Object>> warnings = service.detect(
                List.of(appNode, domainNode),
                List.of(imp)
        );
        assertThat(warnings.stream().filter(w -> "CROSS_CONTEXT_IMPORT".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("DB_TABLE 노드에 hasConverter=true 메타데이터 — MISSING_CONVERTER_MIGRATION 경고")
    void missingConverterMigration_detected() {
        Node tableNode = Node.create(graphId, NodeType.DB_TABLE, "users", "/com/example/domain/user/User.java", "java");
        tableNode.updateMetadata(Map.of("hasConverter", true));

        List<Map<String, Object>> warnings = service.detect(
                List.of(tableNode),
                List.of()
        );
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).get("type")).isEqualTo("MISSING_CONVERTER_MIGRATION");
    }

    @Test
    @DisplayName("DB_TABLE 노드에 hasConverter 없음 — MISSING_CONVERTER_MIGRATION 경고 없음")
    void missingConverterMigration_noConverter_noWarning() {
        Node tableNode = Node.create(graphId, NodeType.DB_TABLE, "users", "/com/example/domain/user/User.java", "java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(tableNode),
                List.of()
        );
        assertThat(warnings.stream().filter(w -> "MISSING_CONVERTER_MIGRATION".equals(w.get("type"))).toList()).isEmpty();
    }

    private List<Map<String, Object>> crossDomain(List<Map<String, Object>> warnings) {
        return warnings.stream().filter(w -> "CROSS_DOMAIN_CALL".equals(w.get("type"))).toList();
    }

    @Test
    @DisplayName("도메인 경계 넘는 FUNCTION_CALL (고유·비프레임워크 이름) — CROSS_DOMAIN_CALL 경고")
    void crossDomainCall_genuineViolation_detected() {
        Node src = funcNodeWithPath("placeOrder", "/com/example/application/order/OrderService.java");
        Node tgt = funcNodeWithPath("reserveStock", "/com/example/domain/inventory/InventoryService.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(src, tgt),
                List.of(callEdge(src.getId(), tgt.getId(), false))
        );
        assertThat(crossDomain(warnings)).hasSize(1);
    }

    @Test
    @DisplayName("getter 등 프레임워크 패턴 callee — CROSS_DOMAIN_CALL 제외 (정규식 오추적)")
    void crossDomainCall_frameworkPatternName_excluded() {
        Node src = funcNodeWithPath("placeOrder", "/com/example/application/order/OrderService.java");
        Node tgt = funcNodeWithPath("getNodes", "/com/example/domain/graph/Graph.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(src, tgt),
                List.of(callEdge(src.getId(), tgt.getId(), false))
        );
        assertThat(crossDomain(warnings)).isEmpty();
    }

    @Test
    @DisplayName("JDK 컬렉션 메서드(add) callee — CROSS_DOMAIN_CALL 제외")
    void crossDomainCall_jdkCollectionName_excluded() {
        Node src = funcNodeWithPath("placeOrder", "/com/example/application/order/OrderService.java");
        Node tgt = funcNodeWithPath("add", "/com/example/domain/team/Team.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(src, tgt),
                List.of(callEdge(src.getId(), tgt.getId(), false))
        );
        assertThat(crossDomain(warnings)).isEmpty();
    }

    @Test
    @DisplayName("동일 이름이 2개 도메인에 존재 — bare-name 모호로 CROSS_DOMAIN_CALL 제외")
    void crossDomainCall_ambiguousName_excluded() {
        Node src = funcNodeWithPath("placeOrder", "/com/example/application/order/OrderService.java");
        Node tgt = funcNodeWithPath("validate", "/com/example/domain/inventory/InventoryService.java");
        Node dup = funcNodeWithPath("validate", "/com/example/domain/payment/PaymentService.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(src, tgt, dup),
                List.of(callEdge(src.getId(), tgt.getId(), false))
        );
        assertThat(crossDomain(warnings)).isEmpty();
    }

    @Test
    @DisplayName("테스트 코드 경로(src/test)의 cross-domain 호출 — CROSS_DOMAIN_CALL 제외")
    void crossDomainCall_testPath_excluded() {
        Node src = funcNodeWithPath("setUp", "/backend/src/test/java/com/example/application/order/OrderServiceTest.java");
        Node tgt = funcNodeWithPath("reserveStock", "/backend/src/main/java/com/example/domain/inventory/InventoryService.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(src, tgt),
                List.of(callEdge(src.getId(), tgt.getId(), false))
        );
        assertThat(crossDomain(warnings)).isEmpty();
    }

    // --- fingerprint (suppress 식별자) ---

    @Test
    @DisplayName("fingerprint — 동일 type+message는 동일 값(안정적), 다르면 다른 값, 64자 16진")
    void fingerprint_stableAndDistinct() {
        String a = GraphWarningService.fingerprint("CROSS_DOMAIN_CALL", "msg");
        assertThat(a).isEqualTo(GraphWarningService.fingerprint("CROSS_DOMAIN_CALL", "msg"));
        assertThat(a).isNotEqualTo(GraphWarningService.fingerprint("DEAD_CODE", "msg"));
        assertThat(a).isNotEqualTo(GraphWarningService.fingerprint("CROSS_DOMAIN_CALL", "other"));
        assertThat(a).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("detect — 각 경고에 64자 fingerprint 필드가 부여됨")
    void detect_attachesFingerprint() {
        Node a = fileNode("A");
        Node b = fileNode("B");
        List<Map<String, Object>> warnings = service.detect(
                List.of(a, b),
                List.of(importEdge(a.getId(), b.getId()), importEdge(b.getId(), a.getId()))
        );
        assertThat(warnings).isNotEmpty();
        assertThat(warnings.get(0).get("fingerprint")).isInstanceOf(String.class);
        assertThat((String) warnings.get(0).get("fingerprint")).hasSize(64);
    }
}
