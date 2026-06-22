// GraphWarningService 단위 테스트 — 순환 의존·인터페이스 체인·비동기 자기호출·DB 레이어 우회 감지 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.ArchitectureIntent;
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
    @DisplayName("Model 정방향 — Service가 Model을 import하는 것은 정상(경고 없음)")
    void layered_serviceImportsModel_noWarning() {
        Node svc = nodeAt("OwnerService", "/app/service/OwnerService.java");
        Node model = nodeAt("OwnerEntity", "/app/model/OwnerEntity.java");

        List<Map<String, Object>> warnings = service.detect(
                List.of(svc, model),
                List.of(importEdgeForPath(svc.getId(), model.getId())));

        assertThat(warnings).noneMatch(w -> String.valueOf(w.get("type")).startsWith("LAYERED_"));
    }
}
