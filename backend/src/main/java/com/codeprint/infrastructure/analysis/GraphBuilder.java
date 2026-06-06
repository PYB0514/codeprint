// 정적 분석 결과를 Graph/Node/Edge 엔티티로 변환하여 저장하는 빌더
package com.codeprint.infrastructure.analysis;

import com.codeprint.domain.graph.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

        // 인터페이스 → 구현체 FUNCTION_CALL 엣지 생성
        // 인터페이스 심플명 → 구현체 ParsedFile 목록 (구현체 여러 개 지원)
        Map<String, List<ParsedFile>> interfaceToImplFiles = new HashMap<>();
        for (ParsedFile pf : parsedFiles) {
            for (String iface : pf.implementedInterfaces()) {
                interfaceToImplFiles.computeIfAbsent(iface, k -> new ArrayList<>()).add(pf);
            }
        }
        // 인터페이스 심플명 → 인터페이스 ParsedFile 인덱스
        Map<String, ParsedFile> ifaceNameToFile = new HashMap<>();
        for (ParsedFile pf : parsedFiles) {
            String simpleName = extractFileNameWithoutExt(pf.filePath());
            if (interfaceToImplFiles.containsKey(simpleName)) {
                ifaceNameToFile.put(simpleName, pf);
            }
        }
        for (Map.Entry<String, ParsedFile> entry : ifaceNameToFile.entrySet()) {
            String ifaceName = entry.getKey();
            ParsedFile ifaceFile = entry.getValue();
            List<ParsedFile> implFiles = interfaceToImplFiles.get(ifaceName);
            if (implFiles == null) continue;
            for (ParsedFile implFile : implFiles) {
                for (String funcName : ifaceFile.functions()) {
                    UUID ifaceFuncId = funcNodeIds.get(ifaceFile.filePath() + "::" + funcName);
                    UUID implFuncId = funcNodeIds.get(implFile.filePath() + "::" + funcName);
                    if (ifaceFuncId == null || implFuncId == null) continue;
                    String edgeId = ifaceName + "-" + funcName + "-impl-" + extractFileNameWithoutExt(implFile.filePath());
                    if (usedEdgeIds.contains(edgeId)) continue;
                    usedEdgeIds.add(edgeId);
                    Edge implEdge = Edge.create(graphId, edgeId, EdgeType.FUNCTION_CALL, ifaceFuncId, implFuncId);
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("callerFile", ifaceFile.filePath());
                    meta.put("calleeFile", implFile.filePath());
                    meta.put("isInterfaceImpl", true);
                    implEdge.updateMetadata(meta);
                    graphRepository.saveEdge(implEdge);
                }
            }
        }

        // DB_TABLE 노드 생성 + Repository → DB_TABLE 엣지 생성
        // 엔티티 클래스명 → DB_TABLE 노드 ID 인덱스
        Map<String, UUID> entityClassToTableNodeId = new HashMap<>();
        // 엔티티 클래스명 → 칼럼 목록 인덱스 (DB_TABLE 노드 메타데이터용)
        Map<String, List<ColumnInfo>> entityClassToColumns = new HashMap<>();

        for (ParsedFile pf : parsedFiles) {
            // @Entity 파일의 칼럼 정보를 엔티티 클래스명으로 인덱싱
            if (!pf.entityColumns().isEmpty()) {
                String className = extractFileNameWithoutExt(pf.filePath());
                entityClassToColumns.put(className, pf.entityColumns());
            }
        }

        for (ParsedFile pf : parsedFiles) {
            for (DbTableInfo table : pf.dbTables()) {
                Node tableNode = Node.create(graphId, NodeType.DB_TABLE, table.tableName(), pf.filePath(), pf.language());
                Map<String, Object> tableMeta = new HashMap<>();
                tableMeta.put("entityClass", table.className());
                List<ColumnInfo> cols = entityClassToColumns.get(table.className());
                if (cols != null && !cols.isEmpty()) {
                    // 칼럼을 Map 목록으로 직렬화하여 JSONB에 저장
                    List<Map<String, String>> colData = new ArrayList<>();
                    for (ColumnInfo col : cols) {
                        Map<String, String> c = new HashMap<>();
                        c.put("fieldName", col.fieldName());
                        c.put("columnName", col.columnName());
                        c.put("javaType", col.javaType());
                        colData.add(c);
                    }
                    tableMeta.put("columns", colData);
                }
                tableNode.updateMetadata(tableMeta);
                graphRepository.saveNode(tableNode);
                entityClassToTableNodeId.put(table.className(), tableNode.getId());
            }
        }

        // Repository 파일 → DB_TABLE 엣지 (CRUD 타입별 분류)
        Set<String> usedDbEdgeIds = new HashSet<>();
        for (ParsedFile pf : parsedFiles) {
            if (pf.repositoryEntityClass() == null) continue;
            UUID repoFileId = fileNodeIds.get(pf.filePath());
            UUID tableNodeId = entityClassToTableNodeId.get(pf.repositoryEntityClass());
            if (repoFileId == null || tableNodeId == null) continue;

            Set<EdgeType> crudTypes = detectCrudTypes(pf.functions());
            String fileBase = extractFileName(pf.filePath());
            for (EdgeType crudType : crudTypes) {
                String edgeId = fileBase + "-" + crudType.name().toLowerCase() + "-" + pf.repositoryEntityClass();
                if (!usedDbEdgeIds.contains(edgeId)) {
                    usedDbEdgeIds.add(edgeId);
                    graphRepository.saveEdge(Edge.create(graphId, edgeId, crudType, repoFileId, tableNodeId));
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

        // 프론트엔드 axios 호출 → 백엔드 컨트롤러 API_CALL 엣지 생성
        // 컨트롤러 경로 정규화 인덱스: {pathVar} → * 글로브 패턴 → fileNodeId
        Map<String, UUID> controllerGlobIndex = new java.util.LinkedHashMap<>();
        for (ParsedFile pf : parsedFiles) {
            if (pf.controllerMappings().isEmpty()) continue;
            UUID fileId = fileNodeIds.get(pf.filePath());
            if (fileId == null) continue;
            for (String mapping : pf.controllerMappings()) {
                String glob = mapping.replaceAll("\\{[^}]+}", "*");
                controllerGlobIndex.put(glob, fileId);
            }
        }

        Set<String> usedApiCallEdgeIds = new HashSet<>();
        for (ParsedFile pf : parsedFiles) {
            if (pf.apiCalls().isEmpty()) continue;
            UUID frontFileId = fileNodeIds.get(pf.filePath());
            if (frontFileId == null) continue;

            for (String apiCall : pf.apiCalls()) {
                // "METHOD:/path" 형식에서 경로만 추출 (이미 extractApiCalls에서 ${...} → * 처리됨)
                int colon = apiCall.indexOf(':');
                if (colon < 0) continue;
                String frontPath = apiCall.substring(colon + 1);
                // 쿼리스트링 제거
                int q = frontPath.indexOf('?');
                if (q >= 0) frontPath = frontPath.substring(0, q);

                UUID controllerFileId = null;
                for (Map.Entry<String, UUID> entry : controllerGlobIndex.entrySet()) {
                    if (globPathMatches(entry.getKey(), frontPath)) {
                        controllerFileId = entry.getValue();
                        break;
                    }
                }
                if (controllerFileId == null || controllerFileId.equals(frontFileId)) continue;

                UUID finalControllerFileId = controllerFileId;
                String targetFileName = parsedFiles.stream()
                        .filter(f -> finalControllerFileId.equals(fileNodeIds.get(f.filePath())))
                        .map(f -> extractFileName(f.filePath()))
                        .findFirst().orElse("unknown");
                String edgeId = extractFileName(pf.filePath()) + "-apicall-" + targetFileName;
                if (!usedApiCallEdgeIds.contains(edgeId)) {
                    usedApiCallEdgeIds.add(edgeId);
                    graphRepository.saveEdge(Edge.create(graphId, edgeId, EdgeType.API_CALL, frontFileId, controllerFileId));
                }
            }
        }

        return graph;
    }

    // Repository 메서드명 목록에서 수행하는 CRUD 타입 집합 반환
    private Set<EdgeType> detectCrudTypes(List<String> methods) {
        Set<EdgeType> types = new java.util.LinkedHashSet<>();
        for (String method : methods) {
            String m = method.toLowerCase();
            if (m.startsWith("find") || m.startsWith("get") || m.startsWith("count")
                    || m.startsWith("exists") || m.startsWith("load") || m.startsWith("fetch")
                    || m.startsWith("read") || m.startsWith("list") || m.startsWith("search")) {
                types.add(EdgeType.DB_READ);
            } else if (m.startsWith("save") || m.startsWith("create") || m.startsWith("insert")
                    || m.startsWith("add") || m.startsWith("persist")) {
                types.add(EdgeType.DB_CREATE);
            } else if (m.startsWith("update") || m.startsWith("modify") || m.startsWith("edit")
                    || m.startsWith("change") || m.startsWith("set") || m.startsWith("patch")) {
                types.add(EdgeType.DB_UPDATE);
            } else if (m.startsWith("delete") || m.startsWith("remove") || m.startsWith("purge")
                    || m.startsWith("clear") || m.startsWith("drop")) {
                types.add(EdgeType.DB_DELETE);
            }
        }
        // 메서드가 없거나 분류 불가 시 기본 READ+WRITE로 폴백
        if (types.isEmpty()) {
            types.add(EdgeType.DB_READ);
            types.add(EdgeType.DB_WRITE);
        }
        return types;
    }

    // 글로브 패턴(* = 단일 세그먼트)과 경로 일치 여부 확인
    private boolean globPathMatches(String pattern, String path) {
        String[] pp = pattern.split("/");
        String[] ip = path.split("/");
        if (pp.length != ip.length) return false;
        for (int i = 0; i < pp.length; i++) {
            if (!pp[i].equals("*") && !pp[i].equals(ip[i])) return false;
        }
        return true;
    }

    // 파일 경로에서 파일명만 추출
    private String extractFileName(String filePath) {
        int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return slash >= 0 ? filePath.substring(slash + 1) : filePath;
    }

    // 파일 경로에서 확장자 제거 후 파일명 추출
    private String extractFileNameWithoutExt(String filePath) {
        String name = extractFileName(filePath);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
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
