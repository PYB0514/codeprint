// DDD/피처슬라이스 구조가 감지되지 않는 프로젝트에, API 엔드포인트별 호출 경로 추적으로 후보 모듈 경계를 제안
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.EdgeType;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DddMigrationGuideService {

    private static final int MAX_DEPTH = 10;
    private static final int MAX_VISITED = 500;
    // 후보 모듈 경계를 나눌 때 따라갈 엣지 — "이 엔드포인트가 실제로 쓰는 것"만 본다(IMPORT는 제외, 실제 실행 경로 아님)
    private static final Set<EdgeType> TRAVERSAL_TYPES = Set.of(
            EdgeType.FUNCTION_CALL, EdgeType.INSTANTIATION,
            EdgeType.DB_READ, EdgeType.DB_WRITE, EdgeType.DB_CREATE, EdgeType.DB_UPDATE, EdgeType.DB_DELETE);
    // 전체 엔드포인트의 이 비율 이상이 공통으로 닿는 파일/테이블은 "공유 자원" — 클러스터 병합 판단에서 제외(안 그러면 공유 유틸 하나가 전체를 한 덩어리로 뭉침)
    private static final double SHARED_RATIO = 0.5;
    // 두 엔드포인트를 같은 후보 모듈로 묶으려면 공유 자원 제외하고도 겹치는 파일/테이블이 최소 이 개수 이상
    private static final int MIN_SHARED_TO_MERGE = 2;
    private static final int MIN_ENDPOINTS = 2;
    private static final int MIN_CLUSTERS = 2;

    // 후보 모듈 경계 섹션 생성 — 이미 DDD/피처슬라이스 구조가 있거나 추적 신호가 부족하면 null(호출부가 섹션 자체를 생략)
    public String generateSection(List<Node> nodes, List<Edge> edges) {
        if (hasExistingStructure(nodes)) return null;

        List<Node> endpoints = nodes.stream().filter(n -> n.getType() == NodeType.API_ENDPOINT).toList();
        if (endpoints.size() < MIN_ENDPOINTS) return null;

        Map<UUID, Node> nodesById = nodes.stream().collect(Collectors.toMap(Node::getId, n -> n));
        Map<UUID, List<UUID>> adjacency = buildAdjacency(edges);

        Map<Node, Set<String>> footprints = new LinkedHashMap<>();
        for (Node ep : endpoints) {
            Set<String> footprint = footprintOf(ep.getId(), adjacency, nodesById);
            if (!footprint.isEmpty()) footprints.put(ep, footprint);
        }
        if (footprints.size() < MIN_ENDPOINTS) return null;

        Set<String> shared = sharedLabels(footprints);
        List<Node> epList = new ArrayList<>(footprints.keySet());
        int[] parent = clusterByOverlap(epList, footprints, shared);

        Map<Integer, List<Node>> clusters = new LinkedHashMap<>();
        for (int i = 0; i < epList.size(); i++) {
            clusters.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(epList.get(i));
        }
        if (clusters.size() < MIN_CLUSTERS) return null;

        return render(clusters, footprints, shared);
    }

    // DDD 바운디드 컨텍스트 또는 피처슬라이스가 이미 2개 이상 감지되면 이 기능은 불필요(RepoMapService.generateByContext와 동일 판정 기준 재사용)
    private boolean hasExistingStructure(List<Node> nodes) {
        List<String> filePaths = nodes.stream()
                .filter(n -> n.getType() == NodeType.FILE)
                .map(n -> normalize(n.getFilePath()))
                .toList();
        Set<String> cfContexts = BoundedContextResolver.detectContextFirstContexts(filePaths);
        long dddContexts = filePaths.stream()
                .map(p -> BoundedContextResolver.functionContextOf(p, cfContexts))
                .filter(Objects::nonNull).distinct().count();
        if (dddContexts >= 2) return true;
        long featureContexts = filePaths.stream()
                .map(BoundedContextResolver::featureOf)
                .filter(Objects::nonNull).distinct().count();
        return featureContexts >= 2;
    }

    private Map<UUID, List<UUID>> buildAdjacency(List<Edge> edges) {
        Map<UUID, List<UUID>> adjacency = new HashMap<>();
        for (Edge e : edges) {
            if (!TRAVERSAL_TYPES.contains(e.getType())) continue;
            adjacency.computeIfAbsent(e.getSourceNodeId(), k -> new ArrayList<>()).add(e.getTargetNodeId());
        }
        return adjacency;
    }

    // API_ENDPOINT에서 출발해 도달 가능한 FILE/FUNCTION의 소속 파일과 DB_TABLE을 라벨로 수집(BFS, 깊이·노드 수 상한)
    private Set<String> footprintOf(UUID startId, Map<UUID, List<UUID>> adjacency, Map<UUID, Node> nodesById) {
        Set<String> labels = new LinkedHashSet<>();
        Set<UUID> visited = new HashSet<>(List.of(startId));
        Map<UUID, Integer> depthOf = new HashMap<>(Map.of(startId, 0));
        Deque<UUID> frontier = new ArrayDeque<>(List.of(startId));

        while (!frontier.isEmpty() && visited.size() < MAX_VISITED) {
            UUID cur = frontier.poll();
            int depth = depthOf.get(cur);
            addLabel(labels, nodesById.get(cur));
            if (depth >= MAX_DEPTH) continue;
            for (UUID next : adjacency.getOrDefault(cur, List.of())) {
                if (visited.add(next)) {
                    depthOf.put(next, depth + 1);
                    frontier.add(next);
                }
            }
        }
        return labels;
    }

    private void addLabel(Set<String> labels, Node n) {
        if (n == null) return;
        if ((n.getType() == NodeType.FILE || n.getType() == NodeType.FUNCTION) && n.getFilePath() != null) {
            labels.add(normalize(n.getFilePath()));
        } else if (n.getType() == NodeType.DB_TABLE) {
            labels.add("DB:" + n.getName());
        }
    }

    private Set<String> sharedLabels(Map<Node, Set<String>> footprints) {
        Map<String, Integer> count = new HashMap<>();
        footprints.values().forEach(fp -> fp.forEach(l -> count.merge(l, 1, Integer::sum)));
        // 엔드포인트가 적을 때 비율만으로 판정하면 "2개 엔드포인트가 공유"만으로도 shared가 돼 진짜 클러스터 신호를
        // 지워버린다 — 최소 절대 개수(3) 이상 닿아야 "널리 공유된 자원"으로 본다.
        int threshold = Math.max((int) Math.ceil(footprints.size() * SHARED_RATIO), 3);
        return count.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    // Union-Find — 공유 자원을 뺀 footprint끼리 겹침이 임계 이상인 엔드포인트끼리 같은 클러스터로 병합
    private int[] clusterByOverlap(List<Node> epList, Map<Node, Set<String>> footprints, Set<String> shared) {
        int n = epList.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            Set<String> a = new HashSet<>(footprints.get(epList.get(i)));
            a.removeAll(shared);
            for (int j = i + 1; j < n; j++) {
                Set<String> b = new HashSet<>(footprints.get(epList.get(j)));
                b.removeAll(shared);
                Set<String> intersection = new HashSet<>(a);
                intersection.retainAll(b);
                if (intersection.size() >= MIN_SHARED_TO_MERGE) union(parent, i, j);
            }
        }
        return parent;
    }

    private int find(int[] parent, int i) {
        while (parent[i] != i) i = parent[i];
        return i;
    }

    private void union(int[] parent, int i, int j) {
        int ri = find(parent, i);
        int rj = find(parent, j);
        if (ri != rj) parent[ri] = rj;
    }

    private String render(Map<Integer, List<Node>> clusters, Map<Node, Set<String>> footprints, Set<String> shared) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## DDD 마이그레이션 후보 가이드 (참고용)\n\n");
        sb.append("> 이 프로젝트에서 도메인/기능 구조를 감지하지 못해, API 엔드포인트별 실행 경로(호출·DB 접근)를 추적해 ");
        sb.append("자주 함께 쓰이는 파일들을 후보 모듈로 묶었습니다. 결합 패턴만 본 추정치이며 실제 도메인 판단을 대체하지 않습니다 — 참고용으로만 쓰세요.\n\n");

        int idx = 1;
        for (List<Node> clusterEndpoints : clusters.values()) {
            Set<String> files = new LinkedHashSet<>();
            clusterEndpoints.forEach(ep -> files.addAll(footprints.get(ep)));
            files.removeAll(shared);
            if (files.isEmpty()) continue;

            String name = suggestName(clusterEndpoints);
            sb.append("### 후보 모듈 ").append(idx++).append(name != null ? " — " + name : "").append("\n\n");
            sb.append("- 관련 엔드포인트: ").append(clusterEndpoints.size()).append("개\n");
            sb.append("- 관련 파일/테이블: ").append(files.size()).append("개\n\n```\n");
            List<String> sample = files.stream().sorted().limit(15).toList();
            sample.forEach(f -> sb.append(f).append("\n"));
            if (files.size() > sample.size()) sb.append("... 외 ").append(files.size() - sample.size()).append("개\n");
            sb.append("```\n\n");
        }

        if (!shared.isEmpty()) {
            sb.append("### 여러 후보 모듈이 공유하는 파일/테이블\n\n");
            sb.append("> 대부분의 엔드포인트가 함께 사용해 특정 모듈로 나누지 않았습니다 — 공유 커널로 남기거나, 실제로 여러 도메인이 얽혀 있다는 신호일 수 있습니다.\n\n```\n");
            shared.stream().sorted().limit(20).forEach(f -> sb.append(f).append("\n"));
            sb.append("```\n\n");
        }

        return sb.toString();
    }

    // 클러스터 내 엔드포인트 경로들에서 가장 흔한 의미있는 세그먼트로 모듈명 추정 — 실패하면 순번만 표시
    private String suggestName(List<Node> clusterEndpoints) {
        Map<String, Integer> segCount = new HashMap<>();
        for (Node ep : clusterEndpoints) {
            String path = ep.getName();
            if (path == null) continue;
            String p = path.contains(":") ? path.substring(path.indexOf(':') + 1) : path;
            for (String seg : p.split("/")) {
                if (seg.isBlank() || seg.startsWith("{") || seg.equalsIgnoreCase("api") || seg.matches("v\\d+")) continue;
                segCount.merge(seg, 1, Integer::sum);
            }
        }
        return segCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String normalize(String path) {
        return path == null ? "" : path.replace("\\", "/");
    }
}
