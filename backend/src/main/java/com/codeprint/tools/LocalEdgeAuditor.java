// 그래프 엣지를 타입별로 표본추출해 phantom(오귀속) 여부를 사람이 판정할 수 있는 JSON으로 출력하는 CLI 도구
package com.codeprint.tools;

import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.EdgeType;
import com.codeprint.domain.graph.Node;
import com.codeprint.infrastructure.analysis.CachedParsedFileLoader;
import com.codeprint.infrastructure.analysis.StaticCodeAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class LocalEdgeAuditor {

    // 소스 파일에서 노드 정의 위치부터 잘라 보여줄 줄 수 — 과거 수동 감사(DECISIONS_ANALYSIS.md 1~3차)의
    // "함수 본문 추출 40줄 제한" 관례와 동일하게 맞춤(사람이 호출 지점을 눈으로 찾기에 충분한 범위)
    private static final int SNIPPET_LINES = 40;

    // rootDir을 분석해 엣지 타입별 고정 시드 표본을 build/codeprint-local/edge-audit-<repoLabel>.json에 기록
    public static void main(String[] args) throws Exception {
        Path rootDir = args.length > 0 && !args[0].isBlank() ? Path.of(args[0]) : Path.of(".");
        String repoLabel = args.length > 1 && !args[1].isBlank() ? args[1] : "self";
        long seed = args.length > 2 && !args[2].isBlank() ? Long.parseLong(args[2]) : 42L;
        int sampleSize = args.length > 3 && !args[3].isBlank() ? Integer.parseInt(args[3]) : 30;

        System.out.println("감사 대상: " + rootDir.toAbsolutePath()
                + " (repoLabel=" + repoLabel + ", seed=" + seed + ", 타입별 표본=" + sampleSize + ")");

        UUID projectId = UUID.randomUUID();
        CachedParsedFileLoader loader = new CachedParsedFileLoader(new StaticCodeAnalyzer(), new InMemoryParsedFileCachePort());
        LocalAnalyzer.GraphResult result = LocalAnalyzer.buildGraph(rootDir, projectId, loader);

        Map<UUID, Node> nodesById = result.nodes().stream().collect(Collectors.toMap(Node::getId, n -> n));
        Map<EdgeType, List<Edge>> edgesByType = result.edges().stream().collect(Collectors.groupingBy(Edge::getType));

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("repoLabel", repoLabel);
        root.put("rootDir", rootDir.toAbsolutePath().toString());
        root.put("seed", seed);
        ArrayNode samples = root.putArray("samples");

        for (EdgeType type : EdgeType.values()) {
            List<Edge> edges = edgesByType.get(type);
            if (edges == null || edges.isEmpty()) continue;
            samples.addAll(sampleType(mapper, type, edges, nodesById, rootDir, seed, sampleSize));
        }

        Path outDir = Path.of("build", "codeprint-local");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("edge-audit-" + repoLabel + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(outFile.toFile(), root);
        System.out.println("표본 " + samples.size() + "건 기록: " + outFile.toAbsolutePath());
    }

    // 한 엣지 타입에서 고정 시드로 표본을 뽑아 JSON 배열로 변환 — edgeIdentifier 정렬 후 셔플로 실행마다 동일 표본 보장
    private static List<ObjectNode> sampleType(ObjectMapper mapper, EdgeType type, List<Edge> edges,
                                                Map<UUID, Node> nodesById, Path rootDir, long seed, int sampleSize) {
        List<Edge> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparing(Edge::getEdgeIdentifier));
        Collections.shuffle(sorted, new Random(seed));
        int n = Math.min(sampleSize, sorted.size());
        List<ObjectNode> result = new ArrayList<>(n);
        for (Edge edge : sorted.subList(0, n)) {
            ObjectNode json = mapper.createObjectNode();
            json.put("edgeType", type.name());
            json.put("edgeIdentifier", edge.getEdgeIdentifier());
            json.set("source", describeNode(mapper, nodesById.get(edge.getSourceNodeId()), rootDir));
            json.set("target", describeNode(mapper, nodesById.get(edge.getTargetNodeId()), rootDir));
            json.putNull("verdict"); // 분류 전 — 사람/Claude가 "real" 또는 "phantom"으로 채워 test resources에 커밋
            result.add(json);
        }
        return result;
    }

    // 노드 하나를 이름·파일·줄·언어·소스 스니펫으로 직렬화 — 소스를 다시 열지 않고도 판정 가능하게
    private static ObjectNode describeNode(ObjectMapper mapper, Node node, Path rootDir) {
        ObjectNode json = mapper.createObjectNode();
        if (node == null) {
            json.put("error", "노드 조회 실패(그래프 정합성 문제)");
            return json;
        }
        json.put("name", node.getName());
        json.put("filePath", node.getFilePath());
        json.put("language", node.getLanguage());
        Integer line = extractLine(node);
        if (line != null) json.put("line", line);
        String snippet = extractSnippet(rootDir, node.getFilePath(), line);
        if (snippet != null) json.put("snippet", snippet);
        return json;
    }

    // FUNCTION 노드 메타데이터의 정의 시작 줄 — Java/TS/JS만 존재(GraphBuilder 참조), 그 외 언어는 null
    private static Integer extractLine(Node node) {
        Map<String, Object> metadata = node.getMetadata();
        if (metadata == null) return null;
        Object line = metadata.get("line");
        return line instanceof Number number ? number.intValue() : null;
    }

    // rootDir 기준 상대경로에서 line부터 최대 SNIPPET_LINES줄 추출 — line 없으면 파일 앞부분
    private static String extractSnippet(Path rootDir, String filePath, Integer line) {
        if (filePath == null) return null;
        try {
            Path file = rootDir.resolve(filePath);
            if (!Files.isRegularFile(file)) return null;
            List<String> lines = Files.readAllLines(file);
            int start = line != null ? Math.max(0, line - 1) : 0;
            int end = Math.min(lines.size(), start + SNIPPET_LINES);
            return String.join("\n", lines.subList(start, end));
        } catch (Exception e) {
            return null;
        }
    }
}
