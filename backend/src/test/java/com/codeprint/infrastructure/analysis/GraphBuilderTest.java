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
                List.of("NoticeRepository")
        );

        // CachedNoticeRepositoryImpl (두 번째 구현체)
        ParsedFile impl2 = new ParsedFile(
                "src/infra/CachedNoticeRepositoryImpl.java", "Java",
                List.of("save"), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(),
                List.of("NoticeRepository")
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
                List.of("GraphRepository") // implementedInterfaces
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

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private ParsedFile parsedFile(String path, String lang, List<String> functions, Map<String, String> comments) {
        return new ParsedFile(path, lang, functions, List.of(), null, comments,
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of());
    }

    private ParsedFile parsedFileWithCalls(String path, String lang, List<String> functions,
                                           Map<String, List<String>> calls) {
        return new ParsedFile(path, lang, functions, List.of(), null, Map.of(),
                calls, List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of());
    }

    private ParsedFile parsedFileWithImports(String path, String lang, List<String> imports) {
        return new ParsedFile(path, lang, List.of(), imports, null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of());
    }
}
