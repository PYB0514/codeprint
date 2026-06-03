// 정적 분석 결과를 Graph/Node/Edge 엔티티로 변환하여 저장하는 빌더
package com.codeprint.infrastructure.analysis;

import com.codeprint.domain.graph.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

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
        // 함수명 → 노드ID (파일 경로 포함: "filePath::funcName" → nodeId)
        Map<String, UUID> funcNodeIds = new HashMap<>();
        Set<String> usedContainsEdgeIds = new HashSet<>();

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

            // FUNCTION 노드 생성 — 생성자는 "생성자"로 표시
            for (String funcName : pf.functions()) {
                Node funcNode = Node.create(graphId, NodeType.FUNCTION,
                        funcName, pf.filePath(), pf.language());

                Map<String, Object> meta = new HashMap<>();
                meta.put("parentFile", pf.filePath());
                String comment = funcName.equals(className)
                        ? "생성자"
                        : pf.functionComments().get(funcName);
                if (comment != null) meta.put("comment", comment);
                funcNode.updateMetadata(meta);
                graphRepository.saveNode(funcNode);
                funcNodeIds.put(pf.filePath() + "::" + funcName, funcNode.getId());

                // FILE → FUNCTION 포함 관계 엣지 (동일 식별자 중복 방지)
                String edgeId = extractFileName(pf.filePath()) + "-" + funcName;
                if (usedContainsEdgeIds.contains(edgeId)) continue;
                usedContainsEdgeIds.add(edgeId);
                Edge containsEdge = Edge.create(graphId, edgeId, EdgeType.CONTAINS,
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

        // 파일 간 FUNCTION_CALL 엣지 생성
        // 각 함수의 호출 목록에서 다른 파일의 함수를 호출하는 경우에만 엣지 생성
        Set<String> usedEdgeIds = new HashSet<>();
        for (ParsedFile callerFile : parsedFiles) {
            for (Map.Entry<String, List<String>> entry : callerFile.functionCalls().entrySet()) {
                String callerFunc = entry.getKey();
                UUID callerFuncId = funcNodeIds.get(callerFile.filePath() + "::" + callerFunc);
                if (callerFuncId == null) continue;

                for (String calleeFunc : entry.getValue()) {
                    // 같은 파일 내 호출은 제외 — 다른 파일의 함수를 찾는다
                    for (ParsedFile calleeFile : parsedFiles) {
                        if (calleeFile.filePath().equals(callerFile.filePath())) continue;
                        if (!calleeFile.functions().contains(calleeFunc)) continue;

                        UUID calleeFuncId = funcNodeIds.get(calleeFile.filePath() + "::" + calleeFunc);
                        if (calleeFuncId == null) continue;

                        String edgeIdentifier = extractFileName(callerFile.filePath()) + "-" + callerFunc + "-calls-" + calleeFunc;
                        if (usedEdgeIds.contains(edgeIdentifier)) continue;
                        usedEdgeIds.add(edgeIdentifier);

                        Edge callEdge = Edge.create(graphId, edgeIdentifier, EdgeType.FUNCTION_CALL,
                                callerFuncId, calleeFuncId);
                        Map<String, Object> meta = new HashMap<>();
                        meta.put("callerFile", callerFile.filePath());
                        meta.put("calleeFile", calleeFile.filePath());
                        callEdge.updateMetadata(meta);
                        graphRepository.saveEdge(callEdge);
                        break; // 첫 번째 매칭 파일만 사용
                    }
                }
            }
        }

        // 파일 간 INSTANTIATION 엣지 생성 — new ClassName() 패턴으로 인스턴스화된 클래스의 파일과 연결
        // 클래스명(확장자 제거 파일명) → 파일 노드 ID 인덱스
        Map<String, UUID> classNameToFileId = new HashMap<>();
        for (Map.Entry<String, UUID> entry : fileNodeIds.entrySet()) {
            String fileName = extractFileName(entry.getKey());
            String className = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            classNameToFileId.put(className, entry.getValue());
        }

        Set<String> usedInstEdgeIds = new HashSet<>();
        for (ParsedFile pf : parsedFiles) {
            UUID sourceFileId = fileNodeIds.get(pf.filePath());
            if (sourceFileId == null) continue;

            for (String className : pf.instantiatedClasses()) {
                UUID targetFileId = classNameToFileId.get(className);
                if (targetFileId == null || targetFileId.equals(sourceFileId)) continue;

                String edgeIdentifier = extractFileName(pf.filePath()) + "-new-" + className;
                if (usedInstEdgeIds.contains(edgeIdentifier)) continue;
                usedInstEdgeIds.add(edgeIdentifier);

                Edge instEdge = Edge.create(graphId, edgeIdentifier, EdgeType.INSTANTIATION,
                        sourceFileId, targetFileId);
                graphRepository.saveEdge(instEdge);
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
