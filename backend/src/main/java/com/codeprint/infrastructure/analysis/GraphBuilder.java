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
        return build(projectId, analysisId, parsedFiles, parsedFiles.size());
    }

    // 전체 대상 파일 수 포함 빌드 — MAX_FILES 절단 시 totalFileCount가 분석 파일 수보다 큼
    public Graph build(UUID projectId, UUID analysisId, List<ParsedFile> parsedFiles, int totalFileCount) {
        Graph graph = Graph.create(projectId, analysisId);
        graph.recordFileCounts(parsedFiles.size(), totalFileCount);
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
                if (pf.asyncMethods() != null && pf.asyncMethods().contains(funcName)) {
                    meta.put("isAsync", true);
                }
                if (pf.frameworkAnnotatedMethods() != null && pf.frameworkAnnotatedMethods().contains(funcName)) {
                    meta.put("isFrameworkAnnotated", true);
                }
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

        // 인터페이스 심플명 → 구현체 ParsedFile 목록 (FUNCTION_CALL 매칭 전에 먼저 빌드)
        // FUNCTION_CALL 매칭 시 인터페이스보다 구현체를 우선 선택하기 위해 사용
        Map<String, List<ParsedFile>> interfaceToImplFiles = new HashMap<>();
        for (ParsedFile pf : parsedFiles) {
            for (String iface : pf.implementedInterfaces()) {
                interfaceToImplFiles.computeIfAbsent(iface, k -> new ArrayList<>()).add(pf);
            }
        }

        // 파일 간 FUNCTION_CALL 엣지 생성 — 구현체가 있으면 인터페이스보다 구현체로 연결
        Set<String> usedEdgeIds = new HashSet<>();
        for (ParsedFile callerFile : parsedFiles) {
            for (Map.Entry<String, List<String>> entry : callerFile.functionCalls().entrySet()) {
                String callerFunc = entry.getKey();
                UUID callerFuncId = funcNodeIds.get(callerFile.filePath() + "::" + callerFunc);
                if (callerFuncId == null) continue;

                for (String calleeEntry : entry.getValue()) {
                    // "ClassName::method" 형식이면 클래스명으로 파일 직접 매칭
                    String calleeFunc;
                    String targetClass = null;
                    if (calleeEntry.contains("::")) {
                        String[] parts = calleeEntry.split("::", 2);
                        targetClass = parts[0];
                        calleeFunc = parts[1];
                    } else {
                        calleeFunc = calleeEntry;
                    }

                    // 후보 callee 파일 중 구현체를 인터페이스보다 우선 선택
                    ParsedFile bestMatch = null;
                    boolean bestIsInterface = false;
                    for (ParsedFile calleeFile : parsedFiles) {
                        if (calleeFile.filePath().equals(callerFile.filePath())) continue;
                        if (!calleeFile.functions().contains(calleeFunc)) continue;
                        String calleeClassName = extractFileNameWithoutExt(calleeFile.filePath());
                        // 클래스명이 명시된 경우: 정확히 일치하는 파일 우선 선택
                        if (targetClass != null && calleeClassName.equals(targetClass)) {
                            bestMatch = calleeFile;
                            break;
                        }
                        if (targetClass != null) continue; // 클래스명 불일치 → 건너뜀
                        boolean calleeIsInterface = interfaceToImplFiles.containsKey(calleeClassName);
                        if (bestMatch == null) {
                            bestMatch = calleeFile;
                            bestIsInterface = calleeIsInterface;
                        } else if (bestIsInterface && !calleeIsInterface) {
                            // 구현체로 업그레이드
                            bestMatch = calleeFile;
                            bestIsInterface = false;
                        }
                    }
                    if (bestMatch == null) continue;

                    UUID calleeFuncId = funcNodeIds.get(bestMatch.filePath() + "::" + calleeFunc);
                    if (calleeFuncId == null) continue;

                    String edgeIdentifier = extractFileName(callerFile.filePath()) + "-" + callerFunc + "-calls-" + extractFileName(bestMatch.filePath()) + "-" + calleeFunc;
                    if (usedEdgeIds.contains(edgeIdentifier)) continue;
                    usedEdgeIds.add(edgeIdentifier);

                    Edge callEdge = Edge.create(graphId, edgeIdentifier, EdgeType.FUNCTION_CALL,
                            callerFuncId, calleeFuncId);
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("callerFile", callerFile.filePath());
                    meta.put("calleeFile", bestMatch.filePath());
                    callEdge.updateMetadata(meta);
                    graphRepository.saveEdge(callEdge);
                }
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

        // JSX 컴포넌트 사용 → FUNCTION_CALL 엣지 생성
        // import 경로에서 컴포넌트 파일을 찾아 FILE→FILE FUNCTION_CALL 엣지로 연결
        Map<String, ParsedFile> fileNameToFile = new HashMap<>();
        for (ParsedFile pf : parsedFiles) {
            String name = extractFileNameWithoutExt(pf.filePath());
            fileNameToFile.put(name, pf);
        }
        for (ParsedFile callerFile : parsedFiles) {
            if (callerFile.jsxComponents().isEmpty()) continue;
            UUID callerFileId = fileNodeIds.get(callerFile.filePath());
            if (callerFileId == null) continue;
            for (String componentName : callerFile.jsxComponents()) {
                // import 경로 중 컴포넌트명으로 끝나는 항목 탐색
                String importedPath = null;
                for (String imp : callerFile.imports()) {
                    String importedName = imp.substring(imp.lastIndexOf('/') + 1);
                    // "import './ComponentName'" 또는 "../ComponentName" 형식
                    if (importedName.equals(componentName) || importedName.startsWith(componentName + ".")) {
                        importedPath = imp;
                        break;
                    }
                }
                // import 경로가 없으면 파일명 인덱스로 직접 탐색
                ParsedFile calleeFile = null;
                if (importedPath != null) {
                    String importedName = importedPath.substring(importedPath.lastIndexOf('/') + 1)
                            .replaceAll("\\.(tsx|ts|jsx|js)$", "");
                    calleeFile = fileNameToFile.get(importedName);
                }
                if (calleeFile == null) {
                    calleeFile = fileNameToFile.get(componentName);
                }
                if (calleeFile == null || calleeFile.filePath().equals(callerFile.filePath())) continue;
                UUID calleeFileId = fileNodeIds.get(calleeFile.filePath());
                if (calleeFileId == null) continue;
                String edgeId = extractFileNameWithoutExt(callerFile.filePath()) + "-jsx-" + componentName;
                if (usedEdgeIds.contains(edgeId)) continue;
                usedEdgeIds.add(edgeId);
                Edge jsxEdge = Edge.create(graphId, edgeId, EdgeType.FUNCTION_CALL, callerFileId, calleeFileId);
                Map<String, Object> jsxMeta = new HashMap<>();
                jsxMeta.put("callerFile", callerFile.filePath());
                jsxMeta.put("calleeFile", calleeFile.filePath());
                jsxMeta.put("isJsxRender", true);
                jsxEdge.updateMetadata(jsxMeta);
                graphRepository.saveEdge(jsxEdge);
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
                graphRepository.saveNode(tableNode);
                entityClassToTableNodeId.put(table.className(), tableNode.getId());
            }
        }

        // Repository FILE → DB_TABLE 엣지 (파일 단위 — 그래프 시각화용) + FUNCTION → DB_TABLE (흐름 재생용)
        Set<String> usedDbEdgeIds = new HashSet<>();
        for (ParsedFile pf : parsedFiles) {
            if (pf.repositoryEntityClass() == null) continue;
            UUID repoFileId = fileNodeIds.get(pf.filePath());
            UUID tableNodeId = entityClassToTableNodeId.get(pf.repositoryEntityClass());
            if (repoFileId == null || tableNodeId == null) continue;

            String fileBase = extractFileName(pf.filePath());

            // 파일 단위 엣지 (기존 방식 유지 — 그래프 시각화)
            Set<EdgeType> fileCrudTypes = detectCrudTypes(pf.functions());
            for (EdgeType crudType : fileCrudTypes) {
                String edgeId = fileBase + "-" + crudType.name().toLowerCase() + "-" + pf.repositoryEntityClass();
                if (!usedDbEdgeIds.contains(edgeId)) {
                    usedDbEdgeIds.add(edgeId);
                    graphRepository.saveEdge(Edge.create(graphId, edgeId, crudType, repoFileId, tableNodeId));
                }
            }

            // 함수 단위 엣지 — 각 Repository 함수에서 DB_TABLE로 직접 연결 (흐름 재생에서 DB까지 추적 가능)
            for (String funcName : pf.functions()) {
                UUID funcId = funcNodeIds.get(pf.filePath() + "::" + funcName);
                if (funcId == null) continue;
                Set<EdgeType> funcCrudTypes = detectCrudTypes(List.of(funcName));
                for (EdgeType crudType : funcCrudTypes) {
                    String edgeId = fileBase + "-fn-" + funcName + "-" + crudType.name().toLowerCase();
                    if (!usedDbEdgeIds.contains(edgeId)) {
                        usedDbEdgeIds.add(edgeId);
                        graphRepository.saveEdge(Edge.create(graphId, edgeId, crudType, funcId, tableNodeId));
                    }
                }
            }
        }

        // raw SQL 문자열에서 추출한 테이블 접근 — DB_TABLE 노드 생성(ORM 미감지 테이블만) + DB_READ/WRITE 엣지
        // rawSqlTableNodeIds: 테이블명(소문자) → 노드 ID (ORM 경유 이미 생성된 것 재사용)
        Map<String, UUID> rawSqlTableNodeIds = new HashMap<>();
        // ORM 경로로 이미 생성된 테이블 노드를 테이블명(소문자)으로 역인덱싱
        for (Map.Entry<String, UUID> entry : entityClassToTableNodeId.entrySet()) {
            rawSqlTableNodeIds.put(entry.getKey().toLowerCase(), entry.getValue());
        }

        for (ParsedFile pf : parsedFiles) {
            if (pf.rawSqlAccesses().isEmpty()) continue;
            UUID fileId = fileNodeIds.get(pf.filePath());
            if (fileId == null) continue;
            String fileBase = extractFileName(pf.filePath());

            for (RawSqlAccess access : pf.rawSqlAccesses()) {
                String tableName = access.tableName();
                // 이미 ORM으로 생성된 테이블이면 재사용, 없으면 새로 생성
                UUID tableNodeId = rawSqlTableNodeIds.computeIfAbsent(tableName, name -> {
                    Node tableNode = Node.create(graphId, NodeType.DB_TABLE, name, pf.filePath(), pf.language());
                    tableNode.updateMetadata(Map.of("source", "raw_sql"));
                    graphRepository.saveNode(tableNode);
                    return tableNode.getId();
                });

                EdgeType edgeType = access.isWrite() ? EdgeType.DB_WRITE : EdgeType.DB_READ;
                String edgeId = fileBase + "-rawsql-" + edgeType.name().toLowerCase() + "-" + tableName;
                if (!usedDbEdgeIds.contains(edgeId)) {
                    usedDbEdgeIds.add(edgeId);
                    graphRepository.saveEdge(Edge.create(graphId, edgeId, edgeType, fileId, tableNodeId));
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
                // 비Spring 프레임워크(Express/FastAPI/Gin 등)는 "METHOD:/path" 형식 — 경로만 분리해야 매칭 가능
                String path = mapping.matches("^[A-Z]+:.*")
                        ? mapping.substring(mapping.indexOf(':') + 1)
                        : mapping;
                // Spring {var}와 Express/Rails :param 세그먼트를 * 글로브로 정규화
                String glob = path.replaceAll("\\{[^}]+}", "*").replaceAll("/:[^/]+", "/*");
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

    // import 경로가 실제 파일 경로와 일치하는지 확인 — 상대경로(TS/JS/Python)와 패키지경로(Java/Kotlin) 모두 처리
    private boolean isImportMatch(String importPath, String filePath) {
        String normalizedFile = filePath.replace("\\", "/");
        String fileWithoutExt = normalizedFile.contains(".")
                ? normalizedFile.substring(0, normalizedFile.lastIndexOf('.'))
                : normalizedFile;

        // 상대경로(./  ../): ./ 제거 후 경로 세그먼트로 매칭 (TypeScript/JS/Python 상대 import)
        if (importPath.startsWith("./") || importPath.startsWith("../")) {
            String rel = importPath.replaceAll("^(\\./)+", "").replace("\\", "/");
            return fileWithoutExt.endsWith(rel)
                    || normalizedFile.endsWith(rel + ".ts")
                    || normalizedFile.endsWith(rel + ".tsx")
                    || normalizedFile.endsWith(rel + ".js")
                    || normalizedFile.endsWith(rel + ".jsx")
                    || normalizedFile.endsWith(rel + ".py")
                    || normalizedFile.endsWith(rel + "/index.ts")
                    || normalizedFile.endsWith(rel + "/index.tsx")
                    || normalizedFile.endsWith(rel + "/index.js");
        }

        // 패키지경로: com.example.User → com/example/User (Java/Kotlin/Python 절대 import)
        String normalizedImport = importPath.replace(".", "/").replace("\\", "/");
        return fileWithoutExt.endsWith(normalizedImport)
                || normalizedFile.endsWith(normalizedImport + ".java")
                || normalizedFile.endsWith(normalizedImport + ".kt")
                || normalizedFile.endsWith(normalizedImport + ".py")
                || normalizedFile.endsWith(normalizedImport + ".go")
                || normalizedFile.endsWith(normalizedImport + ".rs")
                || normalizedFile.endsWith(normalizedImport + ".cs");
    }
}
