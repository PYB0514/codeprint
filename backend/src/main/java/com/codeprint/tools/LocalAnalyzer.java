// DB 없이 로컬 소스 디렉터리를 분석하여 GraphWarning을 출력하는 CLI 도구
package com.codeprint.tools;

import com.codeprint.application.graph.GraphWarningService;
import com.codeprint.domain.graph.ArchitectureIntent;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.GraphRepository;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.port.SnapshotReferencePort;
import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;
import com.codeprint.infrastructure.analysis.CachedParsedFileLoader;
import com.codeprint.infrastructure.analysis.GraphBuilder;
import com.codeprint.infrastructure.analysis.ParsedFile;
import com.codeprint.infrastructure.analysis.SourceFileWalker;
import com.codeprint.infrastructure.analysis.StaticCodeAnalyzer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class LocalAnalyzer {

    // 지정 디렉터리를 분석하여 워닝 목록을 stdout에 출력
    public static void main(String[] args) throws Exception {
        Path rootDir = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        System.out.println("분석 대상: " + rootDir.toAbsolutePath());

        UUID projectId = UUID.randomUUID();
        CachedParsedFileLoader loader = new CachedParsedFileLoader(new StaticCodeAnalyzer(), new InMemoryParsedFileCachePort());
        List<Map<String, Object>> warnings = analyze(rootDir, projectId, loader);

        if (warnings.isEmpty()) {
            System.out.println("\n✅ 워닝 없음");
        } else {
            System.out.println("\n⚠️  워닝 " + warnings.size() + "개 감지:");
            Map<String, Integer> counts = new HashMap<>();
            for (Map<String, Object> w : warnings) {
                String type = (String) w.get("type");
                counts.merge(type, 1, Integer::sum);
                System.out.println("  [" + type + "] " + w.get("message"));
            }
            System.out.println("\n--- 유형별 요약 ---");
            counts.forEach((type, count) -> System.out.println("  " + type + ": " + count + "개"));
        }
    }

    // rootDir을 분석해 워닝 목록을 반환 — LocalWatcher가 동일 loader(캐시 유지)로 반복 호출
    static List<Map<String, Object>> analyze(Path rootDir, UUID projectId, CachedParsedFileLoader loader) throws Exception {
        GraphResult result = buildGraph(rootDir, projectId, loader);

        // 의도 아키텍처 선언(.codeprint/architecture.json)이 있으면 INTENT_DRIFT까지 검사
        ArchitectureIntent intent = loadIntent(rootDir);
        if (intent != null) {
            System.out.println("의도 선언 로드: 모듈 " + intent.modules().size() + "개, 규칙 " + intent.rules().size() + "개");
        }

        GraphWarningService warningService = new GraphWarningService();
        return warningService.detect(result.nodes(), result.edges(), intent);
    }

    // rootDir을 파싱+그래프 구성까지만 수행 — LocalGraphQuery(repo map·노드검색·이웃조회)가 워닝 계산 없이 재사용
    static GraphResult buildGraph(Path rootDir, UUID projectId, CachedParsedFileLoader loader) throws Exception {
        SourceFileWalker walker = new SourceFileWalker();
        List<Path> files = walker.walk(rootDir).files();
        System.out.println("소스 파일 수: " + files.size());

        List<ParsedFile> parsedFiles = loader.load(projectId, rootDir, files);
        System.out.println("파싱 완료: " + parsedFiles.size() + " 파일");

        // 프로덕션과 동일한 GraphBuilder로 그래프 구성 — 인메모리 Repository로 DB 없이 실행.
        // 과거 자체 재구현(buildGraph)은 인터페이스→구현체 우선 매칭·sameFile 마커·isFrameworkAnnotated 메타가 빠져
        // 프로덕션보다 호출 해소가 약했고 미호출 비율이 부풀려져(예: petclinic 19% vs 프로덕션 1%) 임계값 교정에 쓸 수 없었다.
        InMemoryGraphRepository repo = new InMemoryGraphRepository();
        GraphBuilder builder = new GraphBuilder(repo, new NoOpProjectRepository(), new NoOpSnapshotReferencePort());
        Graph graph = builder.build(projectId, UUID.randomUUID(), parsedFiles);
        List<Node> nodes = repo.findNodesByGraphId(graph.getId());
        List<Edge> edges = repo.findEdgesByGraphId(graph.getId());
        System.out.println("노드: " + nodes.size() + ", 엣지: " + edges.size());

        return new GraphResult(nodes, edges);
    }

    // buildGraph 결과 묶음 — 노드·엣지만 필요한 호출자를 위해 워닝 계산과 분리
    record GraphResult(List<Node> nodes, List<Edge> edges) {}

    // rootDir/.codeprint/architecture.json 을 읽어 의도 아키텍처를 구성 — 없거나 파싱 실패면 null
    private static ArchitectureIntent loadIntent(Path rootDir) {
        Path file = rootDir.resolve(".codeprint").resolve("architecture.json");
        if (!Files.isRegularFile(file)) return null;
        try {
            JsonNode root = new ObjectMapper().readTree(Files.readString(file));
            List<ArchitectureIntent.Module> modules = new ArrayList<>();
            for (JsonNode m : root.path("modules")) {
                List<String> globs = new ArrayList<>();
                for (JsonNode g : m.path("globs")) globs.add(g.asText());
                modules.add(new ArchitectureIntent.Module(m.path("name").asText(), globs));
            }
            List<ArchitectureIntent.DependencyRule> rules = new ArrayList<>();
            for (JsonNode r : root.path("rules")) {
                rules.add(new ArchitectureIntent.DependencyRule(r.path("from").asText(), r.path("to").asText()));
            }
            List<ArchitectureIntent.IgnoreRule> ignores = new ArrayList<>();
            for (JsonNode g : root.path("ignore")) {
                ignores.add(new ArchitectureIntent.IgnoreRule(
                        g.path("type").asText(null), g.path("from").asText(null), g.path("to").asText(null)));
            }
            return new ArchitectureIntent(modules, rules, ignores);
        } catch (Exception e) {
            System.err.println("의도 선언 로드 실패 (무시): " + file + " — " + e.getMessage());
            return null;
        }
    }

    // DB 없이 GraphBuilder를 구동하기 위한 인메모리 Repository — 노드/엣지를 리스트에 수집한다.
    private static class InMemoryGraphRepository implements GraphRepository {
        private final List<Graph> graphs = new ArrayList<>();
        private final List<Node> nodes = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();

        // 그래프 저장
        @Override
        public Graph save(Graph graph) {
            graphs.add(graph);
            return graph;
        }

        // ID로 그래프 조회
        @Override
        public Optional<Graph> findById(UUID id) {
            return graphs.stream().filter(g -> g.getId().equals(id)).findFirst();
        }

        // 프로젝트 ID로 그래프 목록 조회
        @Override
        public List<Graph> findByProjectId(UUID projectId) {
            return graphs.stream().filter(g -> projectId.equals(g.getProjectId())).toList();
        }

        // 그래프 ID로 노드 목록 조회
        @Override
        public List<Node> findNodesByGraphId(UUID graphId) {
            return nodes.stream().filter(n -> graphId.equals(n.getGraphId())).toList();
        }

        // 그래프 ID로 엣지 목록 조회
        @Override
        public List<Edge> findEdgesByGraphId(UUID graphId) {
            return edges.stream().filter(e -> graphId.equals(e.getGraphId())).toList();
        }

        // 노드 저장
        @Override
        public Node saveNode(Node node) {
            nodes.add(node);
            return node;
        }

        // 노드 ID로 노드 조회
        @Override
        public Optional<Node> findNodeById(UUID nodeId) {
            return nodes.stream().filter(n -> n.getId().equals(nodeId)).findFirst();
        }

        // 엣지 저장
        @Override
        public Edge saveEdge(Edge edge) {
            edges.add(edge);
            return edge;
        }

        // 그래프 삭제 (노드/엣지 함께 제거)
        @Override
        public void deleteById(UUID id) {
            graphs.removeIf(g -> g.getId().equals(id));
            nodes.removeIf(n -> id.equals(n.getGraphId()));
            edges.removeIf(e -> id.equals(e.getGraphId()));
        }

        // 프로젝트의 최신 그래프 조회
        @Override
        public Optional<Graph> findTopByProjectIdOrderByCreatedAtDesc(UUID projectId) {
            return findByProjectId(projectId).stream()
                    .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
        }

        // 고정 슬롯 비우기 — CLI에선 불필요
        @Override
        public void clearPinnedSlot(UUID projectId, int slot) {
        }
    }

    // DB 없이 GraphBuilder를 구동하기 위한 더미 — CLI엔 실제 프로젝트 소유자 개념이 없어 항상 미조회 처리
    private static class NoOpProjectRepository implements ProjectRepository {
        @Override public Project save(Project project) { throw new UnsupportedOperationException(); }
        @Override public Optional<Project> findById(UUID id) { return Optional.empty(); }
        @Override public List<Project> findByUserId(UUID userId) { return List.of(); }
        @Override public List<Project> findByRepoUrl(String repoHttpsUrl) { return List.of(); }
        @Override public int countPrivateByUserId(UUID userId) { return 0; }
        @Override public List<Project> findPublicByUserId(UUID userId) { return List.of(); }
        @Override public void deleteById(UUID id) { }
        @Override public long count() { return 0; }
        @Override public List<Project> findAllWithPrGateConnected() { return List.of(); }
    }

    // DB 없이 GraphBuilder를 구동하기 위한 더미 — CLI엔 공유 게시물 개념이 없어 항상 빈 집합
    private static class NoOpSnapshotReferencePort implements SnapshotReferencePort {
        @Override public Set<UUID> findReferencedGraphIds(UUID projectId) { return Set.of(); }
    }
}
