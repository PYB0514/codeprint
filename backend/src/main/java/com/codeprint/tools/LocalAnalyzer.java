// DB 없이 로컬 소스 디렉터리를 분석하여 GraphWarning을 출력하는 CLI 도구
package com.codeprint.tools;

import com.codeprint.application.graph.GraphWarningService;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.EdgeType;
import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import com.codeprint.infrastructure.analysis.ColumnInfo;
import com.codeprint.infrastructure.analysis.DbTableInfo;
import com.codeprint.infrastructure.analysis.LanguageDetector;
import com.codeprint.infrastructure.analysis.ParsedFile;
import com.codeprint.infrastructure.analysis.SourceFileWalker;
import com.codeprint.infrastructure.analysis.StaticCodeAnalyzer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LocalAnalyzer {

    // 지정 디렉터리를 분석하여 워닝 목록을 stdout에 출력
    public static void main(String[] args) throws Exception {
        Path rootDir = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        System.out.println("분석 대상: " + rootDir.toAbsolutePath());

        SourceFileWalker walker = new SourceFileWalker();
        StaticCodeAnalyzer analyzer = new StaticCodeAnalyzer();

        List<Path> files = walker.walk(rootDir);
        System.out.println("소스 파일 수: " + files.size());

        UUID graphId = UUID.randomUUID();
        List<ParsedFile> parsedFiles = new ArrayList<>();
        for (Path file : files) {
            String lang = LanguageDetector.detect(file.getFileName().toString()).orElse("unknown");
            try {
                parsedFiles.add(analyzer.analyze(file, rootDir, lang));
            } catch (Exception e) {
                System.err.println("분석 실패 (무시): " + file + " — " + e.getMessage());
            }
        }
        System.out.println("파싱 완료: " + parsedFiles.size() + " 파일");

        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        buildGraph(graphId, parsedFiles, nodes, edges);
        System.out.println("노드: " + nodes.size() + ", 엣지: " + edges.size());

        GraphWarningService warningService = new GraphWarningService();
        List<Map<String, Object>> warnings = warningService.detect(nodes, edges);

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

    // ParsedFile 목록으로 인메모리 Node/Edge 그래프 생성
    private static void buildGraph(UUID graphId, List<ParsedFile> parsedFiles,
                                   List<Node> nodes, List<Edge> edges) {
        Map<String, UUID> fileNodeIds = new HashMap<>();
        Map<String, UUID> funcNodeIds = new HashMap<>();
        Set<String> usedEdgeIds = new HashSet<>();

        // FILE 노드 + FUNCTION 노드 생성
        for (ParsedFile pf : parsedFiles) {
            Node fileNode = Node.create(graphId, NodeType.FILE, extractFileName(pf.filePath()), pf.filePath(), pf.language());
            if (pf.fileComment() != null) {
                fileNode.updateMetadata(Map.of("comment", pf.fileComment()));
            }
            nodes.add(fileNode);
            fileNodeIds.put(pf.filePath(), fileNode.getId());

            for (String funcName : pf.functions()) {
                Node funcNode = Node.create(graphId, NodeType.FUNCTION, funcName, pf.filePath(), pf.language());
                Map<String, Object> meta = new HashMap<>();
                meta.put("parentFile", pf.filePath());
                if (pf.functionComments() != null) {
                    String comment = pf.functionComments().get(funcName);
                    if (comment != null) meta.put("comment", comment);
                }
                if (pf.asyncMethods() != null && pf.asyncMethods().contains(funcName)) {
                    meta.put("isAsync", true);
                }
                funcNode.updateMetadata(meta);
                nodes.add(funcNode);
                funcNodeIds.put(pf.filePath() + "::" + funcName, funcNode.getId());

                String edgeId = extractFileName(pf.filePath()) + "-" + funcName;
                if (usedEdgeIds.add(edgeId)) {
                    edges.add(Edge.create(graphId, edgeId, EdgeType.CONTAINS, fileNode.getId(), funcNode.getId()));
                }
            }
        }

        // DB_TABLE 노드
        Map<String, UUID> entityClassToTableNodeId = new HashMap<>();
        for (ParsedFile pf : parsedFiles) {
            for (DbTableInfo table : pf.dbTables()) {
                Node tableNode = Node.create(graphId, NodeType.DB_TABLE, table.tableName(), pf.filePath(), pf.language());
                Map<String, Object> tableMeta = new HashMap<>();
                tableMeta.put("entityClass", table.className());

                // 컬럼 메타 + hasConverter 플래그
                List<ColumnInfo> cols = pf.entityColumns();
                if (cols != null && !cols.isEmpty()) {
                    List<Map<String, String>> colData = new ArrayList<>();
                    boolean anyConverter = false;
                    for (ColumnInfo col : cols) {
                        Map<String, String> c = new HashMap<>();
                        c.put("fieldName", col.fieldName());
                        c.put("columnName", col.columnName());
                        c.put("javaType", col.javaType());
                        if (col.hasConverter()) {
                            c.put("hasConverter", "true");
                            anyConverter = true;
                        }
                        colData.add(c);
                    }
                    tableMeta.put("columns", colData);
                    if (anyConverter) tableMeta.put("hasConverter", true);
                }
                tableNode.updateMetadata(tableMeta);
                nodes.add(tableNode);
                entityClassToTableNodeId.put(table.className(), tableNode.getId());
            }
        }

        // IMPORT 엣지
        for (ParsedFile pf : parsedFiles) {
            UUID sourceFileId = fileNodeIds.get(pf.filePath());
            if (sourceFileId == null) continue;
            for (String importPath : pf.imports()) {
                fileNodeIds.entrySet().stream()
                        .filter(e -> isImportMatch(importPath, e.getKey()))
                        .findFirst()
                        .ifPresent(e -> {
                            String edgeId = extractFileName(pf.filePath()) + "-imports-" + extractFileName(e.getKey());
                            if (usedEdgeIds.add(edgeId)) {
                                edges.add(Edge.create(graphId, edgeId, EdgeType.IMPORT, sourceFileId, e.getValue()));
                            }
                        });
            }
        }

        // FUNCTION_CALL 엣지 (같은 파일 내 함수 호출 포함)
        Map<String, List<ParsedFile>> interfaceToImplFiles = new HashMap<>();
        for (ParsedFile pf : parsedFiles) {
            for (String iface : pf.implementedInterfaces()) {
                interfaceToImplFiles.computeIfAbsent(iface, k -> new ArrayList<>()).add(pf);
            }
        }

        for (ParsedFile pf : parsedFiles) {
            for (Map.Entry<String, List<String>> entry : pf.functionCalls().entrySet()) {
                String callerFunc = entry.getKey();
                UUID callerNodeId = funcNodeIds.get(pf.filePath() + "::" + callerFunc);
                if (callerNodeId == null) continue;

                for (String callee : entry.getValue()) {
                    UUID calleeNodeId = null;
                    // 같은 파일 내 함수 우선
                    calleeNodeId = funcNodeIds.get(pf.filePath() + "::" + callee);
                    if (calleeNodeId == null) {
                        // 다른 파일에서 검색
                        for (Map.Entry<String, UUID> fn : funcNodeIds.entrySet()) {
                            if (fn.getKey().endsWith("::" + callee)) {
                                calleeNodeId = fn.getValue();
                                break;
                            }
                        }
                    }
                    if (calleeNodeId == null || calleeNodeId.equals(callerNodeId)) continue;
                    String edgeId = callerFunc + "-calls-" + callee + "-" + pf.filePath().hashCode();
                    if (usedEdgeIds.add(edgeId)) {
                        edges.add(Edge.create(graphId, edgeId, EdgeType.FUNCTION_CALL, callerNodeId, calleeNodeId));
                    }
                }
            }
        }
    }

    private static String extractFileName(String filePath) {
        int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return slash >= 0 ? filePath.substring(slash + 1) : filePath;
    }

    private static boolean isImportMatch(String importPath, String filePath) {
        String normalizedImport = importPath.replace(".", "/");
        String normalizedFile = filePath.replace("\\", "/");
        String fileWithoutExt = normalizedFile.contains(".")
                ? normalizedFile.substring(0, normalizedFile.lastIndexOf('.'))
                : normalizedFile;
        return fileWithoutExt.endsWith(normalizedImport)
                || normalizedFile.endsWith(importPath.replace(".", "/") + ".java")
                || normalizedFile.endsWith(importPath.replace(".", "/") + ".kt");
    }
}
