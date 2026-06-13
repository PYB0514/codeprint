// GraphBuilder 회귀 테스트 — DECISIONS_ANALYSIS.md에 기록된 버그 재발 방지
package com.codeprint.infrastructure.analysis;

import com.codeprint.domain.graph.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphBuilderTest {

    @Mock
    private GraphRepository graphRepository;

    private GraphBuilder graphBuilder;

    private final UUID projectId = UUID.randomUUID();
    private final UUID analysisId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        graphBuilder = new GraphBuilder(graphRepository);
        // Graph.create() → graphRepository.save() 가 Graph 객체를 반환해야 build()가 동작
        when(graphRepository.save(any(Graph.class))).thenAnswer(inv -> inv.getArgument(0));
        when(graphRepository.saveNode(any(Node.class))).thenAnswer(inv -> inv.getArgument(0));
        when(graphRepository.saveEdge(any(Edge.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── 회귀: CONTAINS 엣지 중복 방지 ──────────────────────────────────────

    @Test
    @DisplayName("같은 파일의 같은 함수에 CONTAINS 엣지가 중복 생성되지 않는다")
    void CONTAINS_엣지_중복_방지() {
        // DECISIONS_ANALYSIS.md: usedContainsEdgeIds Set 누락으로 CONTAINS 엣지가 중복 생성됐던 버그
        ParsedFile file = parsedFile("src/UserService.java", "Java",
                List.of("createUser", "createUser"), // 같은 함수명 중복 입력 (방어 케이스)
                Map.of("createUser", "유저 생성"));

        graphBuilder.build(projectId, analysisId, List.of(file));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        long containsCount = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.CONTAINS)
                .filter(e -> e.getEdgeIdentifier().contains("createUser"))
                .count();

        assertThat(containsCount).isEqualTo(1);
    }

    // ── 파일 간 FUNCTION_CALL 엣지 생성 ────────────────────────────────────

    @Test
    @DisplayName("한 파일의 함수가 다른 파일의 함수를 호출하면 FUNCTION_CALL 엣지가 생성된다")
    void 파일_간_FUNCTION_CALL_엣지_생성() {
        ParsedFile controller = parsedFileWithCalls("src/AnalysisController.java", "Java",
                List.of("startAnalysis"),
                Map.of("startAnalysis", List.of("start")));

        ParsedFile service = parsedFile("src/AnalysisService.java", "Java",
                List.of("start"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(controller, service));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasFunctionCall = edgeCaptor.getAllValues().stream()
                .anyMatch(e -> e.getType() == EdgeType.FUNCTION_CALL);

        assertThat(hasFunctionCall).isTrue();
    }

    @Test
    @DisplayName("같은 파일 내 함수 호출은 FUNCTION_CALL 엣지를 생성하지 않는다")
    void 같은_파일_내_호출은_엣지_미생성() {
        // callerFile.filePath().equals(calleeFile.filePath()) 이면 skip
        ParsedFile file = parsedFileWithCalls("src/UserService.java", "Java",
                List.of("createUser", "validate"),
                Map.of("createUser", List.of("validate")));

        graphBuilder.build(projectId, analysisId, List.of(file));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        long functionCallCount = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .count();

        assertThat(functionCallCount).isEqualTo(0);
    }

    // ── IMPORT 엣지 타입 확인 ───────────────────────────────────────────────

    @Test
    @DisplayName("파일 간 import 관계는 IMPORT 타입 엣지로 생성된다")
    void import_관계_IMPORT_타입_엣지() {
        // isImportMatch: "com.example.UserService" → "com/example/UserService" 로 변환 후 파일 경로 suffix 매칭
        ParsedFile importer = parsedFileWithImports("src/com/example/UserController.java", "Java",
                List.of("com.example.UserService"));
        ParsedFile importee = parsedFile("src/com/example/UserService.java", "Java",
                List.of("createUser"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(importer, importee));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasImport = edgeCaptor.getAllValues().stream()
                .anyMatch(e -> e.getType() == EdgeType.IMPORT);
        boolean hasNoApiCallForImport = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.API_CALL)
                .noneMatch(e -> e.getEdgeIdentifier().contains("UserService"));

        assertThat(hasImport).isTrue();
        // PR #58 버그: importEdges 필터 누락으로 IMPORT가 API_CALL로 오인됐던 케이스 방어
        assertThat(hasNoApiCallForImport).isTrue();
    }

    // ── 인터페이스 → 구현체 FUNCTION_CALL 엣지 ─────────────────────────────

    @Test
    @DisplayName("인터페이스 하나에 구현체 2개 — 각 구현체마다 isInterfaceImpl 엣지가 생성된다")
    void 여러_구현체_각각_FUNCTION_CALL_엣지_생성() {
        // domain/NoticeRepository.java (인터페이스)
        ParsedFile ifaceFile = parsedFile("src/domain/NoticeRepository.java", "Java",
                List.of("save"), Map.of());

        // NoticeRepositoryImpl (첫 번째 구현체)
        ParsedFile impl1 = new ParsedFile(
                "src/infra/NoticeRepositoryImpl.java", "Java",
                List.of("save"), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(),
                List.of("NoticeRepository"), List.of(), List.of(), List.of()
        );

        // CachedNoticeRepositoryImpl (두 번째 구현체)
        ParsedFile impl2 = new ParsedFile(
                "src/infra/CachedNoticeRepositoryImpl.java", "Java",
                List.of("save"), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(),
                List.of("NoticeRepository"), List.of(), List.of(), List.of()
        );

        graphBuilder.build(projectId, analysisId, List.of(ifaceFile, impl1, impl2));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        long implEdgeCount = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .filter(e -> e.getMetadata() != null
                        && Boolean.TRUE.equals(e.getMetadata().get("isInterfaceImpl")))
                .count();

        // 구현체 2개 × save 1개 = isInterfaceImpl 엣지 2개
        assertThat(implEdgeCount).isEqualTo(2);
    }

    @Test
    @DisplayName("인터페이스 메서드 노드와 구현체 메서드 노드 사이에 isInterfaceImpl FUNCTION_CALL 엣지가 생성된다")
    void 인터페이스_구현체_FUNCTION_CALL_엣지_생성() {
        // GraphRepository (인터페이스) → GraphRepositoryImpl (구현체)
        ParsedFile ifaceFile = parsedFile("src/domain/graph/GraphRepository.java", "Java",
                List.of("save", "findById"), Map.of());
        ParsedFile implFile = new ParsedFile(
                "src/infrastructure/graph/GraphRepositoryImpl.java", "Java",
                List.of("save", "findById"), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(),
                List.of("GraphRepository"), // implementedInterfaces
                List.of(), // asyncMethods
                List.of(), // jsxComponents
                List.of()  // rawSqlAccesses
        );

        graphBuilder.build(projectId, analysisId, List.of(ifaceFile, implFile));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasInterfaceImplEdge = edgeCaptor.getAllValues().stream()
                .anyMatch(e -> e.getType() == EdgeType.FUNCTION_CALL
                        && e.getMetadata() != null
                        && Boolean.TRUE.equals(e.getMetadata().get("isInterfaceImpl")));

        assertThat(hasInterfaceImplEdge).isTrue();
    }

    // ── FILE/FUNCTION 노드 기본 생성 ────────────────────────────────────────

    @Test
    @DisplayName("parsedFiles 수만큼 FILE 노드가 생성된다")
    void FILE_노드_수_검증() {
        List<ParsedFile> files = List.of(
                parsedFile("src/A.java", "Java", List.of("methodA"), Map.of()),
                parsedFile("src/B.java", "Java", List.of("methodB"), Map.of()),
                parsedFile("src/C.java", "Java", List.of(), Map.of())
        );

        graphBuilder.build(projectId, analysisId, files);

        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(graphRepository, atLeastOnce()).saveNode(nodeCaptor.capture());

        long fileNodeCount = nodeCaptor.getAllValues().stream()
                .filter(n -> n.getType() == NodeType.FILE)
                .count();

        assertThat(fileNodeCount).isEqualTo(3);
    }

    // ── 인터페이스 → 구현체 우선 매핑 ─────────────────────────────────────────

    @Test
    @DisplayName("FUNCTION_CALL 매칭 시 인터페이스보다 구현체를 우선 선택한다")
    void FUNCTION_CALL_구현체_우선_선택() {
        // ServiceA.doWork() → save() 호출, 후보: Repository(인터페이스) vs RepositoryImpl(구현체)
        // 인터페이스가 파일 목록에 먼저 오더라도 구현체로 연결되어야 한다
        ParsedFile service = parsedFileWithCalls("src/ServiceA.java", "Java",
                List.of("doWork"), Map.of("doWork", List.of("save")));
        ParsedFile repo = parsedFile("src/Repository.java", "Java",
                List.of("save"), Map.of()); // 인터페이스 (구현체가 implements Repository)
        ParsedFile repoImpl = parsedFileWithImpl("src/RepositoryImpl.java", "Java",
                List.of("save"), "Repository"); // 구현체

        // 인터페이스(repo)가 먼저 오는 순서로 입력
        graphBuilder.build(projectId, analysisId, List.of(service, repo, repoImpl));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        List<Edge> callEdges = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .collect(Collectors.toList());

        // doWork → save FUNCTION_CALL 엣지는 RepositoryImpl의 save를 target으로 해야 한다
        boolean hasDirectImplEdge = callEdges.stream()
                .filter(e -> e.getEdgeIdentifier().contains("doWork") && e.getEdgeIdentifier().contains("save"))
                .anyMatch(e -> {
                    Object calleeFile = e.getMetadata() != null ? e.getMetadata().get("calleeFile") : null;
                    return calleeFile != null && calleeFile.toString().contains("RepositoryImpl");
                });

        assertThat(hasDirectImplEdge).isTrue();
    }

    @Test
    @DisplayName("구현체가 없는 인터페이스 메서드 호출은 기존대로 인터페이스로 연결된다")
    void FUNCTION_CALL_구현체_없으면_인터페이스_그대로() {
        // 구현체가 없는 경우: 인터페이스로 연결
        ParsedFile service = parsedFileWithCalls("src/ServiceA.java", "Java",
                List.of("doWork"), Map.of("doWork", List.of("query")));
        ParsedFile repo = parsedFile("src/ExternalClient.java", "Java",
                List.of("query"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(service, repo));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasCallEdge = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .anyMatch(e -> e.getEdgeIdentifier().contains("doWork") && e.getEdgeIdentifier().contains("query"));

        assertThat(hasCallEdge).isTrue();
    }

    // ── 파일 수 카운트 기록 (대형 레포 절단 안내) ───────────────────────────

    @Test
    @DisplayName("전체 대상 파일 수를 그래프에 기록한다 — 절단 시 totalFileCount > analyzedFileCount")
    void 절단_파일_수_기록() {
        ParsedFile file = parsedFile("src/A.java", "Java", List.of("doWork"), Map.of());

        Graph graph = graphBuilder.build(projectId, analysisId, List.of(file), 712);

        assertThat(graph.getAnalyzedFileCount()).isEqualTo(1);
        assertThat(graph.getTotalFileCount()).isEqualTo(712);
    }

    @Test
    @DisplayName("3-인자 build는 전체 대상 수 = 분석 파일 수로 기록한다 (절단 없음)")
    void 기본_build_카운트_일치() {
        ParsedFile file = parsedFile("src/A.java", "Java", List.of("doWork"), Map.of());

        Graph graph = graphBuilder.build(projectId, analysisId, List.of(file));

        assertThat(graph.getAnalyzedFileCount()).isEqualTo(1);
        assertThat(graph.getTotalFileCount()).isEqualTo(1);
    }

    // ── B-8: edgeIdentifier callee 파일 포함 (dedup 정확도) ─────────────────

    @Test
    @DisplayName("FUNCTION_CALL edgeIdentifier에 callee 파일명이 포함된다 — 동명 함수 dedup 방지")
    void FUNCTION_CALL_edgeIdentifier_callee_파일명_포함() {
        // B-8 버그: callee 파일명 없이 "callerFile-callerFunc-calls-calleeFunc" 형태라서
        // 동명 함수가 다른 파일에 있을 때 잘못된 dedup이 발생했던 문제 방지
        ParsedFile caller = parsedFileWithCalls("src/ControllerA.java", "Java",
                List.of("doWork"), Map.of("doWork", List.of("save")));
        ParsedFile callee = parsedFile("src/ServiceB.java", "Java", List.of("save"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, callee));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean identifierContainsCalleeFile = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .filter(e -> e.getEdgeIdentifier().contains("doWork") && e.getEdgeIdentifier().contains("save"))
                .anyMatch(e -> e.getEdgeIdentifier().contains("ServiceB"));

        assertThat(identifierContainsCalleeFile).isTrue();
    }

    // ── API_CALL 엣지 생성 (비Spring 백엔드 매핑) ───────────────────────────

    @Test
    @DisplayName("Express 스타일 METHOD:/path 매핑도 프론트 호출과 API_CALL 엣지로 연결된다")
    void Express_매핑_API_CALL_엣지_생성() {
        // 회귀: 비Spring 매핑은 "GET:/users" 형식인데 글로브 매칭이 "GET:"을 경로 세그먼트로 비교해 항상 실패했던 버그
        ParsedFile front = parsedFileWithApiCalls("src/api.ts", "TypeScript", List.of("GET:/users"));
        ParsedFile backend = parsedFileWithMappings("src/userRouter.js", "JavaScript", List.of("GET:/users"));

        graphBuilder.build(projectId, analysisId, List.of(front, backend));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasApiCall = edgeCaptor.getAllValues().stream()
                .anyMatch(e -> e.getType() == EdgeType.API_CALL);

        assertThat(hasApiCall).isTrue();
    }

    @Test
    @DisplayName("Express :param 경로 세그먼트가 글로브로 정규화되어 매칭된다")
    void Express_param_세그먼트_매칭() {
        // 프론트 템플릿 리터럴 ${id}는 extractApiCalls에서 이미 * 로 정규화됨
        ParsedFile front = parsedFileWithApiCalls("src/api.ts", "TypeScript", List.of("DELETE:/users/*"));
        ParsedFile backend = parsedFileWithMappings("src/userRouter.js", "JavaScript", List.of("DELETE:/users/:id"));

        graphBuilder.build(projectId, analysisId, List.of(front, backend));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasApiCall = edgeCaptor.getAllValues().stream()
                .anyMatch(e -> e.getType() == EdgeType.API_CALL);

        assertThat(hasApiCall).isTrue();
    }

    @Test
    @DisplayName("Spring 프리픽스 없는 매핑은 기존대로 매칭된다 (회귀 방지)")
    void Spring_매핑_기존_동작_유지() {
        ParsedFile front = parsedFileWithApiCalls("src/api.ts", "TypeScript", List.of("GET:/api/projects"));
        ParsedFile backend = parsedFileWithMappings("src/ProjectController.java", "Java", List.of("/api/projects"));

        graphBuilder.build(projectId, analysisId, List.of(front, backend));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasApiCall = edgeCaptor.getAllValues().stream()
                .anyMatch(e -> e.getType() == EdgeType.API_CALL);

        assertThat(hasApiCall).isTrue();
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private ParsedFile parsedFile(String path, String lang, List<String> functions, Map<String, String> comments) {
        return new ParsedFile(path, lang, functions, List.of(), null, comments,
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private ParsedFile parsedFileWithCalls(String path, String lang, List<String> functions,
                                           Map<String, List<String>> calls) {
        return new ParsedFile(path, lang, functions, List.of(), null, Map.of(),
                calls, List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private ParsedFile parsedFileWithImports(String path, String lang, List<String> imports) {
        return new ParsedFile(path, lang, List.of(), imports, null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    // 구현체 파일 생성 헬퍼 — implementedInterfaces 포함
    private ParsedFile parsedFileWithImpl(String path, String lang, List<String> functions, String implementedInterface) {
        return new ParsedFile(path, lang, functions, List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(implementedInterface), List.of(), List.of(), List.of());
    }

    // 프론트 API 호출 파일 생성 헬퍼 — apiCalls 포함
    private ParsedFile parsedFileWithApiCalls(String path, String lang, List<String> apiCalls) {
        return new ParsedFile(path, lang, List.of(), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), apiCalls, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    // 백엔드 컨트롤러 매핑 파일 생성 헬퍼 — controllerMappings 포함
    private ParsedFile parsedFileWithMappings(String path, String lang, List<String> mappings) {
        return new ParsedFile(path, lang, List.of(), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), mappings, List.of(), List.of(), List.of(), List.of());
    }
}
