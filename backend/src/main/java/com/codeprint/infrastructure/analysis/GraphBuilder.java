// 정적 분석 결과를 Graph/Node/Edge 엔티티로 변환하여 저장하는 빌더
package com.codeprint.infrastructure.analysis;

import com.codeprint.domain.graph.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GraphBuilder {

    private final GraphRepository graphRepository;

    // 분석된 파일 목록으로 그래프와 노드/엣지를 생성하여 저장
    public Graph build(UUID projectId, UUID analysisId, List<ParsedFile> parsedFiles) {
        Graph graph = Graph.create(projectId, analysisId);
        graphRepository.save(graph);

        UUID graphId = graph.getId();
        Map<String, UUID> fileNodeIds = new HashMap<>();

        // FILE 노드 생성
        for (ParsedFile pf : parsedFiles) {
            Node fileNode = Node.create(graphId, NodeType.FILE,
                    extractFileName(pf.filePath()), pf.filePath(), pf.language());

            if (pf.fileComment() != null) {
                fileNode.updateMetadata(Map.of("comment", pf.fileComment()));
            }
            graphRepository.saveNode(fileNode);
            fileNodeIds.put(pf.filePath(), fileNode.getId());

            // 클래스명(파일명 확장자 제거) — 생성자 필터링용
            String className = extractFileName(pf.filePath());
            int dot = className.lastIndexOf('.');
            if (dot > 0) className = className.substring(0, dot);

            // FUNCTION 노드 생성 (생성자 제외)
            for (String funcName : pf.functions()) {
                if (funcName.equals(className)) continue;
                Node funcNode = Node.create(graphId, NodeType.FUNCTION,
                        funcName, pf.filePath(), pf.language());

                Map<String, Object> meta = new HashMap<>();
                meta.put("parentFile", pf.filePath());
                String comment = pf.functionComments().get(funcName);
                if (comment != null) meta.put("comment", comment);
                funcNode.updateMetadata(meta);
                graphRepository.saveNode(funcNode);

                // FILE → FUNCTION 포함 관계 엣지
                String edgeId = extractFileName(pf.filePath()) + "-" + funcName;
                Edge containsEdge = Edge.create(graphId, edgeId, EdgeType.IMPORT,
                        fileNodeIds.get(pf.filePath()), funcNode.getId());
                graphRepository.saveEdge(containsEdge);
            }
        }

        // 파일 간 IMPORT 엣지 생성
        for (ParsedFile pf : parsedFiles) {
            UUID sourceFileId = fileNodeIds.get(pf.filePath());
            if (sourceFileId == null) continue;

            for (String importPath : pf.imports()) {
                fileNodeIds.entrySet().stream()
                        .filter(e -> isImportMatch(importPath, e.getKey()))
                        .findFirst()
                        .ifPresent(e -> {
                            String edgeIdentifier = extractFileName(pf.filePath()) + "-imports-" + extractFileName(e.getKey());
                            Edge importEdge = Edge.create(graphId, edgeIdentifier, EdgeType.IMPORT,
                                    sourceFileId, e.getValue());
                            graphRepository.saveEdge(importEdge);
                        });
            }
        }

        return graph;
    }

    // 파일 경로에서 파일명만 추출
    private String extractFileName(String filePath) {
        int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return slash >= 0 ? filePath.substring(slash + 1) : filePath;
    }

    // import 경로가 실제 파일 경로와 일치하는지 확인
    private boolean isImportMatch(String importPath, String filePath) {
        String normalizedImport = importPath.replace(".", "/").replace("\\", "/");
        String normalizedFile = filePath.replace("\\", "/");
        String fileWithoutExt = normalizedFile.contains(".")
                ? normalizedFile.substring(0, normalizedFile.lastIndexOf('.'))
                : normalizedFile;
        return fileWithoutExt.endsWith(normalizedImport) ||
               normalizedFile.endsWith(importPath.replace(".", "/") + ".java") ||
               normalizedFile.endsWith(importPath.replace(".", "/") + ".kt");
    }
}
