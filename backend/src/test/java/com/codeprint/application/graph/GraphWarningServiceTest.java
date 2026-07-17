// GraphWarningService 단위 테스트 — 순환 의존·인터페이스 체인·비동기 자기호출·DB 레이어 우회 감지 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.ArchitectureIntent;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.EdgeType;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.shared.gate.GatePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    private Node fileNodeWithPath(String name, String filePath) {
        return Node.create(graphId, NodeType.FILE, name, filePath, "java");
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
    @DisplayName("테스트 픽스처 경로(src/test/) 간의 순환 의존은 CYCLIC_IMPORT 대상에서 제외한다")
    void cyclicImport_testPathExcluded() {
        // 벤치 스위트(BenchCommonCasesTest c-determinism)의 의도적 순환 픽스처(a.ts↔b.ts)가 이 저장소
        // 자체 구조 게이트(codeprint/structure, PR #553)에서 HIGH로 오탐된 것을 계기로 발견 — 다른 탐지기(DB_LAYER_BYPASS
        // 등)는 이미 isTestPath로 테스트 코드를 제외하는데 CYCLIC_IMPORT만 빠져 있었다.
        Node a = fileNodeWithPath("a.ts", "backend/src/test/resources/bench/common/cyclic-with-orphan/a.ts");
        Node b = fileNodeWithPath("b.ts", "backend/src/test/resources/bench/common/cyclic-with-orphan/b.ts");
        List<Map<String, Object>> warnings = service.detect(
                List.of(a, b),
                List.of(importEdge(a.getId(), b.getId()), importEdge(b.getId(), a.getId()))
        );
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("CYCLIC_IMPORT 결정성 — 노드 입력 순서·UUID 가 달라도 동일 사이클 결과(파일 경로 기준 정렬)")
    void cyclicImport_deterministic_orderIndependent() {
        // BEFORE: adj(랜덤 UUID 키 HashMap)·이웃 HashSet 순회 순서가 실행마다 달라 DFS 시작/방문 순서가 변하고
        // 검출 사이클 수가 흔들렸다(같은 코드에 CYCLIC 1↔3). 파일 경로 정렬로 결정론 보장.
        // 공유 노드가 있는 다중 사이클(store↔rootReducer↔여러 slice) 구조를 입력 순서를 뒤집어 두 번 측정 → 동일해야 함.
        long count1 = cyclicCount(false);
        long count2 = cyclicCount(true);
        assertThat(count1).isEqualTo(count2);
    }

    // 공유 노드를 가진 다중 순환 구조를 빌드해 CYCLIC_IMPORT 개수를 반환(reversed=입력 순서 뒤집기, UUID는 매 호출 새로 생성)
    private long cyclicCount(boolean reversed) {
        Node store = fileNode("store");
        Node root = fileNode("rootReducer");
        Node s1 = fileNode("sliceA");
        Node s2 = fileNode("sliceB");
        Node s3 = fileNode("sliceC");
        // store→root→{s1,s2,s3}→store : 세 슬라이스가 store/root 를 공유하는 다중 순환
        List<Edge> edges = new ArrayList<>(List.of(
                importEdge(store.getId(), root.getId()),
                importEdge(root.getId(), s1.getId()),
                importEdge(root.getId(), s2.getId()),
                importEdge(root.getId(), s3.getId()),
                importEdge(s1.getId(), store.getId()),
                importEdge(s2.getId(), store.getId()),
                importEdge(s3.getId(), store.getId())));
        List<Node> nodes = new ArrayList<>(List.of(store, root, s1, s2, s3));
        if (reversed) {
            java.util.Collections.reverse(nodes);
            java.util.Collections.reverse(edges);
        }
        return service.detect(nodes, edges).stream()
                .filter(w -> "CYCLIC_IMPORT".equals(w.get("type"))).count();
    }

    @Test
    @DisplayName("경고에 발생 파일 경로(file)가 primary 노드 경로로 부여된다")
    void warning_carriesPrimaryFilePath() {
        // 호출되지 않는 단일 함수 → DEAD_CODE (함수 1개라 신뢰도 게이트 미적용)
        Node orphan = funcNodeWithPath("orphan", "/com/x/Svc.java");

        List<Map<String, Object>> warnings = service.detect(List.of(orphan), List.of());

        assertThat(warnings).anySatisfy(w -> {
            assertThat(w.get("type")).isEqualTo("DEAD_CODE");
            assertThat(w.get("file")).isEqualTo("/com/x/Svc.java");
        });
    }

    @Test
    @DisplayName("경고에 발생 줄 번호(line)가 metadata에 line이 있는 primary 노드에서 부여된다")
    void warning_carriesPrimaryLine() {
        Node orphan = funcNodeWithPath("orphan", "/com/x/Svc.java");
        orphan.updateMetadata(Map.of("line", 42));

        List<Map<String, Object>> warnings = service.detect(List.of(orphan), List.of());

        assertThat(warnings).anySatisfy(w -> {
            assertThat(w.get("type")).isEqualTo("DEAD_CODE");
            assertThat(w.get("line")).isEqualTo(42);
        });
    }

    @Test
    @DisplayName("primary 노드에 line metadata가 없으면 line 필드가 부여되지 않는다")
    void warning_noLineMetadata_noLineField() {
        Node orphan = funcNodeWithPath("orphan", "/com/x/Svc.java");

        List<Map<String, Object>> warnings = service.detect(List.of(orphan), List.of());

        assertThat(warnings).anySatisfy(w -> assertThat(w).doesNotContainKey("line"));
    }

    @Test
    @DisplayName("경고에 컬럼 범위(col/endCol)가 metadata에 col·endCol이 있는 primary 노드에서 부여된다")
    void warning_carriesPrimaryColumnRange() {
        Node orphan = funcNodeWithPath("orphan", "/com/x/Svc.java");
        orphan.updateMetadata(Map.of("col", 16, "endCol", 24));

        List<Map<String, Object>> warnings = service.detect(List.of(orphan), List.of());

        assertThat(warnings).anySatisfy(w -> {
            assertThat(w.get("type")).isEqualTo("DEAD_CODE");
            assertThat(w.get("col")).isEqualTo(16);
            assertThat(w.get("endCol")).isEqualTo(24);
        });
    }

    @Test
    @DisplayName("primary 노드에 col/endCol metadata가 없으면 col/endCol 필드가 부여되지 않는다")
    void warning_noColumnMetadata_noColumnField() {
        Node orphan = funcNodeWithPath("orphan", "/com/x/Svc.java");

        List<Map<String, Object>> warnings = service.detect(List.of(orphan), List.of());

        assertThat(warnings).anySatisfy(w -> assertThat(w).doesNotContainKeys("col", "endCol"));
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
        // GraphBuilder 실제 방향: isInterfaceImpl 엣지는 source=인터페이스, target=구현체
        List<Map<String, Object>> warnings = service.detect(
                List.of(iface, impl),
                List.of(callEdge(iface.getId(), impl.getId(), true))
        );
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("인터페이스 메서드에 구현체 엣지 없음 — BROKEN_INTERFACE_CHAIN 경고")
    void interfaceChain_broken() {
        Node iface = funcNode("doWork", true);
        List<Map<String, Object>> warnings = service.detect(
                List.of(iface),
                List.of()
        );
        // DEAD_CODE 등 다른 타입 경고가 함께 감지될 수 있으므로 타입 필터 후 검증
        assertThat(warnings).anySatisfy(w -> assertThat(w.get("type")).isEqualTo("BROKEN_INTERFACE_CHAIN"));
    }

    @Test
    @DisplayName("★도그푸딩 실측: Spring Data 기본 메서드명(save 등)은 구현 엣지 없어도 경고 없음(상속 제공, 소스에 재선언 안 됨)")
    void interfaceChain_springDataBaseMethod_noWarning() {
        Node iface = funcNode("save", true);
        List<Map<String, Object>> warnings = service.detect(
                List.of(iface),
                List.of()
        );
        assertThat(warnings).noneMatch(w -> "BROKEN_INTERFACE_CHAIN".equals(w.get("type")));
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
    @DisplayName("별칭: application 이 infrastructure/mybatis 를 직접 IMPORT — DB_LAYER_BYPASS 경고 (realworld CQRS read)")
    void dbLayerBypass_persistenceAlias_mybatis() {
        Node appNode = funcNodeWithPath("articleQuery", "/io/spring/application/ArticleQueryService.java");
        Node readNode = funcNodeWithPath("findById", "/io/spring/infrastructure/mybatis/readservice/ArticleReadService.java");
        Edge imp = importEdgeForPath(appNode.getId(), readNode.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(appNode, readNode), List.of(imp));
        assertThat(warnings.stream().filter(w -> "DB_LAYER_BYPASS".equals(w.get("type"))).toList()).hasSize(1);
    }

    @Test
    @DisplayName("precision: infrastructure/service(비영속화) IMPORT — DB_LAYER_BYPASS 미발화")
    void dbLayerBypass_infraNonPersistence_noWarning() {
        Node appNode = funcNodeWithPath("articleQuery", "/io/spring/application/ArticleQueryService.java");
        Node svcNode = funcNodeWithPath("send", "/io/spring/infrastructure/service/MailService.java");
        Edge imp = importEdgeForPath(appNode.getId(), svcNode.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(appNode, svcNode), List.of(imp));
        assertThat(warnings.stream().filter(w -> "DB_LAYER_BYPASS".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: 도메인 Repository 인터페이스(domain/.../repository, INFRA 밖) IMPORT — DB_LAYER_BYPASS 미발화")
    void dbLayerBypass_domainRepositoryInterface_noWarning() {
        Node appNode = funcNodeWithPath("createProject", "/com/example/application/project/ProjectService.java");
        Node portNode = funcNodeWithPath("save", "/com/example/domain/project/repository/ProjectRepository.java");
        Edge imp = importEdgeForPath(appNode.getId(), portNode.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(appNode, portNode), List.of(imp));
        assertThat(warnings.stream().filter(w -> "DB_LAYER_BYPASS".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("별칭: application/article 이 core/user(도메인 별칭)를 직접 IMPORT — CROSS_CONTEXT_IMPORT 경고")
    void crossContextImport_coreDomainAlias() {
        Node appNode = funcNodeWithPath("createArticle", "/io/spring/application/article/ArticleCommandService.java");
        Node coreNode = funcNodeWithPath("User", "/io/spring/core/user/User.java");
        Edge imp = importEdgeForPath(appNode.getId(), coreNode.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(appNode, coreNode), List.of(imp));
        assertThat(warnings.stream().filter(w -> "CROSS_CONTEXT_IMPORT".equals(w.get("type"))).toList()).hasSize(1);
    }

    @Test
    @DisplayName("context-first 레이아웃({context}/application·{context}/model) 간 IMPORT — CROSS_CONTEXT_IMPORT 경고 (전역 추론)")
    void crossContextImport_contextFirstLayout() {
        // ddd-library 류: 컨텍스트가 레이어보다 앞(book/application·book/model·book/infrastructure).
        // book·patron 각자 application·model·infrastructure 3개 레이어를 선행 → context-first 컨텍스트로 추론.
        Node bookApp = funcNodeWithPath("placeOnHold", "/lending/book/application/BookService.java");
        Node bookModel = funcNodeWithPath("Book", "/lending/book/model/Book.java");
        Node bookInfra = funcNodeWithPath("save", "/lending/book/infrastructure/BookRepo.java");
        Node patronApp = funcNodeWithPath("hold", "/lending/patron/application/PatronService.java");
        Node patronModel = funcNodeWithPath("Patron", "/lending/patron/model/Patron.java");
        Node patronInfra = funcNodeWithPath("save", "/lending/patron/infrastructure/PatronRepo.java");
        // book의 application이 patron의 model을 직접 참조 — 컨텍스트 경계 위반
        Edge imp = importEdgeForPath(bookApp.getId(), patronModel.getId());

        List<Map<String, Object>> warnings = service.detect(
                List.of(bookApp, bookModel, bookInfra, patronApp, patronModel, patronInfra), List.of(imp));
        assertThat(warnings.stream().filter(w -> "CROSS_CONTEXT_IMPORT".equals(w.get("type"))).toList()).hasSize(1);
    }

    @Test
    @DisplayName("Shared Kernel(seedwork) import은 cross-context 아님 — context-first 모듈러 모놀리스의 공유 베이스 (py-ddd FP 방지)")
    void crossContextImport_sharedKernelSeedwork_excluded() {
        // pgorecki/python-ddd 류: modules/{bidding,catalog}/{application,domain,infrastructure} + 공유 seedwork/.
        // 모든 컨텍스트가 seedwork(AggregateRoot·Entity·ValueObject 베이스)를 import하는 건 정상 — 컨텍스트로 오인하면 FP.
        Node biddingApp = funcNodeWithPath("place_bid", "/src/modules/bidding/application/command/place_bid.py");
        Node biddingDom = funcNodeWithPath("Auction", "/src/modules/bidding/domain/entities.py");
        Node biddingInfra = funcNodeWithPath("save", "/src/modules/bidding/infrastructure/repository.py");
        Node catalogApp = funcNodeWithPath("publish_listing", "/src/modules/catalog/application/command/publish.py");
        Node catalogDom = funcNodeWithPath("Listing", "/src/modules/catalog/domain/entities.py");
        Node catalogInfra = funcNodeWithPath("save", "/src/modules/catalog/infrastructure/repository.py");
        Node seedworkDom = funcNodeWithPath("AggregateRoot", "/src/seedwork/domain/aggregates.py");
        // bidding/application 이 seedwork/domain(공유 베이스)을 import — 정상, cross-context 아님
        Edge impSeedwork = importEdgeForPath(biddingApp.getId(), seedworkDom.getId());
        // 대조: bidding/application 이 catalog/domain 을 직접 import — 진짜 컨텍스트 경계 위반(발화해야 함)
        Edge impCatalog = importEdgeForPath(catalogApp.getId(), biddingDom.getId());

        List<Map<String, Object>> warnings = service.detect(
                List.of(biddingApp, biddingDom, biddingInfra, catalogApp, catalogDom, catalogInfra, seedworkDom),
                List.of(impSeedwork, impCatalog));

        List<Map<String, Object>> cc = warnings.stream()
                .filter(w -> "CROSS_CONTEXT_IMPORT".equals(w.get("type"))).toList();
        // seedwork import은 제외되고 catalog→bidding 진짜 위반만 발화 = 1건
        assertThat(cc).hasSize(1);
        assertThat((String) cc.get(0).get("message")).contains("catalog");
    }

    // TS 함수 노드(피처-슬라이스 게이트는 프론트 언어 요구)
    private Node tsNode(String name, String filePath) {
        return Node.create(graphId, NodeType.FUNCTION, name, filePath, "TypeScript");
    }

    @Test
    @DisplayName("피처-슬라이스: features/auth 가 features/comments 를 직접 import — CROSS_FEATURE_IMPORT 발화 (bulletproof #1 규칙)")
    void crossFeatureImport_detected() {
        Node auth = tsNode("login", "src/features/auth/api/login.ts");
        Node comments = tsNode("getComments", "src/features/comments/api/get-comments.ts");
        Edge imp = importEdgeForPath(auth.getId(), comments.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(auth, comments), List.of(imp));

        List<Map<String, Object>> cf = warnings.stream()
                .filter(w -> "CROSS_FEATURE_IMPORT".equals(w.get("type"))).toList();
        assertThat(cf).hasSize(1);
        assertThat((String) cf.get(0).get("message")).contains("auth").contains("comments");
    }

    @Test
    @DisplayName("같은 피처 내부 import(features/auth/components → features/auth/api) — CROSS_FEATURE 미발화")
    void crossFeatureImport_sameFeature_noWarning() {
        Node form = tsNode("LoginForm", "src/features/auth/components/login-form.tsx");
        Node api = tsNode("login", "src/features/auth/api/login.ts");
        // 게이트(피처 2개↑) 충족용 다른 피처 노드
        Node other = tsNode("getComments", "src/features/comments/api/get-comments.ts");
        Edge imp = importEdgeForPath(form.getId(), api.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(form, api, other), List.of(imp));

        assertThat(warnings.stream().filter(w -> "CROSS_FEATURE_IMPORT".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("피처가 공유(shared) 모듈을 import — features 아닌 타깃이라 CROSS_FEATURE 미발화 (단방향 정상)")
    void crossFeatureImport_toShared_noWarning() {
        Node auth = tsNode("login", "src/features/auth/api/login.ts");
        Node button = tsNode("Button", "src/components/ui/button.tsx");
        Node other = tsNode("getComments", "src/features/comments/api/get-comments.ts");
        Edge imp = importEdgeForPath(auth.getId(), button.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(auth, button, other), List.of(imp));

        assertThat(warnings.stream().filter(w -> "CROSS_FEATURE_IMPORT".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: 백엔드(Java) features/ 디렉터리는 게이트(프론트 언어) 미충족 — CROSS_FEATURE 미발화")
    void crossFeatureImport_backendFeaturesDir_noWarning() {
        // 백엔드도 features/ 를 쓸 수 있으나 엔티티/타입 공유가 정상이라 오발화하면 안 됨 — 프론트 언어 게이트로 차단.
        Node a = funcNodeWithPath("doA", "src/features/billing/BillingService.java");
        Node b = funcNodeWithPath("doB", "src/features/account/Account.java");
        Edge imp = importEdgeForPath(a.getId(), b.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(a, b), List.of(imp));

        assertThat(warnings.stream().filter(w -> "CROSS_FEATURE_IMPORT".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("피처 1개뿐이면 게이트 미개방 — CROSS_FEATURE 미발화")
    void crossFeatureImport_singleFeature_noWarning() {
        Node a = tsNode("login", "src/features/auth/api/login.ts");
        Node b = tsNode("LoginForm", "src/features/auth/components/login-form.tsx");
        Edge imp = importEdgeForPath(a.getId(), b.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(a, b), List.of(imp));

        assertThat(warnings.stream().filter(w -> "CROSS_FEATURE_IMPORT".equals(w.get("type"))).toList()).isEmpty();
    }

    // 게이트(피처 2개↑) 충족용 보조 피처 노드 2개 — 레이어 단방향 테스트에서 features 레이어 존재를 보장
    private List<Node> twoFeatureNodes() {
        return List.of(
                tsNode("login", "src/features/auth/api/login.ts"),
                tsNode("getComments", "src/features/comments/api/get-comments.ts"));
    }

    @Test
    @DisplayName("단방향: shared 가 features 를 import — FEATURE_LAYER_VIOLATION 발화 (bulletproof zone 2)")
    void featureLayerViolation_sharedImportsFeature() {
        Node button = tsNode("Button", "src/components/ui/button.tsx");
        Node auth = tsNode("login", "src/features/auth/api/login.ts");
        Edge imp = importEdgeForPath(button.getId(), auth.getId());

        List<Node> nodes = new java.util.ArrayList<>(twoFeatureNodes());
        nodes.add(button); nodes.add(auth);
        List<Map<String, Object>> warnings = service.detect(nodes, List.of(imp));

        List<Map<String, Object>> v = warnings.stream()
                .filter(w -> "FEATURE_LAYER_VIOLATION".equals(w.get("type"))).toList();
        assertThat(v).hasSize(1);
        assertThat((String) v.get(0).get("message")).contains("shared").contains("features");
    }

    @Test
    @DisplayName("단방향: features 가 app 을 import — FEATURE_LAYER_VIOLATION 발화 (bulletproof zone 3)")
    void featureLayerViolation_featureImportsApp() {
        Node auth = tsNode("login", "src/features/auth/api/login.ts");
        Node router = tsNode("AppRouter", "src/app/router.tsx");
        Edge imp = importEdgeForPath(auth.getId(), router.getId());

        List<Node> nodes = new java.util.ArrayList<>(twoFeatureNodes());
        nodes.add(auth); nodes.add(router);
        List<Map<String, Object>> warnings = service.detect(nodes, List.of(imp));

        List<Map<String, Object>> v = warnings.stream()
                .filter(w -> "FEATURE_LAYER_VIOLATION".equals(w.get("type"))).toList();
        assertThat(v).hasSize(1);
        assertThat((String) v.get(0).get("message")).contains("features").contains("app");
    }

    @Test
    @DisplayName("단방향: shared 가 app 을 import — FEATURE_LAYER_VIOLATION 발화")
    void featureLayerViolation_sharedImportsApp() {
        Node lib = tsNode("apiClient", "src/lib/api-client.ts");
        Node provider = tsNode("AppProvider", "src/app/provider.tsx");
        Edge imp = importEdgeForPath(lib.getId(), provider.getId());

        List<Node> nodes = new java.util.ArrayList<>(twoFeatureNodes());
        nodes.add(lib); nodes.add(provider);
        List<Map<String, Object>> warnings = service.detect(nodes, List.of(imp));

        assertThat(warnings.stream().filter(w -> "FEATURE_LAYER_VIOLATION".equals(w.get("type"))).toList()).hasSize(1);
    }

    @Test
    @DisplayName("정상 단방향: app 이 features 를, features 가 shared 를 import — 미발화")
    void featureLayerViolation_correctDirection_noWarning() {
        Node router = tsNode("AppRouter", "src/app/router.tsx");
        Node auth = tsNode("login", "src/features/auth/api/login.ts");
        Node button = tsNode("Button", "src/components/ui/button.tsx");
        Node comments = tsNode("getComments", "src/features/comments/api/get-comments.ts");
        // app→features, features→shared 둘 다 정상 방향
        Edge appToFeature = importEdgeForPath(router.getId(), auth.getId());
        Edge featureToShared = importEdgeForPath(auth.getId(), button.getId());

        List<Map<String, Object>> warnings = service.detect(
                List.of(router, auth, button, comments), List.of(appToFeature, featureToShared));

        assertThat(warnings.stream().filter(w -> "FEATURE_LAYER_VIOLATION".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: 백엔드(Java) shared→features 는 프론트 언어 게이트 미충족 — FEATURE_LAYER_VIOLATION 미발화")
    void featureLayerViolation_backend_noWarning() {
        Node util = funcNodeWithPath("helper", "src/utils/StringUtil.java");
        Node a = funcNodeWithPath("doA", "src/features/billing/BillingService.java");
        Node b = funcNodeWithPath("doB", "src/features/account/AccountService.java");
        Edge imp = importEdgeForPath(util.getId(), a.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(util, a, b), List.of(imp));

        assertThat(warnings.stream().filter(w -> "FEATURE_LAYER_VIOLATION".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("FSD 6계층: entities 가 features 를 import — 단방향 위반 발화 (entities는 features보다 하위)")
    void featureLayerViolation_fsdEntitiesImportsFeature() {
        Node task = tsNode("Task", "src/entities/task/model.ts");
        Node toggle = tsNode("toggleTask", "src/features/toggle-task/api.ts");
        Node filters = tsNode("filters", "src/features/tasks-filters/ui.tsx");
        Edge imp = importEdgeForPath(task.getId(), toggle.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(task, toggle, filters), List.of(imp));

        List<Map<String, Object>> v = warnings.stream()
                .filter(w -> "FEATURE_LAYER_VIOLATION".equals(w.get("type"))).toList();
        assertThat(v).hasSize(1);
        assertThat((String) v.get(0).get("message")).contains("entities").contains("features");
    }

    @Test
    @DisplayName("FSD 정상: app→features→entities→shared 하향 import — 미발화")
    void featureLayerViolation_fsdCorrectDirection_noWarning() {
        Node app = tsNode("AppRoot", "src/app/index.tsx");
        Node toggle = tsNode("toggleTask", "src/features/toggle-task/api.ts");
        Node filters = tsNode("filters", "src/features/tasks-filters/ui.tsx");
        Node task = tsNode("Task", "src/entities/task/model.ts");
        Node api = tsNode("request", "src/shared/api/request.ts");
        // app→features, features→entities, entities→shared 전부 하향(정상)
        Edge a2f = importEdgeForPath(app.getId(), toggle.getId());
        Edge f2e = importEdgeForPath(toggle.getId(), task.getId());
        Edge e2s = importEdgeForPath(task.getId(), api.getId());

        List<Map<String, Object>> warnings = service.detect(
                List.of(app, toggle, filters, task, api), List.of(a2f, f2e, e2s));

        assertThat(warnings.stream().filter(w -> "FEATURE_LAYER_VIOLATION".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: Redux/RTK(app/store 지문)에선 피처 간 import가 정상 — CROSS_FEATURE 미발화")
    void crossFeatureImport_reduxProject_noWarning() {
        // RTK는 features/A 가 features/B 의 slice 를 import 하는 게 정상(idiomatic). app/store.ts 지문으로 억제.
        Node store = tsNode("store", "src/app/store.ts");
        Node a = tsNode("fetchIssue", "src/features/issuesList/issuesSlice.ts");
        Node b = tsNode("IssueDetailsPage", "src/features/issueDetails/IssueDetailsPage.tsx");
        Edge cross = importEdgeForPath(b.getId(), a.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(store, a, b), List.of(cross));

        assertThat(warnings.stream().filter(w -> "CROSS_FEATURE_IMPORT".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: Redux/RTK(rootReducer 지문)에선 features→app/store(RootState) import가 정상 — FEATURE_LAYER 미발화")
    void featureLayerViolation_reduxProject_noWarning() {
        // RTK 모든 피처는 app/store 의 RootState·AppThunk 를 import — features→app 이 정상. rootReducer.ts 지문으로 억제.
        Node rootReducer = tsNode("rootReducer", "src/app/rootReducer.ts");
        Node a = tsNode("issuesSlice", "src/features/issuesList/issuesSlice.ts");
        Node b = tsNode("repoSlice", "src/features/repoSearch/repoDetailsSlice.ts");
        Edge featureToApp = importEdgeForPath(a.getId(), rootReducer.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(rootReducer, a, b), List.of(featureToApp));

        assertThat(warnings.stream().filter(w -> "FEATURE_LAYER_VIOLATION".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("layer-first 레포의 패키지 루트는 컨텍스트로 오인하지 않음 — context-first 미적용, 기존 추출 유지")
    void crossContextImport_layerFirstRoot_notTreatedAsContext() {
        // io/spring 루트는 application·core 둘 다 선행하지만 그런 세그먼트가 유일(후보 1개<2) → context-first 아님.
        // article·user 는 layer-first(application/{ctx}·core/{ctx})로 정상 추출되어 cross-context 1건만 발화.
        Node app = funcNodeWithPath("createArticle", "/io/spring/application/article/ArticleService.java");
        Node core = funcNodeWithPath("User", "/io/spring/core/user/User.java");
        Edge imp = importEdgeForPath(app.getId(), core.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(app, core), List.of(imp));
        List<Map<String, Object>> cc = warnings.stream()
                .filter(w -> "CROSS_CONTEXT_IMPORT".equals(w.get("type"))).toList();
        assertThat(cc).hasSize(1);
        // 메시지에 루트("spring")가 아니라 실제 컨텍스트(article·user)가 담겨야 한다
        assertThat((String) cc.get(0).get("message")).contains("article").contains("user");
    }

    @Test
    @DisplayName("ignore 패턴(type+from+to 글로브)에 매치되는 경고는 그룹 억제")
    void ignorePattern_suppressesMatchingWarning() {
        Node appNode = funcNodeWithPath("createProject", "/com/example/application/project/ProjectService.java");
        Node repoNode = funcNodeWithPath("save", "/com/example/infrastructure/persistence/JpaProjectRepo.java");
        Edge imp = importEdgeForPath(appNode.getId(), repoNode.getId());
        ArchitectureIntent intent = new ArchitectureIntent(List.of(), List.of(),
                List.of(new ArchitectureIntent.IgnoreRule("DB_LAYER_BYPASS", "**/application/**", "**/persistence/**")));

        List<Map<String, Object>> warnings = service.detect(List.of(appNode, repoNode), List.of(imp), intent);
        assertThat(warnings.stream().filter(w -> "DB_LAYER_BYPASS".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("ignore 패턴이 from-only 글로브여도 매치 — type·to는 와일드카드")
    void ignorePattern_fromGlobOnly() {
        Node appNode = funcNodeWithPath("createProject", "/com/example/application/project/ProjectService.java");
        Node repoNode = funcNodeWithPath("save", "/com/example/infrastructure/persistence/JpaProjectRepo.java");
        Edge imp = importEdgeForPath(appNode.getId(), repoNode.getId());
        ArchitectureIntent intent = new ArchitectureIntent(List.of(), List.of(),
                List.of(new ArchitectureIntent.IgnoreRule(null, "**/application/**", null)));

        List<Map<String, Object>> warnings = service.detect(List.of(appNode, repoNode), List.of(imp), intent);
        assertThat(warnings.stream().filter(w -> "DB_LAYER_BYPASS".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("ignore 패턴이 다른 경로/타입이면 경고 보존 (노매치)")
    void ignorePattern_noMatch_keepsWarning() {
        Node appNode = funcNodeWithPath("createProject", "/com/example/application/project/ProjectService.java");
        Node repoNode = funcNodeWithPath("save", "/com/example/infrastructure/persistence/JpaProjectRepo.java");
        Edge imp = importEdgeForPath(appNode.getId(), repoNode.getId());
        ArchitectureIntent intent = new ArchitectureIntent(List.of(), List.of(),
                List.of(new ArchitectureIntent.IgnoreRule("DB_LAYER_BYPASS", "**/interfaces/**", null)));

        List<Map<String, Object>> warnings = service.detect(List.of(appNode, repoNode), List.of(imp), intent);
        assertThat(warnings.stream().filter(w -> "DB_LAYER_BYPASS".equals(w.get("type"))).toList()).hasSize(1);
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
    @DisplayName("헥사고날 레이어(application/domain·application/port) 간 import — CROSS_CONTEXT_IMPORT 미발화 (buckpal류 교과서 FP 방지)")
    void crossContextImport_hexagonalLayers_noWarning() {
        // buckpal: 단일 account 컨텍스트의 헥사고날 레이어. application/domain·application/port 를 컨텍스트로 오인하면
        // 레이어 간 정상 의존이 cross-context HIGH 오탐이 된다(precision 감사로 발견한 교과서 FP). 레이어 denylist+중첩 가드+C1로 방지.
        Node port = funcNodeWithPath("SendMoneyUseCase", "/buckpal/application/port/in/SendMoneyUseCase.java");
        Node model = funcNodeWithPath("Account", "/buckpal/application/domain/model/Account.java");
        Node domainService = funcNodeWithPath("SendMoneyService", "/buckpal/application/domain/service/SendMoneyService.java");
        Edge imp1 = importEdgeForPath(port.getId(), model.getId());
        Edge imp2 = importEdgeForPath(domainService.getId(), model.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(port, model, domainService), List.of(imp1, imp2));

        assertThat(warnings.stream().filter(w -> "CROSS_CONTEXT_IMPORT".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("단일 컨텍스트만 존재하면 cross-context 위반 성립 불가 — 미발화 (C1 가드)")
    void crossContextImport_singleContext_noWarning() {
        // 컨텍스트가 1개뿐이면 application/X→domain/Y 형태라도 cross-context 위반이 불가능 — C1이 발화를 막는다.
        Node app = funcNodeWithPath("createProject", "/com/example/application/project/ProjectService.java");
        Node dom = funcNodeWithPath("Project", "/com/example/domain/project/Project.java");
        Edge imp = importEdgeForPath(app.getId(), dom.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(app, dom), List.of(imp));

        assertThat(warnings.stream().filter(w -> "CROSS_CONTEXT_IMPORT".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("도메인 레이어 별칭(core/) → infrastructure import — DOMAIN_IMPORTS_INFRA 발화 (recall: domain 외 명명 인식)")
    void domainImportsInfra_coreAlias_detected() {
        // realworld 류: 도메인 레이어가 core/ 로 명명됨. 리터럴 /domain/ 만 보면 core→infra 위반을 놓침(recall 0).
        Node core = funcNodeWithPath("BadService", "/io/spring/core/BadService.java");
        Node infra = funcNodeWithPath("ArticleMapper", "/io/spring/infrastructure/mybatis/ArticleMapper.java");
        Edge imp = importEdgeForPath(core.getId(), infra.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(core, infra), List.of(imp));

        assertThat(warnings.stream().filter(w -> "DOMAIN_IMPORTS_INFRA".equals(w.get("type"))).toList()).hasSize(1);
    }

    @Test
    @DisplayName("인프라 레이어 별칭(persistence/) 도 인식 — domain → persistence DOMAIN_IMPORTS_INFRA 발화")
    void domainImportsInfra_persistenceAlias_detected() {
        Node domain = funcNodeWithPath("Order", "/app/domain/Order.java");
        Node persistence = funcNodeWithPath("OrderDao", "/app/persistence/OrderDao.java");
        Edge imp = importEdgeForPath(domain.getId(), persistence.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(domain, persistence), List.of(imp));

        assertThat(warnings.stream().filter(w -> "DOMAIN_IMPORTS_INFRA".equals(w.get("type"))).toList()).hasSize(1);
    }

    @Test
    @DisplayName("Python 별칭: core/(도메인) → db/repositories(영속화 db 별칭) IMPORT — DOMAIN_IMPORTS_INFRA 발화 (py-realworld recall)")
    void domainImportsInfra_pythonDbAlias_detected() {
        // py-realworld 류: 도메인=core/, 영속화=db/repositories/. db 가 INFRA 별칭에 없으면 core→db 위반을 놓침(recall 0).
        // core(domain)+db(infra) 2레이어로 isDddProject 게이트도 열린다.
        Node core = funcNodeWithPath("get_app_settings", "/app/core/config.py");
        Node repo = funcNodeWithPath("ArticlesRepository", "/app/db/repositories/articles.py");
        Edge imp = importEdgeForPath(core.getId(), repo.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(core, repo), List.of(imp));

        assertThat(warnings.stream().filter(w -> "DOMAIN_IMPORTS_INFRA".equals(w.get("type"))).toList()).hasSize(1);
    }

    @Test
    @DisplayName("Python 별칭: services/(application 별칭) → domain/(다른 컨텍스트) IMPORT — CROSS_CONTEXT_IMPORT 발화 (services 레이어 인식)")
    void crossContextImport_pythonServicesAlias_detected() {
        // services 가 APPLICATION 별칭이어야 application 레이어로 인식되어 컨텍스트 추출·게이트 개방이 가능하다.
        Node svc = funcNodeWithPath("create_article", "/app/services/article/ArticleService.py");
        Node dom = funcNodeWithPath("User", "/app/domain/user/User.py");
        Edge imp = importEdgeForPath(svc.getId(), dom.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(svc, dom), List.of(imp));

        assertThat(warnings.stream().filter(w -> "CROSS_CONTEXT_IMPORT".equals(w.get("type"))).toList()).hasSize(1);
    }

    @Test
    @DisplayName("precision: DB_LAYER_BYPASS 소스에 services 미포함 — services(application) → db IMPORT 시 db-bypass 미발화 (격리)")
    void dbLayerBypass_pythonServicesToDb_noWarning() {
        // services 는 APPLICATION 별칭일 뿐 UPPER_LAYER_DIRS(db-bypass 소스=인터페이스/웹 진입)에는 없다 — application→repo는 정상.
        Node svc = funcNodeWithPath("create_article", "/app/services/articles.py");
        Node repo = funcNodeWithPath("ArticlesRepository", "/app/db/repositories/articles.py");
        Edge imp = importEdgeForPath(svc.getId(), repo.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(svc, repo), List.of(imp));

        assertThat(warnings.stream().filter(w -> "DB_LAYER_BYPASS".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("Python 별칭: api/routes(인터페이스) → db/repositories(영속화) IMPORT — DB_LAYER_BYPASS 발화 (py-realworld recall)")
    void dbLayerBypass_pythonApiRoutesSource_detected() {
        // py-realworld 류: 웹 라우트가 도메인 Repository 추상을 거치지 않고 영속화 레포를 직접 import. api·routes 가 UPPER 별칭.
        Node route = funcNodeWithPath("create_article", "/app/api/routes/articles/articles_resource.py");
        Node repo = funcNodeWithPath("ArticlesRepository", "/app/db/repositories/articles.py");
        // isDddProject 게이트 개방용 도메인 레이어(core) 노드 동반
        Node core = funcNodeWithPath("Article", "/app/models/domain/articles.py");
        Edge imp = importEdgeForPath(route.getId(), repo.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(route, repo, core), List.of(imp));

        assertThat(warnings.stream().filter(w -> "DB_LAYER_BYPASS".equals(w.get("type"))).toList()).hasSize(1);
    }

    @Test
    @DisplayName("precision: routes → db/errors.py(순수 예외 타입) IMPORT — DB_LAYER_BYPASS 미발화 (py-realworld FP)")
    void dbLayerBypass_errorsModule_excluded() {
        // app/db/errors.py = class EntityDoesNotExist(Exception) 뿐인 예외 타입 정의. 라우트가 except로 잡는
        // 표준 패턴이라 "직접 persistence 호출"이 아닌데도 db/ 아래라 오탐(2026-07-01 py-realworld 측정).
        Node route = funcNodeWithPath("get_article", "/app/api/routes/articles.py");
        Node errors = funcNodeWithPath("EntityDoesNotExist", "/app/db/errors.py");
        Node core = funcNodeWithPath("Article", "/app/models/domain/articles.py");
        Edge imp = importEdgeForPath(route.getId(), errors.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(route, errors, core), List.of(imp));

        assertThat(warnings.stream().filter(w -> "DB_LAYER_BYPASS".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: 컴포지션 루트(*LifeCycle)가 영속화 구현체를 직접 import — DB_LAYER_BYPASS 미발화 (IDDD_Samples 실측)")
    void dbLayerBypass_compositionRoot_excluded() {
        // ApplicationServiceLifeCycle.java 가 LevelDBEventStore.java 를 직접 배선 — 애플리케이션 부트스트랩은
        // 구체 구현체를 알아야 배선 가능하므로 레이어링 규칙의 의도적 예외(2026-07-01 IDDD_Samples 측정).
        Node lifecycle = funcNodeWithPath("startup", "/com/saasovation/agilepm/application/ApplicationServiceLifeCycle.java");
        Node store = funcNodeWithPath("LevelDBEventStore", "/com/saasovation/agilepm/infrastructure/persistence/LevelDBEventStore.java");
        Edge imp = importEdgeForPath(lifecycle.getId(), store.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(lifecycle, store), List.of(imp));

        assertThat(warnings.stream().filter(w -> "DB_LAYER_BYPASS".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: 테스트 코드(application 패키지 *Test)가 영속화를 직접 import — DB_LAYER_BYPASS 미발화 (통합 테스트 와이어링)")
    void dbLayerBypass_testSource_excluded() {
        // 통합 테스트는 레포지토리를 직접 주입/생성하는 게 정상 — 프로덕션 위반 아님. java-realworld *QueryServiceTest 류 FP 제거.
        Node test = funcNodeWithPath("shouldFetchArticle", "/src/test/java/io/spring/application/ArticleQueryServiceTest.java");
        Node repo = funcNodeWithPath("MyBatisArticleRepository", "/src/main/java/io/spring/infrastructure/repository/MyBatisArticleRepository.java");
        Edge imp = importEdgeForPath(test.getId(), repo.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(test, repo), List.of(imp));

        assertThat(warnings.stream().filter(w -> "DB_LAYER_BYPASS".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: 테스트 코드(*ApiTest)가 타 컨텍스트 도메인을 직접 import — CROSS_CONTEXT_IMPORT 미발화 (테스트 픽스처)")
    void crossContextImport_testSource_excluded() {
        // 통합 테스트가 여러 컨텍스트의 도메인을 import하는 건 정상 — java-realworld *QueryServiceTest 류 cross-context FP 제거.
        // 소스를 application/ 테스트 경로로 두어 제외가 없으면 srcContext=article·tgtContext=user 로 발화할 상황을 만든다.
        Node test = funcNodeWithPath("shouldCreate", "/src/test/java/com/example/application/article/ArticleServiceTest.java");
        Node dom = funcNodeWithPath("UserPlan", "/src/main/java/com/example/domain/user/UserPlan.java");
        // distinct 컨텍스트 2개 성립을 위해 프로덕션 노드도 함께 둔다(C1 게이트 통과용)
        Node app = funcNodeWithPath("createProject", "/src/main/java/com/example/application/project/ProjectService.java");
        Edge imp = importEdgeForPath(test.getId(), dom.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(test, dom, app), List.of(imp));

        assertThat(warnings.stream().filter(w -> "CROSS_CONTEXT_IMPORT".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: 테스트 코드(domain 패키지 *Test)가 인프라를 직접 import — DOMAIN_IMPORTS_INFRA 미발화")
    void domainImportsInfra_testSource_excluded() {
        Node test = funcNodeWithPath("encrypts", "/src/test/java/io/spring/core/UserServiceTest.java");
        Node infra = funcNodeWithPath("ArticleMapper", "/src/main/java/io/spring/infrastructure/mybatis/ArticleMapper.java");
        Edge imp = importEdgeForPath(test.getId(), infra.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(test, infra), List.of(imp));

        assertThat(warnings.stream().filter(w -> "DOMAIN_IMPORTS_INFRA".equals(w.get("type"))).toList()).isEmpty();
    }

    // (레거시) INTERFACES_IMPORTS_INFRA가 DDD 전용이던 시절 isDddProject 게이트를 열던 더미 노드.
    // 2026-07-17 공통 게이트 승격 이후엔 정책 무관 항상 실행돼 더 이상 필요 없지만, 기존 호출부는
    // 있어도 무해해 유지한다(decisions/DECISIONS_ANALYSIS.md 참조).
    private Node dddGateNode() {
        return funcNodeWithPath("Dummy", "/com/example/domain/user/User.java");
    }

    @Test
    @DisplayName("interfaces/(Controller) → infrastructure/ IMPORT — INTERFACES_IMPORTS_INFRA 발화 (G-3)")
    void interfaceImportsInfra_detected() {
        Node controller = funcNodeWithPath("AuthController", "/com/example/interfaces/api/AuthController.java");
        Node infra = funcNodeWithPath("JwtTokenProvider", "/com/example/infrastructure/security/JwtTokenProvider.java");
        Edge imp = importEdgeForPath(controller.getId(), infra.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(controller, infra, dddGateNode()), List.of(imp));

        List<Map<String, Object>> found = warnings.stream().filter(w -> "INTERFACES_IMPORTS_INFRA".equals(w.get("type"))).toList();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).get("severity")).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("presentation/(interfaces 별칭) → infra IMPORT — INTERFACES_IMPORTS_INFRA 발화 (recall)")
    void interfaceImportsInfra_presentationAlias_detected() {
        Node controller = funcNodeWithPath("UserController", "/com/example/presentation/UserController.java");
        Node infra = funcNodeWithPath("S3Service", "/com/example/infrastructure/storage/S3Service.java");
        Edge imp = importEdgeForPath(controller.getId(), infra.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(controller, infra, dddGateNode()), List.of(imp));

        assertThat(warnings.stream().filter(w -> "INTERFACES_IMPORTS_INFRA".equals(w.get("type"))).toList()).hasSize(1);
    }

    @Test
    @DisplayName("precision: application/ → infrastructure/ IMPORT은 정상 방향 — INTERFACES_IMPORTS_INFRA 미발화")
    void interfaceImportsInfra_applicationLayer_notFlagged() {
        // Application이 infra를 직접 쓰는 건 이 프로젝트 컨벤션상 정상(Facade/Service 패턴) — interfaces만 대상.
        Node appService = funcNodeWithPath("AuthTokenService", "/com/example/application/user/AuthTokenService.java");
        Node infra = funcNodeWithPath("JwtTokenProvider", "/com/example/infrastructure/security/JwtTokenProvider.java");
        Edge imp = importEdgeForPath(appService.getId(), infra.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(appService, infra, dddGateNode()), List.of(imp));

        assertThat(warnings.stream().filter(w -> "INTERFACES_IMPORTS_INFRA".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: interfaces/ → infrastructure/shared/ IMPORT — Shared Kernel 예외로 미발화")
    void interfaceImportsInfra_sharedException_notFlagged() {
        Node controller = funcNodeWithPath("AuthController", "/com/example/interfaces/api/AuthController.java");
        Node sharedInfra = funcNodeWithPath("AesEncryptionConverter", "/com/example/infrastructure/shared/AesEncryptionConverter.java");
        Edge imp = importEdgeForPath(controller.getId(), sharedInfra.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(controller, sharedInfra, dddGateNode()), List.of(imp));

        assertThat(warnings.stream().filter(w -> "INTERFACES_IMPORTS_INFRA".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: 컴포지션 루트(*Configuration)가 interfaces/에서 infra 배선 — 예외로 미발화")
    void interfaceImportsInfra_compositionRoot_excluded() {
        Node config = funcNodeWithPath("SecurityConfiguration", "/com/example/interfaces/api/SecurityConfiguration.java");
        Node infra = funcNodeWithPath("JwtAuthenticationFilter", "/com/example/infrastructure/security/JwtAuthenticationFilter.java");
        Edge imp = importEdgeForPath(config.getId(), infra.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(config, infra, dddGateNode()), List.of(imp));

        assertThat(warnings.stream().filter(w -> "INTERFACES_IMPORTS_INFRA".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: 테스트 코드(interfaces 패키지 *Test)가 인프라를 직접 import — INTERFACES_IMPORTS_INFRA 미발화")
    void interfaceImportsInfra_testSource_excluded() {
        Node test = funcNodeWithPath("login", "/src/test/java/com/example/interfaces/api/AuthControllerTest.java");
        Node infra = funcNodeWithPath("JwtTokenProvider", "/src/main/java/com/example/infrastructure/security/JwtTokenProvider.java");
        Edge imp = importEdgeForPath(test.getId(), infra.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(test, infra, dddGateNode()), List.of(imp));

        assertThat(warnings.stream().filter(w -> "INTERFACES_IMPORTS_INFRA".equals(w.get("type"))).toList()).isEmpty();
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

    @Test
    @DisplayName("infra/persistence의 deleteBy* 메서드에 @Transactional 없음 — MISSING_TRANSACTIONAL_DELETE 경고")
    void missingTransactionalDelete_derivedDeleteMethod_noAnnotation_detected() {
        Node deleteMethod = funcNodeWithPath("deleteByUserIdAndPostId",
                "/infrastructure/persistence/community/PostBookmarkJpaRepository.java");

        List<Map<String, Object>> warnings = service.detect(List.of(deleteMethod), List.of());

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).get("type")).isEqualTo("MISSING_TRANSACTIONAL_DELETE");
    }

    @Test
    @DisplayName("removeBy* 메서드도 동일 규칙 적용")
    void missingTransactionalDelete_removeByVariant_detected() {
        Node removeMethod = funcNodeWithPath("removeByFollowerIdAndFollowingId",
                "/infrastructure/persistence/user/UserFollowRepositoryImpl.java");

        List<Map<String, Object>> warnings = service.detect(List.of(removeMethod), List.of());

        assertThat(warnings.stream().filter(w -> "MISSING_TRANSACTIONAL_DELETE".equals(w.get("type")))).hasSize(1);
    }

    @Test
    @DisplayName("@Transactional 메타데이터 있음 — MISSING_TRANSACTIONAL_DELETE 경고 없음")
    void missingTransactionalDelete_hasAnnotation_noWarning() {
        Node deleteMethod = funcNodeWithPath("deleteByUserIdAndPostId",
                "/infrastructure/persistence/community/PostBookmarkJpaRepository.java");
        deleteMethod.updateMetadata(Map.of("isTransactional", true));

        List<Map<String, Object>> warnings = service.detect(List.of(deleteMethod), List.of());

        assertThat(warnings.stream().filter(w -> "MISSING_TRANSACTIONAL_DELETE".equals(w.get("type")))).isEmpty();
    }

    @Test
    @DisplayName("infra/persistence 레이어 밖의 deleteBy* — MISSING_TRANSACTIONAL_DELETE 경고 없음(오탐 방지 경로 가드)")
    void missingTransactionalDelete_outsidePersistenceLayer_noWarning() {
        Node deleteMethod = funcNodeWithPath("deleteByUserIdAndPostId",
                "/application/community/PostBookmarkService.java");

        List<Map<String, Object>> warnings = service.detect(List.of(deleteMethod), List.of());

        assertThat(warnings.stream().filter(w -> "MISSING_TRANSACTIONAL_DELETE".equals(w.get("type")))).isEmpty();
    }

    @Test
    @DisplayName("CrudRepository 기본 메서드 deleteById — MISSING_TRANSACTIONAL_DELETE 경고 없음(프레임워크가 이미 트랜잭션 처리)")
    void missingTransactionalDelete_baseDeleteById_noWarning() {
        Node deleteMethod = funcNodeWithPath("deleteById",
                "/infrastructure/persistence/project/ProjectRepositoryImpl.java");

        List<Map<String, Object>> warnings = service.detect(List.of(deleteMethod), List.of());

        assertThat(warnings.stream().filter(w -> "MISSING_TRANSACTIONAL_DELETE".equals(w.get("type")))).isEmpty();
    }

    @Test
    @DisplayName("@Transactional Impl이 내부 JpaRepository 메서드를 호출 — 내부 메서드는 경고 없음(호출자가 트랜잭션 경계 제공)")
    void missingTransactionalDelete_wrappedByTransactionalCaller_noWarning() {
        Node implMethod = funcNodeWithPath("deleteByFollowerIdAndFollowingId",
                "/infrastructure/persistence/user/UserFollowRepositoryImpl.java");
        implMethod.updateMetadata(Map.of("isTransactional", true));
        Node jpaInterfaceMethod = funcNodeWithPath("deleteByFollowerIdAndFollowingId",
                "/infrastructure/persistence/user/UserFollowJpaRepository.java");
        Edge call = callEdge(implMethod.getId(), jpaInterfaceMethod.getId(), false);

        List<Map<String, Object>> warnings = service.detect(List.of(implMethod, jpaInterfaceMethod), List.of(call));

        assertThat(warnings.stream().filter(w -> "MISSING_TRANSACTIONAL_DELETE".equals(w.get("type")))).isEmpty();
    }

    @Test
    @DisplayName("호출자가 트랜잭션 경계 없이 호출 — 경고 유지(정상 발화 회귀 방지)")
    void missingTransactionalDelete_callerNotTransactional_stillDetected() {
        Node caller = funcNodeWithPath("removeFollow",
                "/application/user/UserFollowService.java");
        Node jpaInterfaceMethod = funcNodeWithPath("deleteByFollowerIdAndFollowingId",
                "/infrastructure/persistence/user/UserFollowJpaRepository.java");
        Edge call = callEdge(caller.getId(), jpaInterfaceMethod.getId(), false);

        List<Map<String, Object>> warnings = service.detect(List.of(caller, jpaInterfaceMethod), List.of(call));

        assertThat(warnings.stream().filter(w -> "MISSING_TRANSACTIONAL_DELETE".equals(w.get("type")))).hasSize(1);
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
    @DisplayName("JDK 정규식 메서드(matches) callee — CROSS_DOMAIN_CALL 제외 (Matcher.matches phantom 차단)")
    void crossDomainCall_jdkMatchesName_excluded() {
        // ProjectCommandService.createProject 의 GITHUB_URL_PATTERN.matcher(url).matches() 가 graph 도메인의
        // 동명 함수로 오연결되던 phantom — matches/matcher 는 JDK String/Pattern 메서드라 cross-domain 제외.
        Node src = funcNodeWithPath("createProject", "/com/example/application/project/ProjectCommandService.java");
        Node tgt = funcNodeWithPath("matches", "/com/example/application/graph/GraphWarningService.java");

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

    @Test
    @DisplayName("pytest 테스트 경로(modules/x/tests/test_*.py)의 cross-domain 호출 — CROSS_DOMAIN_CALL 제외 (py-ddd FP)")
    void crossDomainCall_pytestPath_excluded() {
        // isTestPath는 Java src/test·JS test/spec만 인식해 pytest tests/ 관례를 놓쳤음(py-ddd 18건 오탐, 2026-07-01 측정).
        Node src = funcNodeWithPath("test_place_one_bid", "/src/modules/bidding/tests/domain/test_bidding.py");
        Node tgt = funcNodeWithPath("place_bid", "/src/modules/bidding/domain/entities.py");

        List<Map<String, Object>> warnings = service.detect(
                List.of(src, tgt),
                List.of(callEdge(src.getId(), tgt.getId(), false))
        );
        assertThat(crossDomain(warnings)).isEmpty();
    }

    @Test
    @DisplayName("헥사고날 단일 컨텍스트(application/domain/{service,model}) 내부 호출 — CROSS_DOMAIN_CALL 제외 (buckpal FP)")
    void crossDomainCall_hexagonalSubLayers_excluded() {
        // buckpal: application/domain/service/SendMoneyService → application/domain/model/Account.
        // service·model 은 단일 컨텍스트의 하위레이어이지 별개 도메인이 아니다(레이어 용어·application 하위 중첩 가드로 컨텍스트=null).
        Node src = funcNodeWithPath("sendMoney", "/io/reflectoring/buckpal/application/domain/service/SendMoneyService.java");
        Node tgt = funcNodeWithPath("withdraw", "/io/reflectoring/buckpal/application/domain/model/Account.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(src, tgt),
                List.of(callEdge(src.getId(), tgt.getId(), false))
        );
        assertThat(crossDomain(warnings)).isEmpty();
    }

    // --- DEAD_CODE 오탐 제외 ---

    // 특정 노드가 DEAD_CODE로 플래그됐는지 확인
    private boolean isDeadCode(List<Map<String, Object>> warnings, UUID nodeId) {
        return warnings.stream()
                .filter(w -> "DEAD_CODE".equals(w.get("type")))
                .anyMatch(w -> ((List<?>) w.get("nodeIds")).contains(nodeId.toString()));
    }

    @Test
    @DisplayName("JPA AttributeConverter 메서드(convertToDatabaseColumn) — Hibernate 리플렉션 호출이라 DEAD_CODE 제외")
    void deadCode_jpaConverter_excluded() {
        Node conv = funcNodeWithPath("convertToDatabaseColumn", "/com/example/shared/jpa/AesEncryptionConverter.java");
        List<Map<String, Object>> warnings = service.detect(List.of(conv), List.of());
        assertThat(isDeadCode(warnings, conv.getId())).isFalse();
    }

    @Test
    @DisplayName("도메인 Repository 인터페이스 선언 메서드 — 같은 이름 호출이 있으면 디스패치로 보고 DEAD_CODE 제외")
    void deadCode_domainInterfaceDispatch_excluded() {
        Node caller = funcNodeWithPath("removeUser", "/com/example/application/user/UserCommandService.java");
        Node impl = funcNodeWithPath("delete", "/com/example/infrastructure/persistence/user/UserRepositoryImpl.java");
        Node iface = funcNodeWithPath("delete", "/com/example/domain/user/UserRepository.java");
        // 호출은 구현체로 향함 — 인터페이스 선언 노드엔 인바운드 엣지 없음
        Edge call = callEdge(caller.getId(), impl.getId(), false);

        List<Map<String, Object>> warnings = service.detect(List.of(caller, impl, iface), List.of(call));
        assertThat(isDeadCode(warnings, iface.getId())).isFalse();
    }

    @Test
    @DisplayName("도메인 port 인터페이스 메서드(confirmPayment) — 같은 이름 호출 있으면 DEAD_CODE 제외")
    void deadCode_domainPortDispatch_excluded() {
        Node caller = funcNodeWithPath("confirmDonation", "/com/example/application/donation/DonationApplicationService.java");
        Node impl = funcNodeWithPath("confirmPayment", "/com/example/infrastructure/payment/TossPaymentsService.java");
        Node port = funcNodeWithPath("confirmPayment", "/com/example/domain/donation/port/PaymentGatewayPort.java");
        Edge call = callEdge(caller.getId(), impl.getId(), false);

        List<Map<String, Object>> warnings = service.detect(List.of(caller, impl, port), List.of(call));
        assertThat(isDeadCode(warnings, port.getId())).isFalse();
    }

    @Test
    @DisplayName("동명 함수 다중 정의 + 호출 존재 — 리시버/인터페이스 다형성 디스패치로 보고 미연결 구현체도 DEAD_CODE 제외")
    void deadCode_polymorphicDispatch_excluded() {
        // 회귀: Go func (T) Bind() 다중 구현 중 정적 분석이 한 구현체로만 엣지 연결 → 나머지가 거짓 DEAD_CODE
        Node caller = funcNodeWithPath("handle", "/gin/context.go");
        Node bindJson = funcNodeWithPath("Bind", "/gin/binding/json.go");
        Node bindXml = funcNodeWithPath("Bind", "/gin/binding/xml.go");
        // 호출은 json 구현체로만 연결됨 — xml 구현체엔 인바운드 엣지 없음
        Edge call = callEdge(caller.getId(), bindJson.getId(), false);

        List<Map<String, Object>> warnings = service.detect(List.of(caller, bindJson, bindXml), List.of(call));
        assertThat(isDeadCode(warnings, bindJson.getId())).isFalse();
        assertThat(isDeadCode(warnings, bindXml.getId())).isFalse();
    }

    @Test
    @DisplayName("루트 레벨 components/ 함수(앞 슬래시 없는 경로) — 세그먼트 매칭으로 React 제외 적용, DEAD_CODE 아님")
    void deadCode_rootLevelComponentsDir_excluded() {
        // 회귀: 분석 루트가 frontend/src 면 경로가 "components/Graph.tsx"(앞 슬래시 없음)라 fp.contains("/components/")가
        // 빗나가 React 모듈이 거짓 DEAD_CODE로 잡혔다(데스크탑/서브디렉터리 분석). 앞 슬래시 정규화로 세그먼트 매칭.
        Node comp = funcNodeWithPath("computeLayout", "components/Graph.tsx");
        List<Map<String, Object>> warnings = service.detect(List.of(comp), List.of());
        assertThat(isDeadCode(warnings, comp.getId())).isFalse();
    }

    @Test
    @DisplayName("호출되지 않는 일반 함수(shared/) — DEAD_CODE 여전히 감지 (과잉 억제 방지)")
    void deadCode_genuinelyUnused_stillDetected() {
        Node unused = funcNodeWithPath("monthlyPrice", "/com/example/shared/plan/UserPlan.java");
        List<Map<String, Object>> warnings = service.detect(List.of(unused), List.of());
        assertThat(isDeadCode(warnings, unused.getId())).isTrue();
    }

    @Test
    @DisplayName("아무 데서도 호출 안 되는 인터페이스 메서드 — DEAD_CODE 여전히 감지")
    void deadCode_uncalledInterfaceMethod_stillDetected() {
        Node iface = funcNodeWithPath("neverCalledQuery", "/com/example/domain/user/UserRepository.java");
        List<Map<String, Object>> warnings = service.detect(List.of(iface), List.of());
        assertThat(isDeadCode(warnings, iface.getId())).isTrue();
    }

    @Test
    @DisplayName("Python 던더 메서드(__init__) — 런타임 호출이라 DEAD_CODE 제외 (C-13)")
    void deadCode_pythonDunder_excluded() {
        Node dunder = Node.create(graphId, NodeType.FUNCTION, "__init__", "/requests/models.py", "Python");
        List<Map<String, Object>> warnings = service.detect(List.of(dunder), List.of());
        assertThat(isDeadCode(warnings, dunder.getId())).isFalse();
    }

    @Test
    @DisplayName("던더 아님(__private, 트레일링 __ 없음) — DEAD_CODE 여전히 감지 (과잉 억제 방지)")
    void deadCode_notDunder_stillDetected() {
        Node n = Node.create(graphId, NodeType.FUNCTION, "__private", "/shared/util.py", "Python");
        List<Map<String, Object>> warnings = service.detect(List.of(n), List.of());
        assertThat(isDeadCode(warnings, n.getId())).isTrue();
    }

    @Test
    @DisplayName("프레임워크 어노테이션 메서드(isFrameworkAnnotated 메타) — DEAD_CODE 제외 (C-13)")
    void deadCode_frameworkAnnotated_excluded() {
        Node handler = funcNodeWithPath("welcome", "/petclinic/system/WelcomeController.java");
        handler.updateMetadata(Map.of("isFrameworkAnnotated", true));
        List<Map<String, Object>> warnings = service.detect(List.of(handler), List.of());
        assertThat(isDeadCode(warnings, handler.getId())).isFalse();
    }

    @Test
    @DisplayName("값(콜백)으로 참조되는 함수(referencedAsValue 메타) — 호출 엣지 없어도 DEAD_CODE 제외 (B-16)")
    void deadCode_referencedAsValue_excluded() {
        Node handler = funcNodeWithPath("defaultHandleRecovery", "/gin/recovery.go");
        handler.updateMetadata(Map.of("referencedAsValue", true));
        List<Map<String, Object>> warnings = service.detect(List.of(handler), List.of());
        assertThat(isDeadCode(warnings, handler.getId())).isFalse();
    }

    @Test
    @DisplayName("테스트 디렉터리(/tests/)·pytest 함수(test_*) — DEAD_CODE 제외 (C-13)")
    void deadCode_testArtifacts_excluded() {
        Node testsDir = Node.create(graphId, NodeType.FUNCTION, "helper", "/requests/tests/utils.py", "Python");
        Node pytestFn = Node.create(graphId, NodeType.FUNCTION, "test_get_returns_200", "/requests/api.py", "Python");
        List<Map<String, Object>> warnings = service.detect(List.of(testsDir, pytestFn), List.of());
        assertThat(isDeadCode(warnings, testsDir.getId())).isFalse();
        assertThat(isDeadCode(warnings, pytestFn.getId())).isFalse();
    }

    @Test
    @DisplayName("테스트 파일명(*Tests.java·*.spec.ts) — /test/ 경로 밖이어도 DEAD_CODE 제외 (C-13)")
    void deadCode_testFileNames_excluded() {
        Node junit = funcNodeWithPath("george", "/petclinic/owner/OwnerControllerTests.java");
        Node jest = Node.create(graphId, NodeType.FUNCTION, "renders", "/src/app/Button.spec.ts", "TypeScript");
        List<Map<String, Object>> warnings = service.detect(List.of(junit, jest), List.of());
        assertThat(isDeadCode(warnings, junit.getId())).isFalse();
        assertThat(isDeadCode(warnings, jest.getId())).isFalse();
    }

    @Test
    @DisplayName("루트 레벨 tests/·test/ 디렉터리(상대경로, 앞 슬래시 없음) — DEAD_CODE 제외 (재캘리)")
    void deadCode_rootLevelTestDir_excluded() {
        // 분석기는 repoRoot 상대경로를 저장하므로 루트 tests/ 는 "tests/..."(앞 슬래시 없음) — "/tests/" 매칭 실패하던 버그
        Node pyFixture = Node.create(graphId, NodeType.FUNCTION, "response_handler", "tests/test_requests.py", "Python");
        Node goHelper = Node.create(graphId, NodeType.FUNCTION, "newTestServer", "test/server.go", "Go");
        List<Map<String, Object>> warnings = service.detect(List.of(pyFixture, goHelper), List.of());
        assertThat(isDeadCode(warnings, pyFixture.getId())).isFalse();
        assertThat(isDeadCode(warnings, goHelper.getId())).isFalse();
    }

    // DEAD_CODE 타입 경고만 필터
    private List<Map<String, Object>> deadCodeWarnings(List<Map<String, Object>> warnings) {
        return warnings.stream().filter(w -> "DEAD_CODE".equals(w.get("type"))).toList();
    }

    @Test
    @DisplayName("미호출 비율 4% 초과(40/40=100%) — 개별 경고 대신 단일 신뢰도 안내로 치환 (C-13 게이트)")
    void deadCode_lowConfidenceGate_collapsesToSingleNotice() {
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            nodes.add(funcNodeWithPath("calc" + i, "/com/example/shared/calc/Calc" + i + ".java"));
        }
        List<Map<String, Object>> dead = deadCodeWarnings(service.detect(nodes, List.of()));
        assertThat(dead).hasSize(1);
        assertThat((String) dead.get(0).get("message")).contains("미호출 함수 비율");
        assertThat((List<?>) dead.get(0).get("nodeIds")).isEmpty();
    }

    @Test
    @DisplayName("미호출 3건/43개(≈7%, 비율은 4% 임계 초과지만 개수<10) — 개수 하한이 소형 진짜 데드코드 보호 (재캘리)")
    void deadCode_belowGateThreshold_keepsIndividualWarnings() {
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        // application/ 함수 40개 — dead 후보에서 제외되지만 전체 함수 수(분모)에는 포함
        for (int i = 0; i < 40; i++) {
            nodes.add(funcNodeWithPath("svc" + i, "/com/example/application/order/Svc" + i + ".java"));
        }
        // 진짜 미호출 함수 3개 (shared/) — 비율 7%는 4% 임계를 넘지만 개수 3<10이라 게이트 미발동
        for (int i = 0; i < 3; i++) {
            nodes.add(funcNodeWithPath("dead" + i, "/com/example/shared/calc/Dead" + i + ".java"));
        }
        List<Map<String, Object>> dead = deadCodeWarnings(service.detect(nodes, List.of()));
        assertThat(dead).hasSize(3);
        assertThat(dead).allSatisfy(w -> assertThat((List<?>) w.get("nodeIds")).isNotEmpty());
    }

    @Test
    @DisplayName("미호출 12건/200개(6%, 비율·개수 둘 다 충족) — 재캘리 4% 게이트 발동, 단일 안내로 치환")
    void deadCode_recalibratedGate_firesAboveFourPercent() {
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        // application/ 함수 188개 — dead 후보 제외, 분모에만 포함
        for (int i = 0; i < 188; i++) {
            nodes.add(funcNodeWithPath("svc" + i, "/com/example/application/order/Svc" + i + ".java"));
        }
        // 진짜 미호출 12개 (shared/) — 12/200=6% ≥4% AND 12 ≥10 → 게이트 (requests 5.3% 약-추출 케이스 대응)
        for (int i = 0; i < 12; i++) {
            nodes.add(funcNodeWithPath("dead" + i, "/com/example/shared/calc/Dead" + i + ".java"));
        }
        List<Map<String, Object>> dead = deadCodeWarnings(service.detect(nodes, List.of()));
        assertThat(dead).hasSize(1);
        assertThat((String) dead.get(0).get("message")).contains("미호출 함수 비율");
    }

    @Test
    @DisplayName("미호출 10건/350개(≈2.9%, 개수는 충족하나 비율<4%) — 게이트 미발동, 개별 유지 (정상 앱의 실제 데드코드 보존)")
    void deadCode_belowRatioWithHighCount_keepsIndividual() {
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        for (int i = 0; i < 340; i++) {
            nodes.add(funcNodeWithPath("svc" + i, "/com/example/application/order/Svc" + i + ".java"));
        }
        // 진짜 미호출 10개 — 10/350≈2.9% <4% → 비율 미달로 게이트 미발동 (개수 10은 충족)
        for (int i = 0; i < 10; i++) {
            nodes.add(funcNodeWithPath("dead" + i, "/com/example/shared/calc/Dead" + i + ".java"));
        }
        List<Map<String, Object>> dead = deadCodeWarnings(service.detect(nodes, List.of()));
        assertThat(dead).hasSize(10);
        assertThat(dead).allSatisfy(w -> assertThat((List<?>) w.get("nodeIds")).isNotEmpty());
    }

    @Test
    @DisplayName("함수 수 30개 미만(5/5=100%)은 게이트 미적용 — 소형 그래프 개별 경고 유지")
    void deadCode_smallGraph_notGated() {
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            nodes.add(funcNodeWithPath("calc" + i, "/com/example/shared/calc/Calc" + i + ".java"));
        }
        List<Map<String, Object>> dead = deadCodeWarnings(service.detect(nodes, List.of()));
        assertThat(dead).hasSize(5);
        assertThat(dead).allSatisfy(w -> assertThat((List<?>) w.get("nodeIds")).isNotEmpty());
    }

    // --- B-13: 같은 파일 호출 sameFile 엣지 (DEAD_CODE 오탐·ASYNC no-op·HIGH_FAN_OUT 보존) ---

    // sameFile 마커가 붙은 같은 파일 내 FUNCTION_CALL 엣지
    private Edge sameFileCallEdge(UUID src, UUID tgt) {
        Edge e = Edge.create(graphId, src + "->sf->" + tgt, EdgeType.FUNCTION_CALL, src, tgt);
        e.updateMetadata(Map.of("sameFile", true));
        return e;
    }

    // HIGH_FAN_OUT 타입 경고만 필터
    private List<Map<String, Object>> highFanOut(List<Map<String, Object>> warnings) {
        return warnings.stream().filter(w -> "HIGH_FAN_OUT".equals(w.get("type"))).toList();
    }

    @Test
    @DisplayName("같은 파일 내에서만 호출되는 함수 — sameFile 엣지가 있으면 DEAD_CODE 제외 (B-13 오탐 해소)")
    void deadCode_sameFileCalled_excluded() {
        Node caller = funcNodeWithPath("verifySignature", "/com/example/shared/webhook/SignatureVerifier.java");
        Node helper = funcNodeWithPath("hmacSha256Hex", "/com/example/shared/webhook/SignatureVerifier.java");
        Edge sameFile = sameFileCallEdge(caller.getId(), helper.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(caller, helper), List.of(sameFile));
        assertThat(isDeadCode(warnings, helper.getId())).isFalse();
    }

    @Test
    @DisplayName("TypeScript async 함수의 같은 파일 호출 — ASYNC_SELF_CALL 제외 (B-14: 프록시 없는 언어 오탐 방지)")
    void asyncSelfCall_typescript_excluded() {
        // TS/JS async는 Spring @Async 같은 프록시 우회가 없어 같은 파일 호출이 정상 — 오탐이면 안 됨
        String file = "/frontend/src/pages/DashboardPage.tsx";
        Node caller = Node.create(graphId, NodeType.FUNCTION, "handleReanalyze", file, "TypeScript");
        Node asyncTarget = Node.create(graphId, NodeType.FUNCTION, "handleStartAnalysis", file, "TypeScript");
        asyncTarget.updateMetadata(Map.of("isAsync", true));
        Edge call = sameFileCallEdge(caller.getId(), asyncTarget.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(caller, asyncTarget), List.of(call));
        assertThat(warnings.stream().filter(w -> "ASYNC_SELF_CALL".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("sameFile 마커 엣지로 @Async 자기 호출 — ASYNC_SELF_CALL 발화 (프로덕션 no-op 해소)")
    void asyncSelfCall_sameFileMarkerEdge_detected() {
        String file = "/com/example/infrastructure/pr/PrReviewRunner.java";
        Node caller = funcNodeWithPath("trigger", file);
        Node asyncTarget = asyncFuncNode("runAsync", file);
        Edge sameFile = sameFileCallEdge(caller.getId(), asyncTarget.getId());

        List<Map<String, Object>> warnings = service.detect(List.of(caller, asyncTarget), List.of(sameFile));
        assertThat(warnings.stream().filter(w -> "ASYNC_SELF_CALL".equals(w.get("type"))).toList()).hasSize(1);
    }

    @Test
    @DisplayName("sameFile 엣지 8개는 HIGH_FAN_OUT 카운트에서 제외 — 경고 없음 (경고량 보존)")
    void highFanOut_sameFileEdges_excluded() {
        Node caller = funcNodeWithPath("orchestrate", "/com/example/shared/Big.java");
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        nodes.add(caller);
        java.util.List<Edge> edges = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Node callee = funcNodeWithPath("step" + i, "/com/example/shared/Big.java");
            nodes.add(callee);
            edges.add(sameFileCallEdge(caller.getId(), callee.getId()));
        }
        List<Map<String, Object>> warnings = service.detect(nodes, edges);
        assertThat(highFanOut(warnings)).isEmpty();
    }

    @Test
    @DisplayName("일반(비sameFile) FUNCTION_CALL 8개는 HIGH_FAN_OUT 발화 — 회귀 방지")
    void highFanOut_normalEdges_stillDetected() {
        Node caller = funcNodeWithPath("orchestrate", "/com/example/shared/Big.java");
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        nodes.add(caller);
        java.util.List<Edge> edges = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Node callee = funcNodeWithPath("step" + i, "/com/example/shared/Dep" + i + ".java");
            nodes.add(callee);
            edges.add(callEdge(caller.getId(), callee.getId(), false));
        }
        List<Map<String, Object>> warnings = service.detect(nodes, edges);
        assertThat(highFanOut(warnings)).hasSize(1);
    }

    @Test
    @DisplayName("파일 내 동명 머지 노드(mergedDefCount≥2)의 fan-out 8개는 HIGH_FAN_OUT 제외 — union으로 부풀린 폴리모픽 머지 오탐 해소")
    void highFanOut_polymorphicMergedNode_excluded() {
        // 한 파일에 동명 메서드(JSON.Render·HTML.Render 등)가 여럿이면 file::name 한 노드로 합쳐져 호출이 union 되어
        // fan-out이 부풀려진다. GraphBuilder가 이 노드에 mergedDefCount≥2를 표시하면 단일 책임 신호로 신뢰 불가 → 제외.
        Node render = funcNodeWithPath("Render", "/gin/render/render.go");
        render.updateMetadata(Map.of("mergedDefCount", 6)); // 한 파일에 6개 Render 정의가 한 노드로 머지됨
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        nodes.add(render);
        java.util.List<Edge> edges = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Node callee = funcNodeWithPath("util" + i, "/gin/render/dep" + i + ".go");
            nodes.add(callee);
            edges.add(callEdge(render.getId(), callee.getId(), false));
        }
        List<Map<String, Object>> warnings = service.detect(nodes, edges);
        assertThat(highFanOut(warnings)).isEmpty();
    }

    @Test
    @DisplayName("서로 다른 파일의 동명 함수(각자 단일 정의)는 머지가 아니므로 그 이름으로 호출이 있어도 fan-out 발화 — 전역 이름 휴리스틱의 과잉 억제 해소")
    void highFanOut_distinctSameNameDifferentFiles_stillDetected() {
        // 옛 전역 가드는 "이름이 2+ 파일에 정의 + 호출 존재"면 무조건 억제해, 서로 다른 두 validate()가 각자 진짜
        // 고-fan-out이어도 거짓 억제했다. 정밀 가드는 노드별 mergedDefCount만 보므로 머지 아닌 별개 정의는 정상 발화한다.
        Node validateA = funcNodeWithPath("validate", "/com/a/A.java"); // 단일 정의 (mergedDefCount 없음)
        Node validateB = funcNodeWithPath("validate", "/com/b/B.java"); // 다른 파일의 별개 정의
        Node dispatcher = funcNodeWithPath("run", "/com/c/C.java");
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        nodes.add(validateA);
        nodes.add(validateB);
        nodes.add(dispatcher);
        java.util.List<Edge> edges = new java.util.ArrayList<>();
        edges.add(callEdge(dispatcher.getId(), validateB.getId(), false)); // "validate" 이름으로 호출 존재
        for (int i = 0; i < 8; i++) {
            Node callee = funcNodeWithPath("step" + i, "/com/a/dep" + i + ".java");
            nodes.add(callee);
            edges.add(callEdge(validateA.getId(), callee.getId(), false));
        }
        List<Map<String, Object>> warnings = service.detect(nodes, edges);
        assertThat(highFanOut(warnings)).hasSize(1); // validateA의 fan-out 8은 머지 아니므로 발화
    }

    @Test
    @DisplayName("Controller 파일의 호출 8개는 HIGH_FAN_OUT 제외 — 엔드포인트 조율자는 협력자를 모아 조립하는 게 본연의 역할")
    void highFanOut_controllerFile_excluded() {
        Node handler = funcNodeWithPath("getGraph", "/com/codeprint/interfaces/api/GraphController.java");
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        nodes.add(handler);
        java.util.List<Edge> edges = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Node callee = funcNodeWithPath("step" + i, "/com/codeprint/application/graph/dep" + i + ".java");
            nodes.add(callee);
            edges.add(callEdge(handler.getId(), callee.getId(), false));
        }
        List<Map<String, Object>> warnings = service.detect(nodes, edges);
        assertThat(highFanOut(warnings)).isEmpty();
    }

    @Test
    @DisplayName("application/ 아래 ApplicationService 파일의 호출 8개는 HIGH_FAN_OUT 제외 — 유스케이스 오케스트레이션")
    void highFanOut_applicationServiceFile_excluded() {
        Node useCase = funcNodeWithPath("confirm", "/com/codeprint/application/payment/PaymentApplicationService.java");
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        nodes.add(useCase);
        java.util.List<Edge> edges = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Node callee = funcNodeWithPath("step" + i, "/com/codeprint/domain/payment/dep" + i + ".java");
            nodes.add(callee);
            edges.add(callEdge(useCase.getId(), callee.getId(), false));
        }
        List<Map<String, Object>> warnings = service.detect(nodes, edges);
        assertThat(highFanOut(warnings)).isEmpty();
    }

    @Test
    @DisplayName("이름이 *Inner로 끝나는 함수의 호출 8개는 HIGH_FAN_OUT 제외 — 프론트 페이지 합성 루트 관례")
    void highFanOut_frontendInnerComponent_excluded() {
        Node pageRoot = funcNodeWithPath("GraphPageInner", "/frontend/src/pages/GraphPage.tsx");
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        nodes.add(pageRoot);
        java.util.List<Edge> edges = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Node callee = funcNodeWithPath("handler" + i, "/frontend/src/pages/GraphPage.tsx");
            nodes.add(callee);
            edges.add(callEdge(pageRoot.getId(), callee.getId(), false));
        }
        List<Map<String, Object>> warnings = service.detect(nodes, edges);
        assertThat(highFanOut(warnings)).isEmpty();
    }

    @Test
    @DisplayName("테스트 함수(_test.go)의 호출 8개는 HIGH_FAN_OUT 제외 — 테스트는 setup+assert로 자연히 다호출 (Phase 1 #3)")
    void highFanOut_testFunction_excluded() {
        // gin TestLoggerWithConfig 등 *_test.go의 Test 함수가 단일 책임 위반으로 오탐되던 노이즈 제거
        Node testFn = funcNodeWithPath("TestLoggerWithConfig", "/gin/logger_test.go");
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        nodes.add(testFn);
        java.util.List<Edge> edges = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Node callee = funcNodeWithPath("helper" + i, "/gin/dep" + i + ".go");
            nodes.add(callee);
            edges.add(callEdge(testFn.getId(), callee.getId(), false));
        }
        List<Map<String, Object>> warnings = service.detect(nodes, edges);
        assertThat(highFanOut(warnings)).isEmpty();
    }

    @Test
    @DisplayName("루트 레벨 tests/ 디렉터리(상대경로)의 호출 8개는 HIGH_FAN_OUT 제외 — 테스트 헬퍼 노이즈 (재캘리)")
    void highFanOut_rootLevelTestDir_excluded() {
        // requests/conftest 류 픽스처 헬퍼가 루트 tests/ 에 있어 "/tests/" 매칭 실패하던 버그
        Node testHelper = funcNodeWithPath("response_handler", "tests/testserver.py");
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        nodes.add(testHelper);
        java.util.List<Edge> edges = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Node callee = funcNodeWithPath("step" + i, "src/dep" + i + ".py");
            nodes.add(callee);
            edges.add(callEdge(testHelper.getId(), callee.getId(), false));
        }
        List<Map<String, Object>> warnings = service.detect(nodes, edges);
        assertThat(highFanOut(warnings)).isEmpty();
    }

    @Test
    @DisplayName("Swift 테스트 파일(*Tests.swift)의 호출 8개는 HIGH_FAN_OUT 제외 — XCTest 메서드 setup+assert 다호출 노이즈 (Swift AST)")
    void highFanOut_swiftTestFile_excluded() {
        // Alamofire SessionTests.swift 등 *Tests.swift 의 testThat 메서드가 단일 책임 위반으로 오탐되던 노이즈 제거.
        // Swift 테스트는 Tests/(대문자) 디렉터리라 경로 매칭이 빗나가 파일명 접미사로 제외한다.
        Node testFn = funcNodeWithPath("testThatRequestsCanBeMassCancelled", "/Tests/SessionTests.swift");
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        nodes.add(testFn);
        java.util.List<Edge> edges = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Node callee = funcNodeWithPath("helper" + i, "/Source/dep" + i + ".swift");
            nodes.add(callee);
            edges.add(callEdge(testFn.getId(), callee.getId(), false));
        }
        List<Map<String, Object>> warnings = service.detect(nodes, edges);
        assertThat(highFanOut(warnings)).isEmpty();
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

    // ── INTENT_DRIFT — 의도↔실제 conformance ────────────────────────────────

    private Node nodeAt(String name, String filePath) {
        return Node.create(graphId, NodeType.FILE, name, filePath, "java");
    }

    private ArchitectureIntent forbidIntent(String fromGlob, String fromName, String toGlob, String toName) {
        return new ArchitectureIntent(
                List.of(new ArchitectureIntent.Module(fromName, List.of(fromGlob)),
                        new ArchitectureIntent.Module(toName, List.of(toGlob))),
                List.of(new ArchitectureIntent.DependencyRule(fromName, toName)));
    }

    @Test
    @DisplayName("INTENT_DRIFT — 금지 모듈 의존(import)을 어기면 경고")
    void intentDrift_forbiddenImport_warns() {
        Node domain = nodeAt("User", "/com/example/domain/user/User.java");
        Node infra = nodeAt("UserJpa", "/com/example/infrastructure/user/UserJpa.java");
        ArchitectureIntent intent = forbidIntent("**/domain/**", "domain", "**/infrastructure/**", "infrastructure");

        List<Map<String, Object>> warnings = service.detect(
                List.of(domain, infra),
                List.of(importEdgeForPath(domain.getId(), infra.getId())), intent);

        assertThat(warnings).anySatisfy(w -> {
            assertThat(w.get("type")).isEqualTo("INTENT_DRIFT");
            assertThat(w.get("severity")).isEqualTo("HIGH");
            assertThat((String) w.get("message")).contains("domain", "infrastructure");
        });
    }

    @Test
    @DisplayName("INTENT_DRIFT — 허용 방향(역방향)은 경고하지 않음")
    void intentDrift_allowedDirection_silent() {
        Node domain = nodeAt("User", "/com/example/domain/user/User.java");
        Node infra = nodeAt("UserJpa", "/com/example/infrastructure/user/UserJpa.java");
        // domain→infrastructure만 금지 — infrastructure→domain은 허용
        ArchitectureIntent intent = forbidIntent("**/domain/**", "domain", "**/infrastructure/**", "infrastructure");

        List<Map<String, Object>> warnings = service.detect(
                List.of(domain, infra),
                List.of(importEdgeForPath(infra.getId(), domain.getId())), intent);

        assertThat(warnings).noneMatch(w -> "INTENT_DRIFT".equals(w.get("type")));
    }

    @Test
    @DisplayName("INTENT_DRIFT — 같은 모듈 내부 의존은 경고하지 않음")
    void intentDrift_sameModule_silent() {
        Node a = nodeAt("A", "/com/example/domain/user/A.java");
        Node b = nodeAt("B", "/com/example/domain/user/B.java");
        ArchitectureIntent intent = forbidIntent("**/domain/**", "domain", "**/infrastructure/**", "infrastructure");

        List<Map<String, Object>> warnings = service.detect(
                List.of(a, b),
                List.of(importEdgeForPath(a.getId(), b.getId())), intent);

        assertThat(warnings).noneMatch(w -> "INTENT_DRIFT".equals(w.get("type")));
    }

    @Test
    @DisplayName("INTENT_DRIFT — FUNCTION_CALL(인터페이스→구현체 해소)은 트리거하지 않음 (IMPORT 전용)")
    void intentDrift_functionCallNotImport_silent() {
        // domain 포트 인터페이스 → infra 구현체로 해소된 FUNCTION_CALL 엣지는 정당한 의존성 역전(port/adapter)이라 오탐이면 안 됨
        Node port = nodeAt("UserRepository", "/com/example/domain/user/UserRepository.java");
        Node impl = nodeAt("UserRepositoryImpl", "/com/example/infrastructure/user/UserRepositoryImpl.java");
        ArchitectureIntent intent = forbidIntent("**/domain/**", "domain", "**/infrastructure/**", "infrastructure");

        List<Map<String, Object>> warnings = service.detect(
                List.of(port, impl),
                List.of(callEdge(port.getId(), impl.getId(), true)), intent);

        assertThat(warnings).noneMatch(w -> "INTENT_DRIFT".equals(w.get("type")));
    }

    @Test
    @DisplayName("INTENT_DRIFT — intent가 null이면 검사하지 않음(하위호환)")
    void intentDrift_nullIntent_silent() {
        Node domain = nodeAt("User", "/com/example/domain/user/User.java");
        Node infra = nodeAt("UserJpa", "/com/example/infrastructure/user/UserJpa.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(domain, infra),
                List.of(importEdgeForPath(domain.getId(), infra.getId())));

        assertThat(warnings).noneMatch(w -> "INTENT_DRIFT".equals(w.get("type")));
    }

    // ===== LAYERED ARCHITECTURE (비DDD 프로젝트 레이어 컨벤션 위반) =====

    @Test
    @DisplayName("LAYERED_BYPASS — service 레이어 존재 시 Controller가 Repository를 직접 import")
    void layeredBypass_detected() {
        Node controller = nodeAt("OwnerController", "/app/web/OwnerController.java");
        Node svc = nodeAt("OwnerService", "/app/service/OwnerService.java");
        Node repo = nodeAt("OwnerRepository", "/app/repo/OwnerRepository.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(controller, svc, repo),
                List.of(importEdgeForPath(controller.getId(), repo.getId())));

        assertThat(warnings).anySatisfy(w -> assertThat(w.get("type")).isEqualTo("LAYERED_BYPASS"));
    }

    @Test
    @DisplayName("LAYERED_BYPASS 미발생 — service 레이어 없으면 Controller→Repository는 정상(petclinic 패턴)")
    void layeredBypass_notDetected_whenNoServiceLayer() {
        Node controller = nodeAt("OwnerController", "/app/owner/OwnerController.java");
        Node repo = nodeAt("OwnerRepository", "/app/owner/OwnerRepository.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(controller, repo),
                List.of(importEdgeForPath(controller.getId(), repo.getId())));

        assertThat(warnings).noneMatch(w -> "LAYERED_BYPASS".equals(w.get("type")));
    }

    @Test
    @DisplayName("LAYERED_REVERSE_DEPENDENCY — Repository가 Controller를 import (레이어 역전)")
    void layeredReverse_detected() {
        Node controller = nodeAt("OwnerController", "/app/web/OwnerController.java");
        Node repo = nodeAt("OwnerRepository", "/app/repo/OwnerRepository.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(controller, repo),
                List.of(importEdgeForPath(repo.getId(), controller.getId())));

        assertThat(warnings).anySatisfy(w -> assertThat(w.get("type")).isEqualTo("LAYERED_REVERSE_DEPENDENCY"));
    }

    @Test
    @DisplayName("Layered 정방향 import(Controller→Service→Repository)는 경고 없음")
    void layeredNormalDirection_noWarning() {
        Node controller = nodeAt("OwnerController", "/app/web/OwnerController.java");
        Node svc = nodeAt("OwnerService", "/app/service/OwnerService.java");
        Node repo = nodeAt("OwnerRepository", "/app/repo/OwnerRepository.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(controller, svc, repo),
                List.of(importEdgeForPath(controller.getId(), svc.getId()),
                        importEdgeForPath(svc.getId(), repo.getId())));

        assertThat(warnings).noneMatch(w -> String.valueOf(w.get("type")).startsWith("LAYERED_"));
    }

    @Test
    @DisplayName("Layered 디렉터리 컨벤션 — 클래스명 접미사 없이 controllers/·services/·repositories/ 로 레이어 인식")
    void layered_directoryConvention() {
        Node controller = nodeAt("handler", "/src/controllers/handler.js");
        Node svc = nodeAt("logic", "/src/services/logic.js");
        Node repo = nodeAt("queries", "/src/repositories/queries.js");

        List<Map<String, Object>> warnings = service.detect(
                List.of(controller, svc, repo),
                List.of(importEdgeForPath(repo.getId(), controller.getId())));

        assertThat(warnings).anySatisfy(w -> assertThat(w.get("type")).isEqualTo("LAYERED_REVERSE_DEPENDENCY"));
    }

    @Test
    @DisplayName("Layered 미적용 — 단일 레이어(Controller만 분류)면 경고 없음")
    void layered_notApplied_singleLayer() {
        Node c1 = nodeAt("FooController", "/app/FooController.java");
        Node c2 = nodeAt("BarController", "/app/BarController.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(c1, c2),
                List.of(importEdgeForPath(c1.getId(), c2.getId())));

        assertThat(warnings).noneMatch(w -> String.valueOf(w.get("type")).startsWith("LAYERED_"));
    }

    @Test
    @DisplayName("Layered 미적용 — DDD 프로젝트(domain/application/infrastructure)는 LAYERED 경고를 내지 않음")
    void layered_notApplied_whenDddProject() {
        Node controller = nodeAt("OwnerController", "/app/interfaces/OwnerController.java");
        Node appSvc = nodeAt("OwnerService", "/app/application/owner/OwnerService.java");
        Node model = nodeAt("Owner", "/app/domain/owner/Owner.java");
        Node repo = nodeAt("OwnerRepository", "/app/infrastructure/persistence/OwnerRepository.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(controller, appSvc, model, repo),
                List.of(importEdgeForPath(controller.getId(), repo.getId())));

        assertThat(warnings).noneMatch(w -> String.valueOf(w.get("type")).startsWith("LAYERED_"));
    }

    @Test
    @DisplayName("Layered 테스트 코드 제외 — 테스트 경로 노드는 레이어 분류 대상 아님")
    void layered_excludesTestArtifacts() {
        Node controller = nodeAt("OwnerController", "/app/web/OwnerController.java");
        Node svc = nodeAt("OwnerService", "/app/service/OwnerService.java");
        Node testRepo = nodeAt("OwnerRepository", "/src/test/java/app/OwnerRepository.java");

        // 테스트 파일(OwnerRepository)에서 Controller로의 역전 import는 분류 제외라 무시되어야 함
        List<Map<String, Object>> warnings = service.detect(
                List.of(controller, svc, testRepo),
                List.of(importEdgeForPath(testRepo.getId(), controller.getId())));

        assertThat(warnings).noneMatch(w -> String.valueOf(w.get("type")).startsWith("LAYERED_"));
    }

    @Test
    @DisplayName("LAYERED_REVERSE_DEPENDENCY — 도메인 모델(Entity)이 Controller를 import (최하위→최상위 역전)")
    void layeredReverse_modelImportsController() {
        Node model = nodeAt("OwnerEntity", "/app/model/OwnerEntity.java");
        Node controller = nodeAt("OwnerController", "/app/web/OwnerController.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(model, controller),
                List.of(importEdgeForPath(model.getId(), controller.getId())));

        assertThat(warnings).anySatisfy(w -> {
            assertThat(w.get("type")).isEqualTo("LAYERED_REVERSE_DEPENDENCY");
            assertThat(String.valueOf(w.get("message"))).contains("Model").contains("Controller");
        });
    }

    @Test
    @DisplayName("Model 분류 — domain/ 디렉터리 파일이 Service를 import해도 정방향(Service→Model 아님)이면 역전만 검사")
    void layered_modelLayer_reverseFromDomainDir() {
        // domain/ 단일 디렉터리는 비DDD 레이어드의 모델 폴더로 분류(접미사 없는 Owner도 포착)
        Node model = nodeAt("Owner", "/app/domain/Owner.java");
        Node svc = nodeAt("OwnerService", "/app/service/OwnerService.java");

        // model → service 역전(3 > 1)
        List<Map<String, Object>> warnings = service.detect(
                List.of(model, svc),
                List.of(importEdgeForPath(model.getId(), svc.getId())));

        assertThat(warnings).anySatisfy(w -> assertThat(w.get("type")).isEqualTo("LAYERED_REVERSE_DEPENDENCY"));
    }

    @Test
    @DisplayName("LAYERED_REVERSE_DEPENDENCY 미발화 — FSD 피처-슬라이스 게이트 (fsd-examples 실측: entities→shared/api가 " +
            "CONTROLLER_DIRS 'api' 별칭에 오분류돼 레이어 역전 오탐이던 것을 전용 FEATURE_LAYER_VIOLATION 게이트로 스킵)")
    void layeredReverse_fsdFeatureSliced_gated() {
        Node entity = Node.create(graphId, NodeType.FILE, "lib.ts", "entities/task/lib.ts", "typescript");
        Node sharedApi = Node.create(graphId, NodeType.FILE, "index.ts", "shared/api/index.ts", "typescript");
        // 게이트가 요구하는 피처 2개 이상(features/{X}/) 동반
        Node featureA = Node.create(graphId, NodeType.FILE, "ui.ts", "features/toggle-task/ui.ts", "typescript");
        Node featureB = Node.create(graphId, NodeType.FILE, "ui.ts", "features/add-task/ui.ts", "typescript");

        List<Map<String, Object>> warnings = service.detect(
                List.of(entity, sharedApi, featureA, featureB),
                List.of(importEdgeForPath(entity.getId(), sharedApi.getId())));

        assertThat(warnings).noneMatch(w -> "LAYERED_REVERSE_DEPENDENCY".equals(w.get("type")));
    }

    @Test
    @DisplayName("Model 정방향 — Service가 Model을 import하는 것은 정상(경고 없음)")
    void layered_serviceImportsModel_noWarning() {
        Node svc = nodeAt("OwnerService", "/app/service/OwnerService.java");
        Node model = nodeAt("OwnerEntity", "/app/model/OwnerEntity.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(svc, model),
                List.of(importEdgeForPath(svc.getId(), model.getId())));

        assertThat(warnings).noneMatch(w -> String.valueOf(w.get("type")).startsWith("LAYERED_"));
    }

    @Test
    @DisplayName("HIGH_FAN_OUT — main 진입점은 호출이 많아도 제외(부트스트랩은 SRP 위반 아님)")
    void highFanOut_excludesMainEntryPoint() {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        Node main = funcNodeWithPath("main", "/app/Application.java");
        nodes.add(main);
        for (int i = 0; i < 9; i++) {
            Node callee = funcNodeWithPath("dep" + i, "/app/Dep" + i + ".java");
            nodes.add(callee);
            edges.add(callEdge(main.getId(), callee.getId(), false));
        }

        List<Map<String, Object>> warnings = service.detect(nodes, edges);

        assertThat(warnings).noneMatch(w -> "HIGH_FAN_OUT".equals(w.get("type")));
    }

    @Test
    @DisplayName("HIGH_FAN_OUT — main이 아닌 일반 함수는 7개 초과 호출 시 감지(대조)")
    void highFanOut_detectsNonMainFunction() {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        Node orchestrator = funcNodeWithPath("doEverything", "/app/Big.java");
        nodes.add(orchestrator);
        for (int i = 0; i < 9; i++) {
            Node callee = funcNodeWithPath("dep" + i, "/app/Dep" + i + ".java");
            nodes.add(callee);
            edges.add(callEdge(orchestrator.getId(), callee.getId(), false));
        }

        List<Map<String, Object>> warnings = service.detect(nodes, edges);

        assertThat(warnings).anySatisfy(w -> assertThat(w.get("type")).isEqualTo("HIGH_FAN_OUT"));
    }

    // ── 게이트 테마(detectActiveTheme) — 안정성 갭 이후 신규 기능(PROGRESS.md "게이트 테마") ──────────────

    @Test
    @DisplayName("detectActiveTheme — domain/application/infrastructure 폴더가 있으면 DDD 테마, 규칙 3종(바운디드 컨텍스트 축만), AUTO는 selfDeclared false")
    void detectActiveTheme_dddStructure_returnsDdd() {
        // 의존 방향 축(DOMAIN_IMPORTS_INFRA·INTERFACES_IMPORTS_INFRA)은 공통 게이트로 승격돼 themeRuleTypes가 아닌
        // universalRuleTypes에 포함된다(2026-07-17 — decisions/DECISIONS_ANALYSIS.md 참조) — DDD 테마 고유는 바운디드
        // 컨텍스트 축(DB_LAYER_BYPASS·CROSS_CONTEXT_IMPORT·CROSS_DOMAIN_CALL) 3종만 남는다.
        List<Node> nodes = List.of(
                fileNodeWithPath("A", "src/domain/user/User.java"),
                fileNodeWithPath("B", "src/application/user/UserService.java"),
                fileNodeWithPath("C", "src/infrastructure/UserRepositoryImpl.java"));

        var theme = service.detectActiveTheme(nodes, List.of(), GatePolicy.AUTO);

        assertThat(theme.theme()).isEqualTo("DDD");
        assertThat(theme.themeRuleTypes()).hasSize(3);
        assertThat(theme.universalRuleTypes()).contains("DOMAIN_IMPORTS_INFRA", "INTERFACES_IMPORTS_INFRA");
        assertThat(theme.dddDetected()).isTrue();
        assertThat(theme.gatePolicy()).isEqualTo(GatePolicy.AUTO);
        assertThat(theme.selfDeclared()).isFalse();
    }

    @Test
    @DisplayName("detectActiveTheme — DDD 폴더가 없어도 DDD 정책이면 DDD 테마로 강제 적용, selfDeclared true")
    void detectActiveTheme_dddPolicy_forcesDdd() {
        List<Node> nodes = List.of(fileNodeWithPath("A", "src/main/Flat.java"));

        var theme = service.detectActiveTheme(nodes, List.of(), GatePolicy.DDD);

        assertThat(theme.theme()).isEqualTo("DDD");
        assertThat(theme.dddDetected()).isFalse();
        assertThat(theme.gatePolicy()).isEqualTo(GatePolicy.DDD);
        assertThat(theme.selfDeclared()).isTrue();
    }

    @Test
    @DisplayName("detectActiveTheme — Controller/Service/Repository 2종 이상이면 레이어드 테마, 규칙 2종")
    void detectActiveTheme_layeredStructure_returnsLayered() {
        List<Node> nodes = List.of(
                fileNodeWithPath("UserController", "src/controllers/UserController.java"),
                fileNodeWithPath("UserService", "src/services/UserService.java"));

        var theme = service.detectActiveTheme(nodes, List.of(), GatePolicy.AUTO);

        assertThat(theme.theme()).isEqualTo("LAYERED");
        assertThat(theme.themeRuleTypes()).hasSize(2);
    }

    @Test
    @DisplayName("detectActiveTheme — LAYERED 정책 강제 시 DDD 폴더가 있어도 LAYERED 테마로 전환, selfDeclared true")
    void detectActiveTheme_layeredPolicy_forcesLayeredOverDddDetection() {
        List<Node> nodes = List.of(
                fileNodeWithPath("A", "src/domain/user/User.java"),
                fileNodeWithPath("B", "src/application/user/UserService.java"),
                fileNodeWithPath("C", "src/infrastructure/UserRepositoryImpl.java"));

        var theme = service.detectActiveTheme(nodes, List.of(), GatePolicy.LAYERED);

        assertThat(theme.theme()).isEqualTo("LAYERED");
        assertThat(theme.themeRuleTypes()).hasSize(2);
        assertThat(theme.dddDetected()).isTrue();
        assertThat(theme.gatePolicy()).isEqualTo(GatePolicy.LAYERED);
        assertThat(theme.selfDeclared()).isTrue();
    }

    @Test
    @DisplayName("detectActiveTheme — 어느 구조에도 안 걸리면 GENERIC, 테마 규칙 0종")
    void detectActiveTheme_flatStructure_returnsGeneric() {
        List<Node> nodes = List.of(fileNodeWithPath("A", "src/Main.java"));

        var theme = service.detectActiveTheme(nodes, List.of(), GatePolicy.AUTO);

        assertThat(theme.theme()).isEqualTo("GENERIC");
        assertThat(theme.themeRuleTypes()).isEmpty();
        assertThat(theme.universalRuleTypes()).isNotEmpty();
    }

    @Test
    @DisplayName("DDD 정책이 켜지면 실제 detect()도 DDD 전용 규칙(DB_LAYER_BYPASS, 바운디드 컨텍스트 축)을 적용한다")
    void detect_dddPolicy_appliesDddOnlyRules() {
        // interfaces/+infrastructure/persistence/ 조합은 isDddProject() 판정용 3종(domain/application/infrastructure) 중
        // infrastructure 하나만 걸쳐 자동감지로는 DDD 프로젝트로 잡히지 않는다 — 정책 자체의 효과만 순수하게 검증.
        // (INTERFACES_IMPORTS_INFRA는 2026-07-17 공통 게이트로 승격돼 정책 무관 항상 적용되므로 더 이상 이 구분에 안 맞음
        // — decisions/DECISIONS_ANALYSIS.md 참조. 대신 여전히 DDD 전용인 DB_LAYER_BYPASS로 검증한다.)
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        Node controllerFile = fileNodeWithPath("OrderController", "src/interfaces/OrderController.java");
        Node persistFile = fileNodeWithPath("OrderRepositoryImpl", "src/infrastructure/persistence/OrderRepositoryImpl.java");
        nodes.add(controllerFile);
        nodes.add(persistFile);
        edges.add(importEdge(controllerFile.getId(), persistFile.getId()));

        List<Map<String, Object>> withAuto = service.detect(nodes, edges, null, GatePolicy.AUTO);
        List<Map<String, Object>> withDdd = service.detect(nodes, edges, null, GatePolicy.DDD);

        assertThat(withAuto).noneSatisfy(w -> assertThat(w.get("type")).isEqualTo("DB_LAYER_BYPASS"));
        assertThat(withDdd).anySatisfy(w -> assertThat(w.get("type")).isEqualTo("DB_LAYER_BYPASS"));
    }

    @Test
    @DisplayName("LAYERED 정책을 강제 선언해도 공통 게이트(DOMAIN_IMPORTS_INFRA)는 계속 적용된다 (헥사고날 사각지대 해소)")
    void detect_layeredPolicyForced_stillAppliesUniversalDependencyRule() {
        // 사용자가 domain/infrastructure 구조를 가진(=구조상 DDD/헥사고날) 레포에 LAYERED를 직접 강제 선언해도,
        // 의존 방향 축(도메인→인프라)은 정책 선택과 무관한 보편 원칙이라 계속 잡혀야 한다 — 2026-07-17 사용자 논의로
        // 발견된 사각지대(공통게이트/특화게이트 축 분리) 해소를 검증. decisions/DECISIONS_ANALYSIS.md 참조.
        Node domainFile = fileNodeWithPath("Order", "src/domain/Order.java");
        Node infraFile = fileNodeWithPath("OrderAdapter", "src/adapter/OrderAdapter.java");
        Edge imp = importEdge(domainFile.getId(), infraFile.getId());

        List<Map<String, Object>> withLayered = service.detect(
                List.of(domainFile, infraFile), List.of(imp), null, GatePolicy.LAYERED);

        assertThat(withLayered).anySatisfy(w -> assertThat(w.get("type")).isEqualTo("DOMAIN_IMPORTS_INFRA"));
    }

    @Test
    @DisplayName("LAYERED 강제 시 공통 게이트와 겹치는 엣지는 LAYERED_REVERSE_DEPENDENCY로 중복 라벨링하지 않는다")
    void detect_layeredPolicyForced_dedupesOverlapWithUniversalRule() {
        // INFRA_LAYER_DIRS와 REPOSITORY_DIRS가 "persistence"를 공유해 domain→infrastructure/persistence/ 엣지가
        // Layer.MODEL→Layer.REPOSITORY로도 분류돼 LAYERED_REVERSE_DEPENDENCY와 겹칠 수 있다 — coveredByUniversalDependencyRule
        // 가드가 이 중복을 막는지 검증. decisions/DECISIONS_ANALYSIS.md 참조.
        Node domainFile = fileNodeWithPath("Order", "src/domain/Order.java");
        Node persistFile = fileNodeWithPath("OrderRepositoryImpl", "src/infrastructure/persistence/OrderRepositoryImpl.java");
        Edge imp = importEdge(domainFile.getId(), persistFile.getId());

        List<Map<String, Object>> withLayered = service.detect(
                List.of(domainFile, persistFile), List.of(imp), null, GatePolicy.LAYERED);

        assertThat(withLayered).anySatisfy(w -> assertThat(w.get("type")).isEqualTo("DOMAIN_IMPORTS_INFRA"));
        assertThat(withLayered).noneSatisfy(w -> assertThat(w.get("type")).isEqualTo("LAYERED_REVERSE_DEPENDENCY"));
    }

    @Test
    @DisplayName("LAYERED 정책이 켜지면 DDD 폴더가 있어도 실제 detect()는 레이어드 규칙(LAYERED_REVERSE_DEPENDENCY)을 적용한다")
    void detect_layeredPolicy_appliesLayeredRulesOverDddDetection() {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        Node domainFile = fileNodeWithPath("Marker", "src/domain/Marker.java");
        Node appFile = fileNodeWithPath("MarkerApp", "src/application/MarkerApp.java");
        Node controllerFile = fileNodeWithPath("OrderController", "src/infrastructure/OrderController.java");
        Node repoFile = fileNodeWithPath("OrderRepository", "src/infrastructure/OrderRepository.java");
        nodes.add(domainFile);
        nodes.add(appFile);
        nodes.add(controllerFile);
        nodes.add(repoFile);
        // Repository → Controller 직접 import = 하위 레이어가 상위 레이어를 아는 역전
        edges.add(importEdge(repoFile.getId(), controllerFile.getId()));

        List<Map<String, Object>> withAuto = service.detect(nodes, edges, null, GatePolicy.AUTO);
        List<Map<String, Object>> withLayered = service.detect(nodes, edges, null, GatePolicy.LAYERED);

        assertThat(withAuto).noneSatisfy(w -> assertThat(w.get("type")).isEqualTo("LAYERED_REVERSE_DEPENDENCY"));
        assertThat(withLayered).anySatisfy(w -> assertThat(w.get("type")).isEqualTo("LAYERED_REVERSE_DEPENDENCY"));
    }

    // ===== SHARED_DATABASE_ACCESS (모노레포 MSA shared database 안티패턴, 2026-07-17) =====

    private Edge dbAccessEdge(UUID src, UUID tgt, EdgeType type) {
        return Edge.create(graphId, src + "->db->" + tgt, type, src, tgt);
    }

    @Test
    @DisplayName("SHARED_DATABASE_ACCESS — 서로 다른 서비스 2개가 같은 테이블에 쓰기")
    void sharedDatabaseAccess_detected() {
        Node customersFile = fileNodeWithPath("OwnerRepository",
                "spring-petclinic-customers-service/src/main/java/OwnerRepository.java");
        Node visitsFile = fileNodeWithPath("OwnerCache",
                "spring-petclinic-visits-service/src/main/java/OwnerCache.java");
        Node ownersTable = Node.create(graphId, NodeType.DB_TABLE, "owners", "OwnerRepository.java", "java");
        Edge writeEdge = dbAccessEdge(customersFile.getId(), ownersTable.getId(), EdgeType.DB_WRITE);
        Edge readEdge = dbAccessEdge(visitsFile.getId(), ownersTable.getId(), EdgeType.DB_READ);

        List<Map<String, Object>> warnings = service.detect(
                List.of(customersFile, visitsFile, ownersTable), List.of(writeEdge, readEdge));

        List<Map<String, Object>> found = warnings.stream()
                .filter(w -> "SHARED_DATABASE_ACCESS".equals(w.get("type"))).toList();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).get("severity")).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("precision: 단일 서비스만 DB 접근 — SHARED_DATABASE_ACCESS 미발화 (모노레포 MSA 아님)")
    void sharedDatabaseAccess_singleService_notFlagged() {
        Node fileA = fileNodeWithPath("OwnerRepository",
                "spring-petclinic-customers-service/src/main/java/OwnerRepository.java");
        Node fileB = fileNodeWithPath("OwnerService",
                "spring-petclinic-customers-service/src/main/java/OwnerService.java");
        Node ownersTable = Node.create(graphId, NodeType.DB_TABLE, "owners", "OwnerRepository.java", "java");
        Edge writeEdge = dbAccessEdge(fileA.getId(), ownersTable.getId(), EdgeType.DB_WRITE);
        Edge readEdge = dbAccessEdge(fileB.getId(), ownersTable.getId(), EdgeType.DB_READ);

        List<Map<String, Object>> warnings = service.detect(
                List.of(fileA, fileB, ownersTable), List.of(writeEdge, readEdge));

        assertThat(warnings.stream().filter(w -> "SHARED_DATABASE_ACCESS".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: 서비스 2개지만 서로 다른 테이블 — SHARED_DATABASE_ACCESS 미발화 (database-per-service 정상)")
    void sharedDatabaseAccess_differentTables_notFlagged() {
        Node customersFile = fileNodeWithPath("OwnerRepository",
                "spring-petclinic-customers-service/src/main/java/OwnerRepository.java");
        Node vetsFile = fileNodeWithPath("VetRepository",
                "spring-petclinic-vets-service/src/main/java/VetRepository.java");
        Node ownersTable = Node.create(graphId, NodeType.DB_TABLE, "owners", "OwnerRepository.java", "java");
        Node vetsTable = Node.create(graphId, NodeType.DB_TABLE, "vets", "VetRepository.java", "java");
        Edge writeOwners = dbAccessEdge(customersFile.getId(), ownersTable.getId(), EdgeType.DB_WRITE);
        Edge writeVets = dbAccessEdge(vetsFile.getId(), vetsTable.getId(), EdgeType.DB_WRITE);

        List<Map<String, Object>> warnings = service.detect(
                List.of(customersFile, vetsFile, ownersTable, vetsTable), List.of(writeOwners, writeVets));

        assertThat(warnings.stream().filter(w -> "SHARED_DATABASE_ACCESS".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: 테스트 코드의 DB 접근은 서비스 판정에서 제외 — SHARED_DATABASE_ACCESS 미발화")
    void sharedDatabaseAccess_testSource_excluded() {
        Node customersFile = fileNodeWithPath("OwnerRepository",
                "spring-petclinic-customers-service/src/main/java/OwnerRepository.java");
        Node visitsTestFile = fileNodeWithPath("OwnerRepositoryTest",
                "spring-petclinic-visits-service/src/test/java/OwnerRepositoryTest.java");
        Node ownersTable = Node.create(graphId, NodeType.DB_TABLE, "owners", "OwnerRepository.java", "java");
        Edge writeEdge = dbAccessEdge(customersFile.getId(), ownersTable.getId(), EdgeType.DB_WRITE);
        Edge testReadEdge = dbAccessEdge(visitsTestFile.getId(), ownersTable.getId(), EdgeType.DB_READ);

        List<Map<String, Object>> warnings = service.detect(
                List.of(customersFile, visitsTestFile, ownersTable), List.of(writeEdge, testReadEdge));

        assertThat(warnings.stream().filter(w -> "SHARED_DATABASE_ACCESS".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("SHARED_DATABASE_ACCESS — services/ 래퍼 디렉터리 다음 세그먼트를 서비스명으로 인식")
    void sharedDatabaseAccess_servicesWrapper_detected() {
        Node orderFile = fileNodeWithPath("OrderRepository",
                "services/order-service/src/main/java/OrderRepository.java");
        Node paymentFile = fileNodeWithPath("OrderLookup",
                "services/payment-service/src/main/java/OrderLookup.java");
        Node ordersTable = Node.create(graphId, NodeType.DB_TABLE, "orders", "OrderRepository.java", "java");
        Edge writeEdge = dbAccessEdge(orderFile.getId(), ordersTable.getId(), EdgeType.DB_WRITE);
        Edge readEdge = dbAccessEdge(paymentFile.getId(), ordersTable.getId(), EdgeType.DB_READ);

        List<Map<String, Object>> warnings = service.detect(
                List.of(orderFile, paymentFile, ordersTable), List.of(writeEdge, readEdge));

        assertThat(warnings.stream().filter(w -> "SHARED_DATABASE_ACCESS".equals(w.get("type"))).toList()).hasSize(1);
    }

    @Test
    @DisplayName("SHARED_DATABASE_ACCESS — GatePolicy 선택과 무관하게 항상 적용(독립 축)")
    void sharedDatabaseAccess_appliesRegardlessOfGatePolicy() {
        Node customersFile = fileNodeWithPath("OwnerRepository",
                "spring-petclinic-customers-service/src/main/java/OwnerRepository.java");
        Node visitsFile = fileNodeWithPath("OwnerCache",
                "spring-petclinic-visits-service/src/main/java/OwnerCache.java");
        Node ownersTable = Node.create(graphId, NodeType.DB_TABLE, "owners", "OwnerRepository.java", "java");
        Edge writeEdge = dbAccessEdge(customersFile.getId(), ownersTable.getId(), EdgeType.DB_WRITE);
        Edge readEdge = dbAccessEdge(visitsFile.getId(), ownersTable.getId(), EdgeType.DB_READ);
        List<Node> nodes = List.of(customersFile, visitsFile, ownersTable);
        List<Edge> edges = List.of(writeEdge, readEdge);

        List<Map<String, Object>> withDdd = service.detect(nodes, edges, null, GatePolicy.DDD);
        List<Map<String, Object>> withLayered = service.detect(nodes, edges, null, GatePolicy.LAYERED);

        assertThat(withDdd).anySatisfy(w -> assertThat(w.get("type")).isEqualTo("SHARED_DATABASE_ACCESS"));
        assertThat(withLayered).anySatisfy(w -> assertThat(w.get("type")).isEqualTo("SHARED_DATABASE_ACCESS"));
    }

    @Test
    @DisplayName("detectActiveTheme — 서비스 2개가 DB 접근하면 msaActive=true, msaRuleTypes에 SHARED_DATABASE_ACCESS 포함")
    void detectActiveTheme_multiServiceDbAccess_msaActive() {
        Node customersFile = fileNodeWithPath("OwnerRepository",
                "spring-petclinic-customers-service/src/main/java/OwnerRepository.java");
        Node visitsFile = fileNodeWithPath("OwnerCache",
                "spring-petclinic-visits-service/src/main/java/OwnerCache.java");
        Node ownersTable = Node.create(graphId, NodeType.DB_TABLE, "owners", "OwnerRepository.java", "java");
        Edge writeEdge = dbAccessEdge(customersFile.getId(), ownersTable.getId(), EdgeType.DB_WRITE);
        Edge readEdge = dbAccessEdge(visitsFile.getId(), ownersTable.getId(), EdgeType.DB_READ);

        var theme = service.detectActiveTheme(
                List.of(customersFile, visitsFile, ownersTable), List.of(writeEdge, readEdge), GatePolicy.AUTO);

        assertThat(theme.msaActive()).isTrue();
        assertThat(theme.msaRuleTypes()).contains("SHARED_DATABASE_ACCESS");
    }

    @Test
    @DisplayName("detectActiveTheme — 단일 서비스 프로젝트는 msaActive=false")
    void detectActiveTheme_singleService_msaInactive() {
        List<Node> nodes = List.of(fileNodeWithPath("A", "src/domain/user/User.java"));

        var theme = service.detectActiveTheme(nodes, List.of(), GatePolicy.AUTO);

        assertThat(theme.msaActive()).isFalse();
    }

    // ===== SERVICE_CALL_CHAIN (모노레포 MSA 서비스 간 동기 호출 체인, 2026-07-17) =====

    private Edge serviceCallEdge(UUID src, UUID tgt) {
        return Edge.create(graphId, src + "->svccall->" + tgt, EdgeType.SERVICE_CALL, src, tgt);
    }

    @Test
    @DisplayName("SERVICE_CALL_CHAIN — 서비스 3개(2홉) 연쇄 호출이면 발화")
    void serviceCallChain_threeServices_detected() {
        Node gateway = fileNodeWithPath("ApiGateway", "api-gateway/src/ApiGateway.java");
        Node customers = fileNodeWithPath("CustomersController", "customers-service/src/CustomersController.java");
        Node visits = fileNodeWithPath("VisitsController", "visits-service/src/VisitsController.java");
        Edge gatewayToCustomers = serviceCallEdge(gateway.getId(), customers.getId());
        Edge customersToVisits = serviceCallEdge(customers.getId(), visits.getId());

        List<Map<String, Object>> warnings = service.detect(
                List.of(gateway, customers, visits), List.of(gatewayToCustomers, customersToVisits));

        List<Map<String, Object>> found = warnings.stream()
                .filter(w -> "SERVICE_CALL_CHAIN".equals(w.get("type"))).toList();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).get("severity")).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("precision: 서비스 2개(1홉)만 호출하면 SERVICE_CALL_CHAIN 미발화 (임계 미달)")
    void serviceCallChain_singleHop_notFlagged() {
        Node gateway = fileNodeWithPath("ApiGateway", "api-gateway/src/ApiGateway.java");
        Node customers = fileNodeWithPath("CustomersController", "customers-service/src/CustomersController.java");
        Edge gatewayToCustomers = serviceCallEdge(gateway.getId(), customers.getId());

        List<Map<String, Object>> warnings = service.detect(
                List.of(gateway, customers), List.of(gatewayToCustomers));

        assertThat(warnings.stream().filter(w -> "SERVICE_CALL_CHAIN".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("precision: SERVICE_CALL 엣지가 없으면 SERVICE_CALL_CHAIN 미발화")
    void serviceCallChain_noEdges_notFlagged() {
        List<Node> nodes = List.of(fileNodeWithPath("A", "src/domain/user/User.java"));

        List<Map<String, Object>> warnings = service.detect(nodes, List.of());

        assertThat(warnings.stream().filter(w -> "SERVICE_CALL_CHAIN".equals(w.get("type"))).toList()).isEmpty();
    }

    @Test
    @DisplayName("detectActiveTheme — SERVICE_CALL 엣지만 있어도(shared DB 없이) msaActive=true")
    void detectActiveTheme_serviceCallOnly_msaActive() {
        Node gateway = fileNodeWithPath("ApiGateway", "api-gateway/src/ApiGateway.java");
        Node customers = fileNodeWithPath("CustomersController", "customers-service/src/CustomersController.java");
        Edge gatewayToCustomers = serviceCallEdge(gateway.getId(), customers.getId());

        var theme = service.detectActiveTheme(
                List.of(gateway, customers), List.of(gatewayToCustomers), GatePolicy.AUTO);

        assertThat(theme.msaActive()).isTrue();
        assertThat(theme.msaRuleTypes()).contains("SERVICE_CALL_CHAIN");
    }
}
