// DddMigrationGuideService 단위 테스트 — 구조 감지 가드·엔드포인트 클러스터링·공유 자원 분리 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.EdgeType;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DddMigrationGuideServiceTest {

    private final DddMigrationGuideService service = new DddMigrationGuideService();
    private final UUID graphId = UUID.randomUUID();

    private Node fileNode(String path) {
        return Node.create(graphId, NodeType.FILE, lastSegment(path), path, "Java");
    }

    private Node funcNode(String path, String name) {
        return Node.create(graphId, NodeType.FUNCTION, name, path, "Java");
    }

    private Node endpointNode(String route) {
        return Node.create(graphId, NodeType.API_ENDPOINT, route, "/Controller.java", "Java");
    }

    private Node dbNode(String table) {
        return Node.create(graphId, NodeType.DB_TABLE, table, null, null);
    }

    private Edge call(UUID src, UUID tgt) {
        return Edge.create(graphId, src + "->" + tgt, EdgeType.FUNCTION_CALL, src, tgt);
    }

    private Edge dbRead(UUID src, UUID tgt) {
        return Edge.create(graphId, src + "->" + tgt, EdgeType.DB_READ, src, tgt);
    }

    private String lastSegment(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }

    @Test
    @DisplayName("DDD 바운디드 컨텍스트가 이미 감지되면 null을 반환한다")
    void ddd_컨텍스트_감지시_null() {
        List<Node> nodes = List.of(
                fileNode("src/application/user/UserService.java"),
                fileNode("src/domain/user/User.java"),
                fileNode("src/application/order/OrderService.java"),
                fileNode("src/domain/order/Order.java"),
                endpointNode("/api/users"),
                endpointNode("/api/orders")
        );

        assertThat(service.generateSection(nodes, List.of())).isNull();
    }

    @Test
    @DisplayName("API_ENDPOINT가 2개 미만이면 클러스터링 근거가 부족해 null을 반환한다")
    void 엔드포인트_2개미만_null() {
        Node ep = endpointNode("/api/users");
        List<Node> nodes = List.of(fileNode("src/UserController.java"), ep);

        assertThat(service.generateSection(nodes, List.of())).isNull();
    }

    @Test
    @DisplayName("서로 겹치지 않는 두 엔드포인트는 후보 모듈 2개로 분리된다")
    void 겹치지않는_엔드포인트_후보모듈_분리() {
        Node ep1 = endpointNode("/api/orders");
        Node ep2 = endpointNode("/api/products");
        Node func1 = funcNode("src/OrderService.java", "listOrders");
        Node func2 = funcNode("src/ProductService.java", "listProducts");
        Node ordersTable = dbNode("orders");
        Node productsTable = dbNode("products");

        List<Node> nodes = List.of(ep1, ep2, func1, func2, ordersTable, productsTable);
        List<Edge> edges = List.of(
                call(ep1.getId(), func1.getId()),
                dbRead(func1.getId(), ordersTable.getId()),
                call(ep2.getId(), func2.getId()),
                dbRead(func2.getId(), productsTable.getId())
        );

        String md = service.generateSection(nodes, edges);

        assertThat(md).isNotNull();
        assertThat(md).contains("후보 모듈 1", "후보 모듈 2");
        assertThat(md).contains("src/OrderService.java", "DB:orders");
        assertThat(md).contains("src/ProductService.java", "DB:products");
    }

    @Test
    @DisplayName("모든 엔드포인트가 같은 파일/테이블에만 닿으면 클러스터가 1개뿐이라 null을 반환한다")
    void 전부_겹치면_null() {
        Node ep1 = endpointNode("/api/a");
        Node ep2 = endpointNode("/api/b");
        Node sharedFunc = funcNode("src/SharedService.java", "handle");
        Node sharedFunc2 = funcNode("src/SharedHelper.java", "help");
        Node table = dbNode("shared_table");

        List<Node> nodes = List.of(ep1, ep2, sharedFunc, sharedFunc2, table);
        List<Edge> edges = List.of(
                call(ep1.getId(), sharedFunc.getId()),
                call(sharedFunc.getId(), sharedFunc2.getId()),
                dbRead(sharedFunc2.getId(), table.getId()),
                call(ep2.getId(), sharedFunc.getId())
        );

        assertThat(service.generateSection(nodes, edges)).isNull();
    }

    @Test
    @DisplayName("대부분의 엔드포인트가 공유하는 테이블은 후보 모듈에서 빼고 별도 섹션으로 표시한다")
    void 공유자원은_별도섹션() {
        Node ep1 = endpointNode("/api/a");
        Node ep2 = endpointNode("/api/b");
        Node ep3 = endpointNode("/api/c");
        Node funcA = funcNode("src/AService.java", "a");
        Node funcB = funcNode("src/BService.java", "b");
        Node funcC = funcNode("src/CService.java", "c");
        Node commonTable = dbNode("audit_log");

        List<Node> nodes = List.of(ep1, ep2, ep3, funcA, funcB, funcC, commonTable);
        List<Edge> edges = new ArrayList<>();
        for (Node[] pair : new Node[][]{{ep1, funcA}, {ep2, funcB}, {ep3, funcC}}) {
            edges.add(call(pair[0].getId(), pair[1].getId()));
            edges.add(dbRead(pair[1].getId(), commonTable.getId()));
        }

        String md = service.generateSection(nodes, edges);

        assertThat(md).isNotNull();
        assertThat(md).contains("여러 후보 모듈이 공유하는 파일/테이블");
        assertThat(md).contains("DB:audit_log");
        // 공유 테이블은 개별 후보 모듈 블록에는 나타나지 않아야 한다(공유 섹션에만 표시)
        String beforeSharedSection = md.substring(0, md.indexOf("여러 후보 모듈이 공유하는"));
        assertThat(beforeSharedSection).doesNotContain("DB:audit_log");
    }

    @Test
    @DisplayName("두 엔드포인트가 파일·테이블 2개 이상을 공유하면 같은 후보 모듈로 합쳐진다")
    void 충분히_겹치면_같은모듈로_병합() {
        Node ep1 = endpointNode("/api/orders/list");
        Node ep2 = endpointNode("/api/orders/detail");
        Node sharedFunc = funcNode("src/OrderService.java", "shared");
        Node sharedTable = dbNode("orders");
        Node ep3 = endpointNode("/api/products");
        Node otherFunc = funcNode("src/ProductService.java", "other");
        Node otherTable = dbNode("products");

        List<Node> nodes = List.of(ep1, ep2, ep3, sharedFunc, sharedTable, otherFunc, otherTable);
        List<Edge> edges = List.of(
                call(ep1.getId(), sharedFunc.getId()),
                dbRead(sharedFunc.getId(), sharedTable.getId()),
                call(ep2.getId(), sharedFunc.getId()),
                call(ep3.getId(), otherFunc.getId()),
                dbRead(otherFunc.getId(), otherTable.getId())
        );

        String md = service.generateSection(nodes, edges);

        assertThat(md).isNotNull();
        assertThat(md).contains("후보 모듈 1", "후보 모듈 2");
        assertThat(md).doesNotContain("후보 모듈 3");
    }

    @Test
    @DisplayName("엔드포인트가 상한(300)을 넘으면 O(E²) 클러스터링 대신 섹션을 생략한다")
    void 엔드포인트_상한초과_null() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 301; i++) {
            nodes.add(endpointNode("/api/e" + i));
        }

        assertThat(service.generateSection(nodes, List.of())).isNull();
    }

    @Test
    @DisplayName("엔드포인트 7개(비율 임계값이 최소치 3을 넘어서는 구간)에서도 비율 기준으로 공유 자원을 정확히 분리한다")
    void 엔드포인트_다수_비율임계값_적용() {
        // 임계값 = max(ceil(7*0.5)=4, 3) = 4 — 최소치(3)가 아니라 비율(4)이 실제로 적용되는 경계
        Node funcM = funcNode("src/CommonUtil.java", "m"); // 4개 엔드포인트가 공유 → 임계값(4) 이상, "공유 자원"으로 분리돼야 함
        Node funcL = funcNode("src/LessCommon.java", "l"); // 3개 엔드포인트가 공유 → 임계값(4) 미만, 실제 신호로 남아야 함

        List<Node> nodes = new ArrayList<>(List.of(funcM, funcL));
        List<Edge> edges = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Node ep = endpointNode("/api/m" + i);
            nodes.add(ep);
            edges.add(call(ep.getId(), funcM.getId()));
        }
        for (int i = 1; i <= 3; i++) {
            Node ep = endpointNode("/api/l" + i);
            nodes.add(ep);
            edges.add(call(ep.getId(), funcL.getId()));
        }

        String md = service.generateSection(nodes, edges);

        assertThat(md).isNotNull();
        assertThat(md).contains("여러 후보 모듈이 공유하는 파일/테이블");
        String beforeSharedSection = md.substring(0, md.indexOf("여러 후보 모듈이 공유하는"));
        // 3개 엔드포인트만 공유하는 funcL은 임계값(4) 미만이라 "공유 자원"이 아니라 후보 모듈 쪽에 남아야 한다
        assertThat(beforeSharedSection).contains("src/LessCommon.java");
        assertThat(beforeSharedSection).doesNotContain("src/CommonUtil.java");
        assertThat(md).contains("src/CommonUtil.java");
    }
}
