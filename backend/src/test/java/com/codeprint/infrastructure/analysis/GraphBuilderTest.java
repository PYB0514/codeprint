// GraphBuilder 회귀 테스트 — DECISIONS_ANALYSIS.md에 기록된 버그 재발 방지
package com.codeprint.infrastructure.analysis;

import com.codeprint.application.graph.GraphWarningService;
import com.codeprint.domain.graph.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
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
    @DisplayName("같은 파일 내 함수 호출은 sameFile 마커 FUNCTION_CALL 엣지를 생성한다 (B-13: DEAD_CODE 오탐·ASYNC_SELF_CALL no-op 동시 해소)")
    void 같은_파일_내_호출은_sameFile_엣지_생성() {
        // B-13: 같은 파일 호출에 엣지가 없어 createUser만 호출하는 validate가 DEAD_CODE 오탐, @Async 자기호출 미감지
        ParsedFile file = parsedFileWithCalls("src/UserService.java", "Java",
                List.of("createUser", "validate"),
                Map.of("createUser", List.of("validate")));

        graphBuilder.build(projectId, analysisId, List.of(file));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        List<Edge> sameFileEdges = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .filter(e -> e.getMetadata() != null && Boolean.TRUE.equals(e.getMetadata().get("sameFile")))
                .collect(Collectors.toList());

        // createUser → validate (같은 파일) sameFile 엣지 1개 생성
        assertThat(sameFileEdges).hasSize(1);
        Edge edge = sameFileEdges.get(0);
        assertThat(edge.getMetadata().get("callerFile")).isEqualTo("src/UserService.java");
        assertThat(edge.getMetadata().get("calleeFile")).isEqualTo("src/UserService.java");
    }

    @Test
    @DisplayName("같은 파일 내 자기 자신 재귀 호출은 sameFile 엣지를 만들지 않는다 (재귀 전용 함수 DEAD_CODE 부활 방지)")
    void 같은_파일_재귀_자기호출은_엣지_미생성() {
        // recurse() → recurse() (자기 자신) — 외부 호출이 없으면 여전히 DEAD_CODE여야 하므로 자기 루프 엣지 제외
        ParsedFile file = parsedFileWithCalls("src/TreeWalker.java", "Java",
                List.of("recurse"),
                Map.of("recurse", List.of("recurse")));

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

    // 두 파일 사이에 IMPORT 엣지가 있는지 — edgeIdentifier(소스-imports-타깃)로 판별
    private boolean hasImportEdge(ArgumentCaptor<Edge> cap, String srcName, String tgtName) {
        return cap.getAllValues().stream()
                .anyMatch(e -> e.getType() == EdgeType.IMPORT
                        && e.getEdgeIdentifier().equals(srcName + "-imports-" + tgtName));
    }

    @Test
    @DisplayName("TS @/ alias import — @/components/button → src/components/button.tsx 로 해소되어 IMPORT 엣지 생성")
    void tsImport_atAlias_resolved() {
        // BEFORE 버그: @/ 가 패키지 브랜치로 빠져 매칭 0건이었음(모던 TS import의 다수). 분석 루트가 src 미포함이라 'src/' 허용.
        ParsedFile importer = parsedFileWithImports("features/auth/api/login.ts", "TypeScript",
                List.of("@/components/ui/button"));
        ParsedFile importee = parsedFile("components/ui/button.tsx", "TypeScript", List.of("Button"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(importer, importee));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        assertThat(hasImportEdge(cap, "login.ts", "button.tsx")).isTrue();
    }

    @Test
    @DisplayName("TS ../ 상대 import — 소스 디렉터리 기준으로 해소되어 IMPORT 엣지 생성 (BEFORE: ../ 미해소로 엣지 0)")
    void tsImport_parentRelative_resolved() {
        // article.entity 가 ../user/user.entity 를 import — BEFORE는 ../ 를 못 풀어 엣지 누락(→ 진짜 순환 누락)
        ParsedFile importer = parsedFileWithImports("article/article.entity.ts", "TypeScript",
                List.of("../user/user.entity"));
        ParsedFile importee = parsedFile("user/user.entity.ts", "TypeScript", List.of("UserEntity"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(importer, importee));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        assertThat(hasImportEdge(cap, "article.entity.ts", "user.entity.ts")).isTrue();
    }

    @Test
    @DisplayName("TS 부분 세그먼트 오매칭 방지 — ../user/user.entity 가 .../my-user.entity 로 잘못 연결되지 않음")
    void tsImport_segmentBoundary_noPartialMatch() {
        // segmentEndsWith 가 '/'+경로 경계를 요구하므로 user.entity 가 my-user.entity 에 부분 매칭되면 안 된다.
        ParsedFile importer = parsedFileWithImports("article/article.entity.ts", "TypeScript",
                List.of("../user/user.entity"));
        ParsedFile decoy = parsedFile("user/my-user.entity.ts", "TypeScript", List.of("X"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(importer, decoy));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        long importEdges = cap.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.IMPORT).count();
        assertThat(importEdges).isZero();
    }

    @Test
    @DisplayName("Java 패키지 import는 종전대로 매칭 (공유 isImportMatch 무회귀)")
    void javaPackageImport_stillMatches() {
        ParsedFile importer = parsedFileWithImports("src/com/example/UserController.java", "Java",
                List.of("com.example.UserService"));
        ParsedFile importee = parsedFile("src/com/example/UserService.java", "Java", List.of("createUser"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(importer, importee));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        assertThat(hasImportEdge(cap, "UserController.java", "UserService.java")).isTrue();
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
                List.of("NoticeRepository"), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()
        );

        // CachedNoticeRepositoryImpl (두 번째 구현체)
        ParsedFile impl2 = new ParsedFile(
                "src/infra/CachedNoticeRepositoryImpl.java", "Java",
                List.of("save"), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(),
                List.of("NoticeRepository"), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()
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
                List.of(), // rawSqlAccesses
                List.of(), // frameworkAnnotatedMethods
                List.of(), // valueReferencedFunctions
                Map.of()   // functionDefCounts
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

    // ── import 스코프 bare-name 호출 해소 (정확도: phantom cross-file 엣지 제거) ──

    @Test
    @DisplayName("bare-name 호출은 caller가 import한 파일로 해소된다 — import 안 한 동명 함수(decoy)로는 연결되지 않는다")
    void bare_name_호출_import한_파일로_해소() {
        // caller가 RealService를 import. DecoyService도 같은 이름 save를 갖지만 import 안 됨.
        // 입력 순서상 decoy가 먼저라 기존 전역-첫매칭은 decoy를 골랐음 → import 스코프로 real을 선택해야 함
        ParsedFile caller = parsedFileWithCallsAndImports("src/com/app/AppController.java", "Java",
                List.of("handle"), Map.of("handle", List.of("save")),
                List.of("com.app.RealService"));
        ParsedFile decoy = parsedFile("src/com/app/DecoyService.java", "Java", List.of("save"), Map.of());
        ParsedFile real = parsedFile("src/com/app/RealService.java", "Java", List.of("save"), Map.of());

        // decoy를 먼저 넣어 전역-첫매칭이 decoy를 고르도록 유도
        graphBuilder.build(projectId, analysisId, List.of(caller, decoy, real));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        List<Edge> callEdges = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .filter(e -> e.getEdgeIdentifier().contains("handle") && e.getEdgeIdentifier().contains("save"))
                .collect(Collectors.toList());

        assertThat(callEdges).isNotEmpty();
        // 해소 대상은 import한 RealService 여야 하고, import 안 한 DecoyService는 아니어야 한다
        assertThat(callEdges).allMatch(e ->
                e.getMetadata().get("calleeFile").toString().contains("RealService"));
        assertThat(callEdges).noneMatch(e ->
                e.getMetadata().get("calleeFile").toString().contains("DecoyService"));
    }

    @Test
    @DisplayName("import된 후보가 하나도 없으면 전역 매칭으로 폴백한다 — import 추출이 약한 언어 recall 보존")
    void import된_후보_없으면_전역_폴백() {
        // caller가 아무것도 import 안 함(또는 무관한 import) — 그래도 동명 함수가 있으면 엣지 생성돼야 함
        ParsedFile caller = parsedFileWithCallsAndImports("src/Caller.java", "Java",
                List.of("handle"), Map.of("handle", List.of("compute")),
                List.of("java.util.List")); // 후보와 무관한 import
        ParsedFile callee = parsedFile("src/Helper.java", "Java", List.of("compute"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, callee));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasCallEdge = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .anyMatch(e -> e.getEdgeIdentifier().contains("handle") && e.getEdgeIdentifier().contains("compute"));

        assertThat(hasCallEdge).isTrue();
    }

    @Test
    @DisplayName("import된 후보 집합 안에서도 인터페이스보다 구현체를 우선 선택한다")
    void import된_집합에서_구현체_우선() {
        // caller가 인터페이스(Repo)와 구현체(RepoImpl)를 모두 import — 구현체로 해소돼야 함
        ParsedFile service = parsedFileWithCallsAndImports("src/app/AppService.java", "Java",
                List.of("doWork"), Map.of("doWork", List.of("save")),
                List.of("domain.Repo", "infra.RepoImpl"));
        ParsedFile repo = parsedFile("src/domain/Repo.java", "Java", List.of("save"), Map.of()); // 인터페이스
        ParsedFile repoImpl = parsedFileWithImpl("src/infra/RepoImpl.java", "Java",
                List.of("save"), "Repo"); // 구현체

        // 인터페이스가 먼저 오도록 입력
        graphBuilder.build(projectId, analysisId, List.of(service, repo, repoImpl));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean resolvesToImpl = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .filter(e -> e.getEdgeIdentifier().contains("doWork") && e.getEdgeIdentifier().contains("save"))
                .anyMatch(e -> e.getMetadata().get("calleeFile").toString().contains("RepoImpl"));

        assertThat(resolvesToImpl).isTrue();
    }

    // ── JDK/컬렉션 내장 메서드명 폴백 phantom 엣지 차단 ──────────────────────

    @Test
    @DisplayName("JDK 내장 메서드명(add) bare 호출은 import 매칭 없으면 전역 폴백을 막아 엣지를 만들지 않는다")
    void JDK_내장_메서드명_폴백_엣지_미생성() {
        // list.add(x) 같은 호출 — 실제 타깃은 JDK List(노드 없음). import 안 한 도메인 파일에 add()가 있어도
        // 거기로 연결하면 phantom. 폴백 차단으로 엣지를 만들지 않아야 한다
        ParsedFile caller = parsedFileWithCallsAndImports("src/app/Service.java", "Java",
                List.of("handle"), Map.of("handle", List.of("add")),
                List.of()); // import 없음
        ParsedFile decoy = parsedFile("src/other/TeamMember.java", "Java", List.of("add"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, decoy));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasPhantomAddEdge = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .anyMatch(e -> e.getEdgeIdentifier().contains("handle") && e.getEdgeIdentifier().contains("add"));

        assertThat(hasPhantomAddEdge).isFalse();
    }

    @Test
    @DisplayName("JDK 내장 메서드명이라도 caller가 해당 파일을 import하면 엣지를 만든다 (import 스코프는 신뢰)")
    void JDK_내장_메서드명도_import하면_엣지_생성() {
        // 도메인 객체에 add()가 있고 caller가 명시적으로 import하면 의도된 호출 — 차단은 폴백에만 적용
        ParsedFile caller = parsedFileWithCallsAndImports("src/app/Service.java", "Java",
                List.of("handle"), Map.of("handle", List.of("add")),
                List.of("domain.Basket"));
        ParsedFile basket = parsedFile("src/domain/Basket.java", "Java", List.of("add"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, basket));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasImportedAddEdge = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .anyMatch(e -> e.getEdgeIdentifier().contains("handle") && e.getEdgeIdentifier().contains("add")
                        && e.getMetadata().get("calleeFile").toString().contains("Basket"));

        assertThat(hasImportedAddEdge).isTrue();
    }

    @Test
    @DisplayName("JDK 내장 메서드명이라도 같은 디렉터리(패키지) 폴백은 보존한다 — Go same-package 호출 recall (gin get 오탐 회피)")
    void JDK_내장_메서드명_같은_디렉터리_폴백_보존() {
        // Go처럼 import 없이 같은 패키지 함수를 bare 호출 — 같은 디렉터리면 실제 호출일 수 있어 보존
        ParsedFile caller = parsedFileWithCallsAndImports("src/gin/context.go", "Go",
                List.of("handle"), Map.of("handle", List.of("get")),
                List.of());
        ParsedFile callee = parsedFile("src/gin/tree.go", "Go", List.of("get"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, callee));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasSameDirEdge = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .anyMatch(e -> e.getEdgeIdentifier().contains("handle") && e.getEdgeIdentifier().contains("get"));

        assertThat(hasSameDirEdge).isTrue();
    }

    @Test
    @DisplayName("JDK 내장이 아닌 일반 함수명은 import 없어도 전역 폴백으로 엣지를 만든다 (recall 보존)")
    void 일반_함수명은_폴백_유지() {
        // computeDigest 같은 도메인 고유명은 차단 대상 아님 — 기존 폴백 동작 유지
        ParsedFile caller = parsedFileWithCallsAndImports("src/app/Service.java", "Java",
                List.of("handle"), Map.of("handle", List.of("computeDigest")),
                List.of());
        ParsedFile callee = parsedFile("src/other/Digest.java", "Java", List.of("computeDigest"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, callee));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasEdge = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .anyMatch(e -> e.getEdgeIdentifier().contains("handle") && e.getEdgeIdentifier().contains("computeDigest"));

        assertThat(hasEdge).isTrue();
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

    // ── 프로덕션 경로 교차검증 (Phase 1 #2: Go 리시버 + 동일 패키지 CYCLIC) ──

    @Test
    @DisplayName("[프로덕션 교차검증] gin 스타일 Go — 실제 StaticCodeAnalyzer→GraphBuilder→GraphWarningService에서 decodeJSON DEAD_CODE 오탐·자기순환 CYCLIC 동시 해소")
    void Go_리시버_프로덕션_경로_교차검증(@TempDir Path tempDir) throws Exception {
        // 타입 전용 리시버 func (jsonBinding) Bind() 가 같은 파일 decodeJSON 호출 + "json" 리터럴이 import로 오인되던 케이스
        StaticCodeAnalyzer analyzer = new StaticCodeAnalyzer();
        Path jsonGo = tempDir.resolve("json.go");
        Files.writeString(jsonGo, """
                package binding

                import (
                    "io"
                    "github.com/x/codec"
                )

                func (jsonBinding) Bind(req *http.Request, obj any) error {
                    return decodeJSON(req.Body, obj)
                }

                func decodeJSON(r io.Reader, obj any) error {
                    contentType := "json"
                    return nil
                }
                """);
        ParsedFile pf = analyzer.analyze(jsonGo, tempDir, "Go");

        graphBuilder.build(projectId, analysisId, List.of(pf));

        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveNode(nodeCaptor.capture());
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        List<Map<String, Object>> warnings = new GraphWarningService()
                .detect(nodeCaptor.getAllValues(), edgeCaptor.getAllValues());

        // decodeJSON: Bind 본문이 스캔돼 같은 파일 호출 엣지 연결 → DEAD_CODE 아님
        boolean decodeJsonDead = warnings.stream()
                .filter(w -> "DEAD_CODE".equals(w.get("type")))
                .anyMatch(w -> ((String) w.getOrDefault("message", "")).contains("decodeJSON"));
        assertThat(decodeJsonDead).isFalse();

        // "json" 리터럴은 import로 추출되지 않음 → 자기 파일 IMPORT 엣지 없음 → 자기순환 CYCLIC 없음
        assertThat(pf.imports()).containsExactlyInAnyOrder("io", "github.com/x/codec");
        boolean anyCyclic = warnings.stream().anyMatch(w -> "CYCLIC_IMPORT".equals(w.get("type")));
        assertThat(anyCyclic).isFalse();
    }

    @Test
    @DisplayName("[프로덕션 교차검증] gin recovery.go 콜백 — 값으로 전달된 함수가 referencedAsValue 메타로 DEAD_CODE 제외 (B-16)")
    void Go_콜백_값참조_프로덕션_경로_교차검증(@TempDir Path tempDir) throws Exception {
        // gin recovery.go: CustomRecoveryWithWriter(out, defaultHandleRecovery) — 값 전달이라 호출 엣지 없음 → DEAD_CODE 오탐
        StaticCodeAnalyzer analyzer = new StaticCodeAnalyzer();
        Path recoveryGo = tempDir.resolve("recovery.go");
        Files.writeString(recoveryGo, """
                package gin

                func Recovery() HandlerFunc {
                    return CustomRecoveryWithWriter(DefaultErrorWriter, defaultHandleRecovery)
                }

                func defaultHandleRecovery(c *Context, _ any) {
                    c.AbortWithStatus(500)
                }
                """);
        ParsedFile pf = analyzer.analyze(recoveryGo, tempDir, "Go");

        graphBuilder.build(projectId, analysisId, List.of(pf));

        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveNode(nodeCaptor.capture());
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        // 분석기가 값 참조를 잡고, 빌더가 노드 메타에 referencedAsValue 플래그를 심는다
        assertThat(pf.valueReferencedFunctions()).contains("defaultHandleRecovery");
        boolean flagged = nodeCaptor.getAllValues().stream()
                .filter(n -> n.getType() == NodeType.FUNCTION && "defaultHandleRecovery".equals(n.getName()))
                .anyMatch(n -> n.getMetadata() != null && Boolean.TRUE.equals(n.getMetadata().get("referencedAsValue")));
        assertThat(flagged).isTrue();

        List<Map<String, Object>> warnings = new GraphWarningService()
                .detect(nodeCaptor.getAllValues(), edgeCaptor.getAllValues());
        boolean recoveryDead = warnings.stream()
                .filter(w -> "DEAD_CODE".equals(w.get("type")))
                .anyMatch(w -> ((String) w.getOrDefault("message", "")).contains("defaultHandleRecovery"));
        assertThat(recoveryDead).isFalse();
    }

    // ── declaredTypes 기반 Type::method 해소 (파일명≠클래스명 언어) ──────────

    @Test
    @DisplayName("파일명≠클래스명일 때 declaredTypes로 Type::method를 해소한다(TS: ArticleService→article.service.ts)")
    void declaredTypes로_파일명_불일치_해소() {
        // caller: article.controller.ts 의 getAll() 이 ArticleService::findAll() 호출
        ParsedFile caller = parsedFileWithCalls("src/article/article.controller.ts", "TypeScript",
                List.of("getAll"), Map.of("getAll", List.of("ArticleService::findAll")));
        // callee: 파일명은 article.service 지만 클래스명은 ArticleService — declaredTypes로만 매칭 가능
        ParsedFile callee = new ParsedFile(
                "src/article/article.service.ts", "TypeScript",
                List.of("findAll"), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(),
                List.of("ArticleService") // declaredTypes
        );

        graphBuilder.build(projectId, analysisId, List.of(caller, callee));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());
        boolean resolved = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .anyMatch(e -> e.getMetadata() != null
                        && "src/article/article.service.ts".equals(e.getMetadata().get("calleeFile")));
        assertThat(resolved).isTrue();
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private ParsedFile parsedFile(String path, String lang, List<String> functions, Map<String, String> comments) {
        return new ParsedFile(path, lang, functions, List.of(), null, comments,
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    private ParsedFile parsedFileWithCalls(String path, String lang, List<String> functions,
                                           Map<String, List<String>> calls) {
        return new ParsedFile(path, lang, functions, List.of(), null, Map.of(),
                calls, List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    // 호출 + import 를 함께 지정하는 헬퍼 — import 스코프 해소 테스트용
    private ParsedFile parsedFileWithCallsAndImports(String path, String lang, List<String> functions,
                                                     Map<String, List<String>> calls, List<String> imports) {
        return new ParsedFile(path, lang, functions, imports, null, Map.of(),
                calls, List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    private ParsedFile parsedFileWithImports(String path, String lang, List<String> imports) {
        return new ParsedFile(path, lang, List.of(), imports, null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    // 구현체 파일 생성 헬퍼 — implementedInterfaces 포함
    private ParsedFile parsedFileWithImpl(String path, String lang, List<String> functions, String implementedInterface) {
        return new ParsedFile(path, lang, functions, List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(implementedInterface), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    // 프론트 API 호출 파일 생성 헬퍼 — apiCalls 포함
    private ParsedFile parsedFileWithApiCalls(String path, String lang, List<String> apiCalls) {
        return new ParsedFile(path, lang, List.of(), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), apiCalls, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    // 백엔드 컨트롤러 매핑 파일 생성 헬퍼 — controllerMappings 포함
    private ParsedFile parsedFileWithMappings(String path, String lang, List<String> mappings) {
        return new ParsedFile(path, lang, List.of(), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), mappings, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }
}
