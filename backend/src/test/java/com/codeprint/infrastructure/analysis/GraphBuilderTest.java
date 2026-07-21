// GraphBuilder 회귀 테스트 — DECISIONS_ANALYSIS.md에 기록된 버그 재발 방지
package com.codeprint.infrastructure.analysis;

import com.codeprint.application.graph.GraphWarningService;
import com.codeprint.domain.graph.*;
import com.codeprint.domain.graph.port.SnapshotReferencePort;
import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphBuilderTest {

    @Mock
    private GraphRepository graphRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private SnapshotReferencePort snapshotReferencePort;

    private GraphBuilder graphBuilder;

    private final UUID projectId = UUID.randomUUID();
    private final UUID analysisId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        graphBuilder = new GraphBuilder(graphRepository, projectRepository, snapshotReferencePort);
        // Graph.create() → graphRepository.save() 가 Graph 객체를 반환해야 build()가 동작
        when(graphRepository.save(any(Graph.class))).thenAnswer(inv -> inv.getArgument(0));
        when(graphRepository.saveNode(any(Node.class))).thenAnswer(inv -> inv.getArgument(0));
        when(graphRepository.saveEdge(any(Edge.class))).thenAnswer(inv -> inv.getArgument(0));
        // 개인(비시스템) 계정 프로젝트 기본값 — 보존 정책 회귀 테스트만 이 값을 오버라이드
        when(projectRepository.findById(any())).thenReturn(Optional.empty());
        when(snapshotReferencePort.findReferencedGraphIds(any())).thenReturn(Set.of());
    }

    // ── 회귀: 보존 정책 — 시스템 계정 축소 보존 + 스냅샷 보호(§18.8-④) ──────────

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    // 지정 개수만큼 비고정 Graph를 만들어 이미 저장돼 있던 이전 버전들처럼 반환하도록 스텁
    private List<Graph> existingGraphs(int count) throws Exception {
        List<Graph> graphs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Graph g = Graph.create(projectId, UUID.randomUUID());
            var createdAt = Graph.class.getDeclaredField("createdAt");
            createdAt.setAccessible(true);
            createdAt.set(g, java.time.Instant.ofEpochSecond(i));
            graphs.add(g);
        }
        return graphs;
    }

    @Test
    @DisplayName("시스템(갤러리) 계정 프로젝트는 비고정 2개 초과분을 삭제한다")
    void systemOwnedProjectEvictsBeyondSmallerLimit() throws Exception {
        com.codeprint.domain.project.Project systemProject =
                com.codeprint.domain.project.Project.create(SYSTEM_USER_ID, "https://github.com/a/b", "b", null);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(systemProject));
        List<Graph> existing = existingGraphs(2); // 새로 생성될 그래프까지 합치면 비고정 3개 → MAX_RECENT_SYSTEM(2) 초과
        when(graphRepository.findByProjectId(projectId)).thenAnswer(inv -> {
            List<Graph> all = new ArrayList<>(existing);
            all.add(Graph.create(projectId, analysisId));
            return all;
        });

        var file = parsedFile("A.java", "java", List.of("m"), Map.of());
        graphBuilder.build(projectId, analysisId, List.of(file));

        verify(graphRepository).deleteById(existing.get(0).getId());
        verify(graphRepository, never()).deleteById(existing.get(1).getId());
    }

    @Test
    @DisplayName("스냅샷이 참조 중인 그래프는 시스템 계정 축소 보존에서도 삭제되지 않고, 대신 그다음으로 오래된 것이 삭제된다")
    void systemOwnedProjectProtectsSnapshotReferencedGraph() throws Exception {
        com.codeprint.domain.project.Project systemProject =
                com.codeprint.domain.project.Project.create(SYSTEM_USER_ID, "https://github.com/a/b", "b", null);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(systemProject));
        List<Graph> existing = existingGraphs(3); // 새 그래프까지 비고정 4개, 그중 가장 오래된 1개를 보호
        when(graphRepository.findByProjectId(projectId)).thenAnswer(inv -> {
            List<Graph> all = new ArrayList<>(existing);
            all.add(Graph.create(projectId, analysisId));
            return all;
        });
        when(snapshotReferencePort.findReferencedGraphIds(projectId)).thenReturn(Set.of(existing.get(0).getId()));

        var file = parsedFile("A.java", "java", List.of("m"), Map.of());
        graphBuilder.build(projectId, analysisId, List.of(file));

        verify(graphRepository, never()).deleteById(existing.get(0).getId());
        verify(graphRepository).deleteById(existing.get(1).getId());
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

    @Test
    @DisplayName("자기 파일에 동명 정의가 있으면 cross-file 동명 함수로는 phantom 엣지를 만들지 않는다 (엣지 정확도 패턴 A, Java)")
    void 자기정의_있으면_cross파일_동명함수_phantom_엣지_미생성() {
        // DECISIONS_ANALYSIS.md 패턴 A: McpRpcController.handleToolCall→toJson처럼 자기 파일에 이미
        // toJson이 있는데, bare 호출 해소가 전역 폴백으로 무관한 다른 파일의 동명 toJson에도 엣지를 만들던 phantom
        ParsedFile caller = parsedFileWithCalls("src/McpRpcController.java", "Java",
                List.of("handleToolCall", "toJson"),
                Map.of("handleToolCall", List.of("toJson")));
        ParsedFile decoy = parsedFile("src/ArchitectureIntentService.java", "Java", List.of("toJson"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, decoy));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        List<Edge> toJsonCallEdges = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .filter(e -> e.getEdgeIdentifier().contains("toJson"))
                .toList();

        // sameFile 마커 엣지 1개만 있어야 함 — ArchitectureIntentService로 가는 cross-file 엣지는 없어야 함
        assertThat(toJsonCallEdges).hasSize(1);
        assertThat(toJsonCallEdges.get(0).getMetadata().get("sameFile")).isEqualTo(true);
        assertThat(toJsonCallEdges.get(0).getMetadata().get("calleeFile")).isEqualTo("src/McpRpcController.java");
    }

    @Test
    @DisplayName("Java 외 언어는 자기 정의가 있어도 cross-file 동명 함수 해소를 그대로 유지한다 (패턴 A 수정은 Java 한정)")
    void 비Java_언어는_자기정의_있어도_기존_동작_유지() {
        // 언어별 감사(DECISIONS_ANALYSIS.md 2차)에서 비Java는 selfcall도 오귀속될 수 있는 복합형이라
        // 패턴 A 배제를 Java에만 적용하기로 함 — 다른 언어는 기존 폴백 그대로 유지돼야 한다
        ParsedFile caller = parsedFileWithCalls("src/svc/context.go", "Go",
                List.of("handle", "process"),
                Map.of("handle", List.of("process")));
        ParsedFile other = parsedFile("src/other/helper.go", "Go", List.of("process"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, other));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasCrossFileEdge = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .anyMatch(e -> "src/other/helper.go".equals(e.getMetadata().get("calleeFile")));

        assertThat(hasCrossFileEdge).isTrue();
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
    @DisplayName("TS bare npm 패키지 import가 동명 로컬 파일로 자기매칭되지 않음 — IMPORT 자기순환 phantom 방지 (bulletproof-react zustand.ts 발견)")
    void tsImport_bareNpmPackage_noSelfMatch() {
        // __mocks__/zustand.ts 가 `import * as zustand from 'zustand'`(npm 패키지, 상대/alias 아님) →
        // bare 세그먼트 매칭이 파일 자신의 stripped 경로(".../zustand")와 접미사 일치해 자기참조 IMPORT 생성 →
        // CYCLIC_IMPORT 1노드 자가순환 phantom(2026-07-01 bulletproof-react 측정으로 발견).
        // 함수 1개를 동반해 CONTAINS 엣지가 발생하도록 함(단일 파일+import뿐이면 saveEdge 미호출로 stub 검증 불가)
        ParsedFile mock = parsedFileWithCallsAndImports("apps/react-vite/__mocks__/zustand.ts", "TypeScript",
                List.of("create"), Map.of(), List.of("zustand"));

        graphBuilder.build(projectId, analysisId, List.of(mock));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        long importEdges = cap.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.IMPORT).count();
        assertThat(importEdges).isZero();
    }

    @Test
    @DisplayName("TS bare npm 패키지 import가 동명의 '다른' 로컬 파일로도 교차매칭되지 않음 — 슬래시 없는 bare는 baseUrl 매칭 제외")
    void tsImport_bareNpmPackage_noCrossFileMatch() {
        // bulletproof-react 실측: __mocks__/zustand.ts 가 3개 앱(nextjs-app·nextjs-pages·react-vite)에 각각
        // 존재 — 자기매칭 차단만으로는 서로 다른 zustand.ts끼리 교차 매칭돼 CYCLIC_IMPORT(zustand.ts→zustand.ts,
        // 서로 다른 파일)가 남았음. 슬래시 없는 단일 세그먼트는 baseUrl 매칭에서 완전히 제외해야 근본 해결.
        ParsedFile mockA = parsedFileWithCallsAndImports("apps/react-vite/__mocks__/zustand.ts", "TypeScript",
                List.of("create"), Map.of(), List.of("zustand"));
        ParsedFile mockB = parsedFileWithCallsAndImports("apps/nextjs-app/__mocks__/zustand.ts", "TypeScript",
                List.of("create"), Map.of(), List.of("zustand"));

        graphBuilder.build(projectId, analysisId, List.of(mockA, mockB));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        long importEdges = cap.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.IMPORT).count();
        assertThat(importEdges).isZero();
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

    @Test
    @DisplayName("TS baseUrl 디렉터리(barrel) import — 'entities/task' → entities/task/index.ts 로 해소 (BEFORE: 미해소로 엣지 0)")
    void tsImport_baseUrlBarrelDirectory_resolved() {
        // FSD·bulletproof는 각 슬라이스를 index.ts(public API)로 노출하고 'entities/task' 처럼 디렉터리로 import.
        // BEFORE: ./ ../ @/ 아닌 bare baseUrl 은 패키지 브랜치 raw endsWith 라 디렉터리→index 폴백이 없어 엣지 누락.
        ParsedFile importer = parsedFileWithImports("features/toggle-task/ui.tsx", "TypeScript",
                List.of("entities/task"));
        ParsedFile importee = parsedFile("entities/task/index.ts", "TypeScript", List.of("taskModel"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(importer, importee));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        assertThat(hasImportEdge(cap, "ui.tsx", "index.ts")).isTrue();
    }

    @Test
    @DisplayName("비JPA ORM 접근(Django Entity.objects) — 호출 파일에서 엔티티 테이블로 DB_WRITE/DB_READ 엣지 생성")
    void ormDbAccess_createsEdgeToEntityTable() {
        // 모델 정의(Article) → DB_TABLE 노드, 별도 뷰 파일의 Article.objects.create/filter → 코드→테이블 엣지
        ParsedFile model = parsedFileWithDb("articles/models.py", "Python",
                List.of(new DbTableInfo("article", "Article")), List.of());
        ParsedFile view = parsedFileWithDb("articles/views.py", "Python",
                List.of(), List.of(new DbAccess("Article", true), new DbAccess("Article", false)));

        graphBuilder.build(projectId, analysisId, List.of(model, view));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        boolean hasWrite = cap.getAllValues().stream().anyMatch(e -> e.getType() == EdgeType.DB_WRITE);
        boolean hasRead = cap.getAllValues().stream().anyMatch(e -> e.getType() == EdgeType.DB_READ);
        assertThat(hasWrite).isTrue();
        assertThat(hasRead).isTrue();
    }

    @Test
    @DisplayName("SQLAlchemy 접근(Entity.query) — 모델 테이블로 DB_READ 엣지 생성")
    void sqlAlchemyDbAccess_createsReadEdgeToEntityTable() {
        // 모델 정의(User) → DB_TABLE 노드, 별도 뷰 파일의 User.query → 코드→테이블 READ 엣지
        ParsedFile model = parsedFileWithDb("conduit/user/models.py", "Python",
                List.of(new DbTableInfo("users", "User")), List.of());
        ParsedFile view = parsedFileWithDb("conduit/user/views.py", "Python",
                List.of(), List.of(new DbAccess("User", false)));

        graphBuilder.build(projectId, analysisId, List.of(model, view));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        boolean hasRead = cap.getAllValues().stream().anyMatch(e -> e.getType() == EdgeType.DB_READ);
        boolean hasWrite = cap.getAllValues().stream().anyMatch(e -> e.getType() == EdgeType.DB_WRITE);
        assertThat(hasRead).isTrue();
        // SQLAlchemy 읽기 전용 — WRITE 엣지 없음
        assertThat(hasWrite).isFalse();
    }

    @Test
    @DisplayName("TypeORM 접근 — @Entity 테이블로 DB_READ/DB_WRITE 엣지 생성(클래스명 키 매칭)")
    void typeOrmDbAccess_createsReadWriteEdgesToEntityTable() {
        // @Entity 모델(ArticleEntity, 테이블 article) → DB_TABLE 노드(className 키=ArticleEntity),
        // 서비스 파일의 this.articleRepository.findOne/save → 코드→테이블 READ/WRITE 엣지
        ParsedFile entity = parsedFileWithDb("article/article.entity.ts", "TypeScript",
                List.of(new DbTableInfo("article", "ArticleEntity")), List.of());
        ParsedFile service = parsedFileWithDb("article/article.service.ts", "TypeScript",
                List.of(), List.of(new DbAccess("ArticleEntity", false), new DbAccess("ArticleEntity", true)));

        graphBuilder.build(projectId, analysisId, List.of(entity, service));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        boolean hasRead = cap.getAllValues().stream().anyMatch(e -> e.getType() == EdgeType.DB_READ);
        boolean hasWrite = cap.getAllValues().stream().anyMatch(e -> e.getType() == EdgeType.DB_WRITE);
        assertThat(hasRead).isTrue();
        assertThat(hasWrite).isTrue();
    }

    @Test
    @DisplayName("비JPA ORM 접근 — 서로 다른 서비스의 동일 파일명(models.py)이 같은 엔티티에 접근해도 둘 다 엣지가 생성된다(A-2 dedup 재발 방지)")
    void ormDbAccess_sameFileNameDifferentServices_bothEdgesCreated() {
        // usedDbEdgeIds는 전역 Set — 파일명만 키로 쓰면 두 번째 서비스의 엣지가 조용히 드롭됐던 버그(line 441과 동일 원인)
        ParsedFile model = parsedFileWithDb("shared/models.py", "Python",
                List.of(new DbTableInfo("orders", "Order")), List.of());
        ParsedFile serviceA = parsedFileWithDb("service-a/models.py", "Python",
                List.of(), List.of(new DbAccess("Order", false)));
        ParsedFile serviceB = parsedFileWithDb("service-b/models.py", "Python",
                List.of(), List.of(new DbAccess("Order", false)));

        graphBuilder.build(projectId, analysisId, List.of(model, serviceA, serviceB));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        long readEdges = cap.getAllValues().stream().filter(e -> e.getType() == EdgeType.DB_READ).count();
        assertThat(readEdges).isEqualTo(2);
    }

    @Test
    @DisplayName("raw SQL 접근 — 서로 다른 서비스의 동일 파일명(db.py)이 같은 테이블에 접근해도 둘 다 엣지가 생성된다(A-2 dedup 재발 방지)")
    void rawSqlAccess_sameFileNameDifferentServices_bothEdgesCreated() {
        ParsedFile serviceA = new ParsedFile(
                "service-a/db.py", "Python", List.of(), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(new RawSqlAccess("orders", false)), // rawSqlAccesses
                List.of(), List.of(), Map.of());
        ParsedFile serviceB = new ParsedFile(
                "service-b/db.py", "Python", List.of(), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(new RawSqlAccess("orders", false)), // rawSqlAccesses
                List.of(), List.of(), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(serviceA, serviceB));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        long readEdges = cap.getAllValues().stream().filter(e -> e.getType() == EdgeType.DB_READ).count();
        assertThat(readEdges).isEqualTo(2);
    }

    @Test
    @DisplayName("TS baseUrl bare import 세그먼트 경계 — 'entities/task' 가 .../other-task/index.ts 로 오매칭되지 않음")
    void tsImport_baseUrlBare_segmentBoundary_noPartialMatch() {
        ParsedFile importer = parsedFileWithImports("features/toggle-task/ui.tsx", "TypeScript",
                List.of("entities/task"));
        ParsedFile decoy = parsedFile("entities/other-task/index.ts", "TypeScript", List.of("X"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(importer, decoy));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        long importEdges = cap.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.IMPORT).count();
        assertThat(importEdges).isZero();
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

    @Test
    @DisplayName("ParsedFile.functionLines()의 줄 번호가 FUNCTION 노드 메타데이터에 저장된다")
    void FUNCTION_노드_줄번호_메타데이터_저장() {
        ParsedFile file = new ParsedFile("src/UserService.java", "Java",
                List.of("findById"), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), null,
                Map.of(), List.of(), Map.of("findById", 3));

        graphBuilder.build(projectId, analysisId, List.of(file));

        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(graphRepository, atLeastOnce()).saveNode(nodeCaptor.capture());

        Node funcNode = nodeCaptor.getAllValues().stream()
                .filter(n -> n.getType() == NodeType.FUNCTION && "findById".equals(n.getName()))
                .findFirst().orElseThrow();

        assertThat(funcNode.getMetadata().get("line")).isEqualTo(3);
    }

    @Test
    @DisplayName("ParsedFile.functionColumns()의 컬럼이 FUNCTION 노드 메타데이터에 시작/끝으로 저장된다")
    void FUNCTION_노드_컬럼_메타데이터_저장() {
        ParsedFile file = new ParsedFile("src/UserService.java", "Java",
                List.of("findById"), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), null,
                Map.of(), List.of(), Map.of("findById", 3), Map.of("findById", 16));

        graphBuilder.build(projectId, analysisId, List.of(file));

        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(graphRepository, atLeastOnce()).saveNode(nodeCaptor.capture());

        Node funcNode = nodeCaptor.getAllValues().stream()
                .filter(n -> n.getType() == NodeType.FUNCTION && "findById".equals(n.getName()))
                .findFirst().orElseThrow();

        // endCol = col(16) + "findById".length()(8) = 24
        assertThat(funcNode.getMetadata().get("col")).isEqualTo(16);
        assertThat(funcNode.getMetadata().get("endCol")).isEqualTo(24);
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

    // ── 외부 심볼(JDK/테스트 프레임워크) 동명 오귀속 차단 (엣지 정확도 패턴 B) ──

    @Test
    @DisplayName("Mockito verify(정적 임포트) bare 호출은 레포 내 동명 정의로 phantom 연결되지 않는다 (패턴 B)")
    void Mockito_verify_정적임포트_phantom_엣지_미생성() {
        // GitHubWebhookServiceTest 등 테스트 파일이 Mockito.verify(...)를 호출 — 자기 정의도 없고
        // import도 org.mockito.Mockito.verify(정적 임포트)라 도메인 파일 매칭이 안 되므로 전역 폴백에 빠짐.
        // 그런데 레포에 실제로 verify()란 이름의 도메인 메서드(WebhookSignatureVerifier.verify)가 있어
        // 무관한 테스트 호출이 거기로 phantom 연결되던 사각(자기 레포 실측으로 확인된 버그).
        ParsedFile testFile = parsedFileWithCallsAndImports("src/test/GitHubWebhookServiceTest.java", "Java",
                List.of("호출_검증"), Map.of("호출_검증", List.of("verify")),
                List.of("org.mockito.Mockito.verify"));
        ParsedFile decoy = parsedFile("src/domain/analysis/WebhookSignatureVerifier.java", "Java",
                List.of("verify"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(testFile, decoy));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasPhantomVerifyEdge = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .anyMatch(e -> e.getEdgeIdentifier().contains("호출_검증") && e.getEdgeIdentifier().contains("verify"));

        assertThat(hasPhantomVerifyEdge).isFalse();
    }

    @Test
    @DisplayName("JDK Stream collect 폴백 호출은 레포 내 동명 정의로 phantom 연결되지 않는다 (패턴 B)")
    void JDK_Stream_collect_폴백_phantom_엣지_미생성() {
        // list.stream().collect(...) — 실제 타깃은 JDK Collectors(노드 없음). 레포에 우연히 collect()란
        // 이름의 도메인 메서드(AdminMetricsQuery.collect)가 있어 무관한 호출이 거기로 phantom 연결되던 사각
        // (자기 레포 실측: AnalysisApplicationService·GraphDiffService 등 다수 파일의 .collect(Collectors.x())).
        ParsedFile caller = parsedFileWithCallsAndImports("src/graph/GraphDiffService.java", "Java",
                List.of("diff"), Map.of("diff", List.of("collect")),
                List.of());
        ParsedFile decoy = parsedFile("src/infra/admin/AdminMetricsQuery.java", "Java",
                List.of("collect"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, decoy));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasPhantomCollectEdge = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .anyMatch(e -> e.getEdgeIdentifier().contains("diff") && e.getEdgeIdentifier().contains("collect"));

        assertThat(hasPhantomCollectEdge).isFalse();
    }

    @Test
    @DisplayName("collect 호출도 caller가 실제 import한 파일이면 정상 연결된다 (AdminDigestService→AdminMetricsQuery.collect 실사례 보존)")
    void collect_실제_import된_경우엔_엣지_보존() {
        // 차단은 "미해소 시 폴백"에만 적용 — caller가 명시적으로 import한 실제 도메인 메서드 호출까지
        // 막으면 안 된다 (AdminDigestService.metricsQuery.collect(...) 자기 레포 실사례).
        ParsedFile caller = parsedFileWithCallsAndImports("src/app/AdminDigestService.java", "Java",
                List.of("sendDigest"), Map.of("sendDigest", List.of("collect")),
                List.of("infra.admin.AdminMetricsQuery"));
        ParsedFile metricsQuery = parsedFile("src/infra/admin/AdminMetricsQuery.java", "Java",
                List.of("collect"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, metricsQuery));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasImportedCollectEdge = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .anyMatch(e -> e.getEdgeIdentifier().contains("sendDigest") && e.getEdgeIdentifier().contains("collect")
                        && e.getMetadata().get("calleeFile").toString().contains("AdminMetricsQuery"));

        assertThat(hasImportedCollectEdge).isTrue();
    }

    @Test
    @DisplayName("한정 호출(Type::method)의 targetClass가 JDK 흔한 클래스명이면 레포 내 동명 클래스로 해소하지 않는다 (패턴 B, qualified)")
    void 한정호출_JDK_클래스명_동명클래스_phantom_엣지_미생성() {
        // java.net.http.HttpResponse<String>.body() 호출 — 레포가 별개로 "HttpResponse"란 이름의
        // 자체 DTO 클래스를 두고 거기에도 body()가 있으면, 한정 호출 해소가 import 스코프 없이 파일명만
        // 보고 매칭해 무관한 로컬 클래스로 phantom 연결된다.
        ParsedFile caller = parsedFileWithCalls("src/client/ApiClient.java", "Java",
                List.of("send"), Map.of("send", List.of("HttpResponse::body")));
        ParsedFile decoy = parsedFile("src/dto/HttpResponse.java", "Java",
                List.of("body"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, decoy));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasPhantomBodyEdge = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .anyMatch(e -> e.getEdgeIdentifier().contains("send") && e.getEdgeIdentifier().contains("body"));

        assertThat(hasPhantomBodyEdge).isFalse();
    }

    // ── 상속 메서드 호출 해소 (엣지 정확도 패턴 A') ──────────────────────────

    @Test
    @DisplayName("자식이 호출하는 상속 메서드는 부모 정의로 연결된다 — 무관한 동명 함수(decoy)로 phantom 연결되지 않는다 (패턴 A')")
    void 상속_메서드_호출_부모로_해소() {
        // DECISIONS_ANALYSIS.md 표본: AbstractTreeSitterAnalyzer.text()를 자식이 상속받아 호출하는데
        // 자기 파일엔 정의가 없어(상속) bare 해소가 전역 폴백에 빠지고, 무관한 GitHubWebhookService.text로
        // phantom 연결되던 사각.
        ParsedFile parent = parsedFile("src/analysis/AbstractTreeSitterAnalyzer.java", "Java",
                List.of("text"), Map.of());
        ParsedFile child = parsedFileWithExtends("src/analysis/JavaTreeSitterAnalyzer.java", "Java",
                List.of("walk"), Map.of("walk", List.of("text")), "AbstractTreeSitterAnalyzer");
        ParsedFile decoy = parsedFile("src/webhook/GitHubWebhookService.java", "Java",
                List.of("text"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(parent, child, decoy));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        List<Edge> textCallEdges = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .filter(e -> e.getEdgeIdentifier().contains("walk") && e.getEdgeIdentifier().contains("text"))
                .toList();

        assertThat(textCallEdges).hasSize(1);
        assertThat(textCallEdges.get(0).getMetadata().get("calleeFile")).isEqualTo("src/analysis/AbstractTreeSitterAnalyzer.java");
    }

    @Test
    @DisplayName("2단계 상속(조부모) 메서드 호출도 체인을 타고 올라가 해소된다")
    void 다단계_상속_메서드_호출_해소() {
        ParsedFile grandparent = parsedFile("src/analysis/BaseAnalyzer.java", "Java", List.of("log"), Map.of());
        ParsedFile parent = parsedFileWithExtends("src/analysis/AbstractTreeSitterAnalyzer.java", "Java",
                List.of("text"), Map.of(), "BaseAnalyzer");
        ParsedFile child = parsedFileWithExtends("src/analysis/JavaTreeSitterAnalyzer.java", "Java",
                List.of("walk"), Map.of("walk", List.of("log")), "AbstractTreeSitterAnalyzer");

        graphBuilder.build(projectId, analysisId, List.of(grandparent, parent, child));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean resolvedToGrandparent = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .filter(e -> e.getEdgeIdentifier().contains("walk") && e.getEdgeIdentifier().contains("log"))
                .anyMatch(e -> "src/analysis/BaseAnalyzer.java".equals(e.getMetadata().get("calleeFile")));

        assertThat(resolvedToGrandparent).isTrue();
    }

    @Test
    @DisplayName("상속 체인 어디에도 정의가 없으면 기존 bare 폴백 해소로 정상 동작한다 (recall 보존)")
    void 상속체인에_정의없으면_기존_폴백_유지() {
        // extendedClass가 있어도 체인 전체에 calleeFunc가 없으면 일반 bare 호출 해소로 폴백해야 한다
        ParsedFile parent = parsedFile("src/analysis/AbstractTreeSitterAnalyzer.java", "Java",
                List.of("text"), Map.of());
        ParsedFile child = parsedFileWithCallsAndImportsAndExtends("src/analysis/JavaTreeSitterAnalyzer.java", "Java",
                List.of("walk"), Map.of("walk", List.of("computeDigest")),
                List.of("util.Digest"), "AbstractTreeSitterAnalyzer");
        ParsedFile util = parsedFile("src/util/Digest.java", "Java", List.of("computeDigest"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(parent, child, util));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        boolean hasEdge = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.FUNCTION_CALL)
                .anyMatch(e -> e.getEdgeIdentifier().contains("walk") && e.getEdgeIdentifier().contains("computeDigest")
                        && "src/util/Digest.java".equals(e.getMetadata().get("calleeFile")));

        assertThat(hasEdge).isTrue();
    }

    // ── API_ENDPOINT 노드 실체화 (§16 로드맵, 파일 단위 1차) ────────────────

    @Test
    @DisplayName("controllerMappings가 있는 파일은 경로마다 API_ENDPOINT 노드 + FILE→API_ENDPOINT CONTAINS 엣지를 생성한다")
    void controllerMappings_있으면_API_ENDPOINT_노드_생성() {
        ParsedFile controller = parsedFileWithMappings("src/UserController.java", "Java",
                List.of("/api/users", "/api/users/{id}"));

        graphBuilder.build(projectId, analysisId, List.of(controller));

        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(graphRepository, atLeastOnce()).saveNode(nodeCaptor.capture());
        List<Node> endpointNodes = nodeCaptor.getAllValues().stream()
                .filter(n -> n.getType() == NodeType.API_ENDPOINT)
                .toList();
        assertThat(endpointNodes).hasSize(2);
        assertThat(endpointNodes).extracting(Node::getName)
                .containsExactlyInAnyOrder("/api/users", "/api/users/{id}");

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());
        long endpointEdges = edgeCaptor.getAllValues().stream()
                .filter(e -> e.getType() == EdgeType.CONTAINS)
                .filter(e -> e.getEdgeIdentifier().contains("endpoint"))
                .count();
        assertThat(endpointEdges).isEqualTo(2);
    }

    @Test
    @DisplayName("controllerMappings가 없는 파일은 API_ENDPOINT 노드를 생성하지 않는다")
    void controllerMappings_없으면_API_ENDPOINT_노드_미생성() {
        ParsedFile plain = parsedFile("src/UserService.java", "Java", List.of("save"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(plain));

        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(graphRepository, atLeastOnce()).saveNode(nodeCaptor.capture());
        boolean hasEndpointNode = nodeCaptor.getAllValues().stream()
                .anyMatch(n -> n.getType() == NodeType.API_ENDPOINT);
        assertThat(hasEndpointNode).isFalse();
    }

    @Test
    @DisplayName("같은 파일에 동일 경로 매핑이 중복 추출돼도 API_ENDPOINT 노드는 하나만 생성된다")
    void 중복_매핑_문자열은_노드_하나만_생성() {
        ParsedFile controller = parsedFileWithMappings("src/UserController.java", "Java",
                List.of("/api/users", "/api/users"));

        graphBuilder.build(projectId, analysisId, List.of(controller));

        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(graphRepository, atLeastOnce()).saveNode(nodeCaptor.capture());
        long endpointNodeCount = nodeCaptor.getAllValues().stream()
                .filter(n -> n.getType() == NodeType.API_ENDPOINT)
                .count();
        assertThat(endpointNodeCount).isEqualTo(1);
    }

    @Test
    @DisplayName("controllerMappingFunctions로 해소된 매핑은 API_ENDPOINT → FUNCTION을 FUNCTION_CALL 엣지로 연결한다")
    void controllerMappingFunctions_있으면_처리함수로_FUNCTION_CALL_엣지_생성() {
        ParsedFile controller = parsedFileWithMappingsAndFunctions("src/UserController.java", "Java",
                List.of("list", "delete"),
                List.of("/api/users", "/api/users/{id}"),
                Map.of("/api/users", "list", "/api/users/{id}", "delete"));

        graphBuilder.build(projectId, analysisId, List.of(controller));

        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(graphRepository, atLeastOnce()).saveNode(nodeCaptor.capture());
        Node listEndpoint = nodeCaptor.getAllValues().stream()
                .filter(n -> n.getType() == NodeType.API_ENDPOINT && "/api/users".equals(n.getName()))
                .findFirst().orElseThrow();
        Node listFunc = nodeCaptor.getAllValues().stream()
                .filter(n -> n.getType() == NodeType.FUNCTION && "list".equals(n.getName()))
                .findFirst().orElseThrow();

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());
        boolean hasHandlerEdge = edgeCaptor.getAllValues().stream()
                .anyMatch(e -> e.getType() == EdgeType.FUNCTION_CALL
                        && e.getSourceNodeId().equals(listEndpoint.getId())
                        && e.getTargetNodeId().equals(listFunc.getId()));
        assertThat(hasHandlerEdge).isTrue();
    }

    @Test
    @DisplayName("controllerMappingFunctions가 없으면 API_ENDPOINT에서 나가는 FUNCTION_CALL 엣지를 생성하지 않는다")
    void controllerMappingFunctions_없으면_핸들러_엣지_미생성() {
        ParsedFile controller = parsedFileWithMappings("src/UserController.java", "Java",
                List.of("/api/users"));

        graphBuilder.build(projectId, analysisId, List.of(controller));

        ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(graphRepository, atLeastOnce()).saveNode(nodeCaptor.capture());
        Node endpoint = nodeCaptor.getAllValues().stream()
                .filter(n -> n.getType() == NodeType.API_ENDPOINT)
                .findFirst().orElseThrow();

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());
        boolean hasOutgoingFunctionCall = edgeCaptor.getAllValues().stream()
                .anyMatch(e -> e.getType() == EdgeType.FUNCTION_CALL && e.getSourceNodeId().equals(endpoint.getId()));
        assertThat(hasOutgoingFunctionCall).isFalse();
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

    // ── SERVICE_CALL 엣지 생성 (모노레포 MSA 서비스 간 호출) ──────────────────

    @Test
    @DisplayName("논리 서비스명이 대상 서비스 디렉터리와 부분 일치하면 SERVICE_CALL 엣지가 생성된다")
    void 서비스_호출_엣지_생성() {
        ParsedFile caller = parsedFileWithServiceCalls(
                "api-gateway/src/CustomersServiceClient.java", "Java", List.of("customers-service"));
        ParsedFile target = parsedFileWithImports(
                "spring-petclinic-customers-service/src/OwnerController.java", "Java", List.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, target));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        assertThat(edgeCaptor.getAllValues()).anyMatch(e -> e.getType() == EdgeType.SERVICE_CALL);
    }

    @Test
    @DisplayName("논리 서비스명과 일치하는 서비스 디렉터리가 없으면(외부 API 등) SERVICE_CALL 엣지를 만들지 않는다")
    void 서비스_호출_대상_없으면_엣지_미생성() {
        ParsedFile caller = parsedFileWithServiceCalls(
                "api-gateway/src/PaymentClient.java", "Java", List.of("stripe-api"));
        ParsedFile unrelated = parsedFileWithImports(
                "spring-petclinic-customers-service/src/OwnerController.java", "Java", List.of());
        // saveEdge 스텁이 실제로 쓰이도록 무관한 IMPORT 관계를 하나 동반(strict stubbing 대응)
        ParsedFile importer = parsedFileWithImports("src/com/example/UserController.java", "Java",
                List.of("com.example.UserService"));
        ParsedFile importee = parsedFile("src/com/example/UserService.java", "Java", List.of("createUser"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, unrelated, importer, importee));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        assertThat(edgeCaptor.getAllValues()).noneMatch(e -> e.getType() == EdgeType.SERVICE_CALL);
    }

    @Test
    @DisplayName("같은 서비스 안의 자기 호출은 SERVICE_CALL 엣지로 만들지 않는다")
    void 서비스_호출_동일서비스_자기호출_제외() {
        ParsedFile caller = parsedFileWithServiceCalls(
                "spring-petclinic-customers-service/src/InternalClient.java", "Java", List.of("customers-service"));
        ParsedFile target = parsedFileWithImports(
                "spring-petclinic-customers-service/src/OwnerController.java", "Java", List.of());
        // saveEdge 스텁이 실제로 쓰이도록 무관한 IMPORT 관계를 하나 동반(strict stubbing 대응)
        ParsedFile importer = parsedFileWithImports("src/com/example/UserController.java", "Java",
                List.of("com.example.UserService"));
        ParsedFile importee = parsedFile("src/com/example/UserService.java", "Java", List.of("createUser"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(caller, target, importer, importee));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        assertThat(edgeCaptor.getAllValues()).noneMatch(e -> e.getType() == EdgeType.SERVICE_CALL);
    }

    @Test
    @DisplayName("서로 다른 서비스의 동일 파일명(Client.java)이 같은 대상 서비스를 호출해도 둘 다 SERVICE_CALL 엣지가 생성된다(A-2 dedup 재발 방지)")
    void serviceCall_sameFileNameDifferentServices_bothEdgesCreated() {
        // usedServiceCallEdgeIds도 usedDbEdgeIds와 같은 원인의 dedup 버그 — 파일명만 키로 쓰면
        // 두 번째 호출자 서비스의 엣지가 조용히 드롭된다.
        ParsedFile callerA = parsedFileWithServiceCalls("service-a/src/Client.java", "Java", List.of("customers-service"));
        ParsedFile callerB = parsedFileWithServiceCalls("service-b/src/Client.java", "Java", List.of("customers-service"));
        ParsedFile target = parsedFileWithImports(
                "spring-petclinic-customers-service/src/OwnerController.java", "Java", List.of());

        graphBuilder.build(projectId, analysisId, List.of(callerA, callerB, target));

        ArgumentCaptor<Edge> cap = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(cap.capture());
        long serviceCallEdges = cap.getAllValues().stream().filter(e -> e.getType() == EdgeType.SERVICE_CALL).count();
        assertThat(serviceCallEdges).isEqualTo(2);
    }

    @Test
    @DisplayName("FeignClient 인터페이스를 import하면 SERVICE_CALL 엣지가 생성된다(DI 대신 IMPORT 재사용)")
    void 서비스_호출_FeignClient_엣지_생성() {
        ParsedFile feignInterface = parsedFileWithFeignTarget(
                "api-gateway/src/com/example/api/CustomersServiceClient.java", "Java", "customers-service");
        ParsedFile caller = parsedFileWithImports(
                "api-gateway/src/com/example/GatewayService.java", "Java",
                List.of("com.example.api.CustomersServiceClient"));
        ParsedFile target = parsedFileWithImports(
                "spring-petclinic-customers-service/src/OwnerController.java", "Java", List.of());

        graphBuilder.build(projectId, analysisId, List.of(feignInterface, caller, target));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        assertThat(edgeCaptor.getAllValues()).anyMatch(e -> e.getType() == EdgeType.SERVICE_CALL);
    }

    @Test
    @DisplayName("FeignClient 대상이 자기 서비스면 SERVICE_CALL 엣지를 만들지 않는다")
    void 서비스_호출_FeignClient_동일서비스_자기호출_제외() {
        ParsedFile feignInterface = parsedFileWithFeignTarget(
                "spring-petclinic-customers-service/src/com/example/api/InternalClient.java", "Java", "customers-service");
        ParsedFile caller = parsedFileWithImports(
                "spring-petclinic-customers-service/src/com/example/GatewayService.java", "Java",
                List.of("com.example.api.InternalClient"));
        // saveEdge 스텁이 실제로 쓰이도록 무관한 IMPORT 관계를 하나 동반(strict stubbing 대응)
        ParsedFile importer = parsedFileWithImports("src/com/example/UserController.java", "Java",
                List.of("com.example.UserService"));
        ParsedFile importee = parsedFile("src/com/example/UserService.java", "Java", List.of("createUser"), Map.of());

        graphBuilder.build(projectId, analysisId, List.of(feignInterface, caller, importer, importee));

        ArgumentCaptor<Edge> edgeCaptor = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor.capture());

        assertThat(edgeCaptor.getAllValues()).noneMatch(e -> e.getType() == EdgeType.SERVICE_CALL);
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

    // 안정성 갭 B — 같은 입력을 두 번 빌드해도 노드/엣지 구성이 완전히 동일해야 한다(결정론 회귀 테스트).
    // 동명 함수가 여러 파일에 존재하는 모호한 호출(전역 폴백 후보 선택)을 포함시켜, 후보 목록 순회 순서가
    // Map/Set 반복 순서에 우연히 좌우되는 잠재적 비결정성까지 노출되도록 구성.
    @Test
    @DisplayName("동일 입력을 두 번 빌드해도 노드·엣지 구성이 완전히 동일하다(결정론)")
    void 같은_입력_두번_빌드_결과_동일() throws Exception {
        List<ParsedFile> parsedFiles = List.of(
                parsedFileWithCalls("src/a.js", "JavaScript", List.of("run"), Map.of("run", List.of("handle"))),
                parsedFileWithCalls("src/handlers/one.js", "JavaScript", List.of("handle"), Map.of()),
                parsedFileWithCalls("src/handlers/two.js", "JavaScript", List.of("handle"), Map.of()),
                parsedFileWithCalls("src/handlers/three.js", "JavaScript", List.of("handle"), Map.of()),
                parsedFileWithCallsAndImports("src/b.js", "JavaScript", List.of("dispatch"),
                        Map.of("dispatch", List.of("handle")), List.of())
        );

        // 1차 빌드 — 클래스 공용 graphBuilder/mock 사용
        graphBuilder.build(UUID.randomUUID(), UUID.randomUUID(), parsedFiles);
        ArgumentCaptor<Node> nodeCaptor1 = ArgumentCaptor.forClass(Node.class);
        ArgumentCaptor<Edge> edgeCaptor1 = ArgumentCaptor.forClass(Edge.class);
        verify(graphRepository, atLeastOnce()).saveNode(nodeCaptor1.capture());
        verify(graphRepository, atLeastOnce()).saveEdge(edgeCaptor1.capture());
        Set<String> nodesA = toNodeKeys(nodeCaptor1.getAllValues());
        Set<String> edgesA = toEdgeKeys(edgeCaptor1.getAllValues());

        // 2차 빌드 — 완전히 독립된 GraphBuilder+mock으로 같은 입력을 다시 빌드
        GraphRepository repo2 = mock(GraphRepository.class);
        ProjectRepository projRepo2 = mock(ProjectRepository.class);
        SnapshotReferencePort snapshotPort2 = mock(SnapshotReferencePort.class);
        when(repo2.save(any(Graph.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repo2.saveNode(any(Node.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repo2.saveEdge(any(Edge.class))).thenAnswer(inv -> inv.getArgument(0));
        when(projRepo2.findById(any())).thenReturn(Optional.empty());
        when(snapshotPort2.findReferencedGraphIds(any())).thenReturn(Set.of());
        new GraphBuilder(repo2, projRepo2, snapshotPort2)
                .build(UUID.randomUUID(), UUID.randomUUID(), parsedFiles);
        ArgumentCaptor<Node> nodeCaptor2 = ArgumentCaptor.forClass(Node.class);
        ArgumentCaptor<Edge> edgeCaptor2 = ArgumentCaptor.forClass(Edge.class);
        verify(repo2, atLeastOnce()).saveNode(nodeCaptor2.capture());
        verify(repo2, atLeastOnce()).saveEdge(edgeCaptor2.capture());
        Set<String> nodesB = toNodeKeys(nodeCaptor2.getAllValues());
        Set<String> edgesB = toEdgeKeys(edgeCaptor2.getAllValues());

        assertThat(nodesA).isEqualTo(nodesB);
        assertThat(edgesA).isEqualTo(edgesB);
    }

    // 노드를 (type, name, filePath) 안정 키 집합으로 변환 — 매 빌드마다 새로 발급되는 랜덤 UUID는 비교에서 제외
    private Set<String> toNodeKeys(List<Node> nodes) {
        return nodes.stream()
                .map(n -> n.getType() + "|" + n.getName() + "|" + n.getFilePath())
                .collect(Collectors.toSet());
    }

    // 엣지를 (edgeIdentifier, type) 안정 키 집합으로 변환 — sourceNodeId/targetNodeId는 빌드마다 새 랜덤 UUID라
    // 비교 불가하므로 제외하고, 파일명·함수명 기반 edgeIdentifier로 동일성을 판정
    private Set<String> toEdgeKeys(List<Edge> edges) {
        return edges.stream()
                .map(e -> e.getEdgeIdentifier() + "|" + e.getType())
                .collect(Collectors.toSet());
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

    // 호출 + 상위 클래스명(extends)을 지정하는 헬퍼 — 상속 메서드 해소 테스트용
    private ParsedFile parsedFileWithExtends(String path, String lang, List<String> functions,
                                             Map<String, List<String>> calls, String extendedClass) {
        return new ParsedFile(path, lang, functions, List.of(), null, Map.of(),
                calls, List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(),
                List.of(), List.of(), List.of(), extendedClass);
    }

    // 호출 + import + 상위 클래스명을 모두 지정하는 헬퍼 — 상속 체인 미해소 시 기존 폴백 검증용
    private ParsedFile parsedFileWithCallsAndImportsAndExtends(String path, String lang, List<String> functions,
                                                               Map<String, List<String>> calls, List<String> imports,
                                                               String extendedClass) {
        return new ParsedFile(path, lang, functions, imports, null, Map.of(),
                calls, List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(),
                List.of(), List.of(), List.of(), extendedClass);
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

    // 서비스 간 호출 파일 생성 헬퍼 — serviceCalls 포함(canonical 생성자, 나머지 필드는 전부 기본값)
    private ParsedFile parsedFileWithServiceCalls(String path, String lang, List<String> serviceCalls) {
        return new ParsedFile(path, lang, List.of(), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of(), Map.of(), Map.of(), List.of(), serviceCalls);
    }

    // FeignClient 인터페이스 파일 생성 헬퍼 — feignClientTarget 포함(canonical 생성자, 나머지 필드는 전부 기본값)
    private ParsedFile parsedFileWithFeignTarget(String path, String lang, String feignClientTarget) {
        return new ParsedFile(path, lang, List.of(), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(), feignClientTarget);
    }

    // DB 테이블/ORM 접근 지정 헬퍼 — 비JPA ORM 코드→테이블 엣지 테스트용 (canonical 생성자: declaredTypes/testMethods/dbAccesses 포함)
    private ParsedFile parsedFileWithDb(String path, String lang, List<DbTableInfo> dbTables, List<DbAccess> dbAccesses) {
        return new ParsedFile(path, lang, List.of(), List.of(), null, Map.of(),
                Map.of(), List.of(), dbTables, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(),
                List.of(), List.of(), dbAccesses);
    }

    // 백엔드 컨트롤러 매핑 파일 생성 헬퍼 — controllerMappings 포함
    private ParsedFile parsedFileWithMappings(String path, String lang, List<String> mappings) {
        return new ParsedFile(path, lang, List.of(), List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), mappings, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    // 컨트롤러 매핑 + 처리 함수 해소 파일 생성 헬퍼 — controllerMappingFunctions 포함(API_ENDPOINT→FUNCTION 엣지 테스트용)
    private ParsedFile parsedFileWithMappingsAndFunctions(String path, String lang, List<String> functions,
                                                            List<String> mappings, Map<String, String> mappingFunctions) {
        return new ParsedFile(path, lang, functions, List.of(), null, Map.of(),
                Map.of(), List.of(), List.of(), null, List.of(), List.of(), mappings, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), Map.of(), List.of(), List.of(), List.of(), null, mappingFunctions);
    }
}
