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

    // JDK/컬렉션 내장 메서드명 — 실제 타깃은 JDK 타입(그래프에 노드 없음)이라, import 매칭 없이 전역 폴백으로
    // 임의 도메인 파일에 연결되면 거의 확실히 phantom 엣지. 폴백 단계에서만 엣지 생성을 제외하는 용도.
    // (GraphWarningService의 JDK_COLLECTION_CALL_NAMES와 내용은 겹치나 책임이 다르다 — 저쪽은 경고 억제, 이쪽은 엣지 차단.)
    private static final Set<String> JDK_BUILTIN_CALL_NAMES = Set.of(
        "get", "set", "add", "addAll", "put", "putAll", "remove", "removeAll",
        "contains", "containsKey", "clear", "size", "isEmpty", "keySet", "values",
        "entrySet", "stream", "forEach", "orElse", "orElseGet", "orElseThrow",
        "ifPresent", "getOrDefault", "computeIfAbsent", "anyMatch", "allMatch",
        "noneMatch", "findFirst", "toList",
        // String/Pattern 정규식 메서드 — Matcher.matches()·str.matches()·Pattern.matcher() 의 전역 폴백 phantom 엣지 차단
        "matches", "matcher"
    );

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
                // 값(콜백)으로 참조되는 함수 — 호출 엣지가 없어도 DEAD_CODE 오탐에서 제외하기 위한 플래그
                if (pf.valueReferencedFunctions() != null && pf.valueReferencedFunctions().contains(funcName)) {
                    meta.put("referencedAsValue", true);
                }
                // 테스트 함수(Rust #[test]/#[cfg(test)] mod 등 파일명으로 못 거르는 인라인 테스트) — HIGH_FAN_OUT 제외용
                if (pf.testMethods() != null && pf.testMethods().contains(funcName)) {
                    meta.put("isTest", true);
                }
                // 파일 내 동명 정의가 2개 이상이면 이 노드는 여러 정의의 머지 — 호출이 union 되어 fan-out이 부풀려진다.
                // HIGH_FAN_OUT 정밀 가드가 이 값으로 union-부풀린 fan-out을 제외한다.
                if (pf.functionDefCounts() != null) {
                    int defCount = pf.functionDefCounts().getOrDefault(funcName, 1);
                    if (defCount >= 2) meta.put("mergedDefCount", defCount);
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
                        // 자기 자신 매칭 차단 — bare npm 패키지명(예: 'zustand')이 동명 로컬 파일(zustand.ts)의
                        // 경로 접미사와 우연히 일치해 자기참조 IMPORT phantom(CYCLIC_IMPORT 1노드 자가순환)을 만듦.
                        // 파일이 스스로를 import하는 것은 어떤 언어에서도 성립하지 않으므로 항상 안전한 차단.
                        .filter(e -> !sourceFileId.equals(e.getValue()))
                        .filter(e -> isImportMatch(pf.filePath(), importPath, e.getKey()))
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

                    // 같은 파일 내 호출 — sameFile 마커 엣지 생성 (DEAD_CODE 카운트·ASYNC_SELF_CALL 감지용, HIGH_FAN_OUT은 제외)
                    // targetClass가 caller 파일이 선언한 타입이면 같은 파일 호출(Go 리시버 c.Method()=Type::method,
                    // 파일명≠타입명이라 클래스명 매칭만으론 못 잡음 — declaredTypes로 보강해 마커 손실 방지).
                    String callerClassName = extractFileNameWithoutExt(callerFile.filePath());
                    if ((targetClass == null || targetClass.equals(callerClassName)
                            || callerFile.declaredTypes().contains(targetClass))
                            && callerFile.functions().contains(calleeFunc)) {
                        UUID sameFileCalleeId = funcNodeIds.get(callerFile.filePath() + "::" + calleeFunc);
                        if (sameFileCalleeId != null && !sameFileCalleeId.equals(callerFuncId)) {
                            String sameFileEdgeId = extractFileName(callerFile.filePath()) + "-" + callerFunc
                                    + "-selfcalls-" + calleeFunc;
                            if (!usedEdgeIds.contains(sameFileEdgeId)) {
                                usedEdgeIds.add(sameFileEdgeId);
                                Edge sameFileEdge = Edge.create(graphId, sameFileEdgeId, EdgeType.FUNCTION_CALL,
                                        callerFuncId, sameFileCalleeId);
                                Map<String, Object> sameMeta = new HashMap<>();
                                sameMeta.put("callerFile", callerFile.filePath());
                                sameMeta.put("calleeFile", callerFile.filePath());
                                sameMeta.put("sameFile", true);
                                sameFileEdge.updateMetadata(sameMeta);
                                graphRepository.saveEdge(sameFileEdge);
                            }
                        }
                    }

                    // callee 파일 해소: 클래스명 명시 호출은 클래스명으로 정확 매칭,
                    // bare-name 호출은 caller가 실제 import한 파일로 한정(정확도) 후 없으면 전역 폴백(recall 보존)
                    ParsedFile bestMatch;
                    if (targetClass != null) {
                        bestMatch = resolveQualifiedCall(callerFile, calleeFunc, targetClass, parsedFiles);
                    } else if ("Java".equals(callerFile.language()) && callerFile.functions().contains(calleeFunc)) {
                        // 자기 파일에 이미 동명 정의가 있으면 Java 의미론상 그게 확정 우선 — cross-file 동명 후보는
                        // 아예 보지 않음(위 sameFile 마커 엣지로 이미 정확히 기록됨). 안 그러면 resolveBareCall이
                        // 전역 폴백으로 엉뚱한 동명 함수를 골라 phantom cross-file 엣지가 중복 생성됨(패턴 A).
                        continue;
                    } else {
                        bestMatch = resolveBareCall(callerFile, calleeFunc, parsedFiles, interfaceToImplFiles, true);
                        if (bestMatch == null) {
                            ParsedFile fallback = resolveBareCall(callerFile, calleeFunc, parsedFiles, interfaceToImplFiles, false);
                            // JDK/컬렉션 내장 메서드명이 다른 디렉터리 파일로 폴백되면 phantom(실제 타깃은 JDK 타입, 노드 없음)
                            // → 엣지 미생성. 같은 디렉터리 폴백은 같은 패키지 내 실제 호출일 수 있어 보존(Go 등 import 없는
                            // same-package 호출 recall 보호 — gin get() DEAD_CODE 오탐 회피).
                            if (fallback != null && JDK_BUILTIN_CALL_NAMES.contains(calleeFunc)
                                    && !sameDir(callerFile.filePath(), fallback.filePath())) {
                                fallback = null;
                            }
                            bestMatch = fallback;
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

        // 비JPA ORM 데이터 접근(Django Entity.objects 등) — FILE → DB_TABLE 엣지(DB_READ/WRITE).
        // 엔티티 클래스명을 entityClassToTableNodeId(엔티티 정의에서 생성된 테이블 노드)로 해소 — 미지의 클래스는 엣지 미생성(precision).
        for (ParsedFile pf : parsedFiles) {
            if (pf.dbAccesses().isEmpty()) continue;
            UUID fileId = fileNodeIds.get(pf.filePath());
            if (fileId == null) continue;
            String fileBase = extractFileName(pf.filePath());

            for (DbAccess access : pf.dbAccesses()) {
                UUID tableNodeId = entityClassToTableNodeId.get(access.entityClass());
                if (tableNodeId == null) continue;
                EdgeType edgeType = access.isWrite() ? EdgeType.DB_WRITE : EdgeType.DB_READ;
                String edgeId = fileBase + "-orm-" + edgeType.name().toLowerCase() + "-" + access.entityClass();
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

        // 보존 정책 — 방금 만든 버전 포함 비고정 최근 N개만 유지, 초과분 삭제 (cascade로 노드/엣지/코멘트/스타일/프리셋 함께 제거)
        // analysis 컨텍스트가 graph 애플리케이션 서비스를 주입받지 않도록, 그래프 저장을 이미 담당하는 빌더에서 도메인 정책을 직접 적용
        GraphRetentionPolicy.selectEvictable(graphRepository.findByProjectId(projectId))
                .forEach(old -> graphRepository.deleteById(old.getId()));

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

    // "Class::method" 형식 호출 — 클래스명이 파일명 또는 파일이 선언한 클래스명과 일치하는 파일로 해소 (없으면 null).
    // 파일명 매칭은 Java/C#(파일명=클래스명)용, declaredTypes 매칭은 TS·Python 등(파일명≠클래스명)용.
    private ParsedFile resolveQualifiedCall(ParsedFile callerFile, String calleeFunc,
                                            String targetClass, List<ParsedFile> parsedFiles) {
        for (ParsedFile calleeFile : parsedFiles) {
            if (calleeFile.filePath().equals(callerFile.filePath())) continue;
            if (!calleeFile.functions().contains(calleeFunc)) continue;
            if (extractFileNameWithoutExt(calleeFile.filePath()).equals(targetClass)) return calleeFile;
            if (calleeFile.declaredTypes().contains(targetClass)) return calleeFile;
        }
        return null;
    }

    // bare-name 호출의 callee 파일 선택 — 구현체를 인터페이스보다 우선.
    // onlyImported=true면 caller가 import한 파일로 후보를 한정(정확도), false면 전역 후보(폴백)
    private ParsedFile resolveBareCall(ParsedFile callerFile, String calleeFunc,
                                       List<ParsedFile> parsedFiles,
                                       Map<String, List<ParsedFile>> interfaceToImplFiles,
                                       boolean onlyImported) {
        ParsedFile bestMatch = null;
        boolean bestIsInterface = false;
        for (ParsedFile calleeFile : parsedFiles) {
            if (calleeFile.filePath().equals(callerFile.filePath())) continue;
            if (!calleeFile.functions().contains(calleeFunc)) continue;
            if (onlyImported && !callerImports(callerFile, calleeFile)) continue;
            String calleeClassName = extractFileNameWithoutExt(calleeFile.filePath());
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
        return bestMatch;
    }

    // 두 파일 경로가 같은 디렉터리(패키지)에 있는지
    private boolean sameDir(String a, String b) {
        String na = a.replace("\\", "/");
        String nb = b.replace("\\", "/");
        return na.substring(0, na.lastIndexOf('/') + 1).equals(nb.substring(0, nb.lastIndexOf('/') + 1));
    }

    // caller가 calleeFile을 실제로 import하는지 — 기존 isImportMatch 재사용
    private boolean callerImports(ParsedFile callerFile, ParsedFile calleeFile) {
        for (String imp : callerFile.imports()) {
            if (isImportMatch(callerFile.filePath(), imp, calleeFile.filePath())) return true;
        }
        return false;
    }

    // import 경로가 실제 파일 경로와 일치하는지 — 상대경로(./ ../)는 소스 위치 기준 절대 해소,
    // tsconfig alias(@/)는 src 기준 해소, 패키지경로(Java/Kotlin/Go/Python 절대)는 세그먼트 접미사 매칭.
    private boolean isImportMatch(String sourceFilePath, String importPath, String filePath) {
        String normalizedFile = filePath.replace("\\", "/");
        String fileWithoutExt = stripExtension(normalizedFile);

        // TS/JS 상대경로: 소스 디렉터리 기준으로 ./ ../ 를 적용해 분석 루트 기준 경로로 해소 후 세그먼트 정확 매칭.
        // (기존 접미사 매칭은 ../ 를 못 풀고 짧은 ./ 이름이 동명 파일에 오매칭돼 phantom 엣지/순환을 만들었다.)
        if (importPath.startsWith("./") || importPath.startsWith("../")) {
            String resolved = resolveRelativeImport(sourceFilePath, importPath);
            return resolved != null && matchesModulePath(normalizedFile, fileWithoutExt, resolved);
        }

        // tsconfig path alias @/ → src/ (모던 TS 관용, 분석 루트가 src이거나 repo 루트일 수 있어 양쪽 허용).
        if (importPath.startsWith("@/")) {
            String sub = importPath.substring(2).replace("\\", "/");
            return matchesModulePath(normalizedFile, fileWithoutExt, sub)
                    || matchesModulePath(normalizedFile, fileWithoutExt, "src/" + sub);
        }

        // TS/JS baseUrl bare import: "entities/task"(→entities/task/index.ts)·"features/foo/bar"(→.ts) 같은
        // 절대(루트 기준) import. matchesModulePath가 세그먼트 경계 + TS 확장자 + /index.* 폴백을 처리한다.
        // ★점이 있는 패키지경로(Java com.example.User·Python pkg.mod·Go full path)는 슬래시 정규화 시 점이 남아
        //   여기서 no-op → 아래 dotted 브랜치가 처리(순수 additive, 무회귀). 디렉터리(barrel) import recall만 추가.
        // ★슬래시 없는 단일 세그먼트(예: 'zustand')는 npm 패키지 bare specifier가 지배적 — 실제 baseUrl 절대
        //   import는 항상 디렉터리+파일(entities/task류)이라 슬래시를 동반한다. 슬래시 요구로 npm 패키지가
        //   동명 로컬 파일(zustand.ts↔zustand.ts)로 자기/교차 매칭돼 CYCLIC_IMPORT phantom을 만드는 것을 차단
        //   (2026-07-01 bulletproof-react __mocks__/zustand.ts 3개 교차순환 측정으로 발견).
        if (importPath.contains("/")
                && matchesModulePath(normalizedFile, fileWithoutExt, importPath.replace("\\", "/"))) return true;

        // 패키지경로: com.example.User → com/example/User (Java/Kotlin/Python/Go 절대 import)
        String normalizedImport = importPath.replace(".", "/").replace("\\", "/");
        // 확장자 없는 raw endsWith 매칭은 아래 6개 언어 확장자 체크와 중복(각 언어는 자기 확장자 접미사로 이미
        // 커버됨) — TS/JS(.ts/.tsx/.js/.jsx)만 확장자 목록에 없어 이 raw 매칭에 전적으로 의존하는데, 슬래시 없는
        // 단일 세그먼트(zustand 등 npm 패키지)가 여기로 새서 동명 로컬 파일과 오매칭됐다. 슬래시 요구로 차단.
        return (normalizedImport.contains("/") && fileWithoutExt.endsWith(normalizedImport))
                || normalizedFile.endsWith(normalizedImport + ".java")
                || normalizedFile.endsWith(normalizedImport + ".kt")
                || normalizedFile.endsWith(normalizedImport + ".py")
                || normalizedFile.endsWith(normalizedImport + ".go")
                || normalizedFile.endsWith(normalizedImport + ".rs")
                || normalizedFile.endsWith(normalizedImport + ".cs");
    }

    // 마지막 확장자 제거 (디렉터리 세그먼트의 점은 보존하기 위해 마지막 '/' 이후의 '.'만 본다)
    private static String stripExtension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash ? path.substring(0, dot) : path;
    }

    // 상대 import(./ ../)를 소스 파일 디렉터리 기준으로 해소해 분석 루트 기준 경로 문자열을 반환.
    private static String resolveRelativeImport(String sourceFilePath, String importPath) {
        String src = sourceFilePath.replace("\\", "/");
        int lastSlash = src.lastIndexOf('/');
        java.util.Deque<String> segs = new java.util.ArrayDeque<>();
        if (lastSlash > 0) {
            for (String s : src.substring(0, lastSlash).split("/")) {
                if (!s.isEmpty()) segs.addLast(s);
            }
        }
        for (String part : importPath.replace("\\", "/").split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!segs.isEmpty()) segs.removeLast();
            } else {
                segs.addLast(part);
            }
        }
        return String.join("/", segs);
    }

    // TS/JS 모듈 경로(확장자 없는 분석루트 기준 경로)가 후보 파일과 세그먼트 경계에서 일치하는지.
    private static final String[] TS_MODULE_SUFFIXES = {
        ".ts", ".tsx", ".js", ".jsx", ".py", "/index.ts", "/index.tsx", "/index.js"
    };
    private boolean matchesModulePath(String normalizedFile, String fileWithoutExt, String modulePath) {
        if (segmentEndsWith(fileWithoutExt, modulePath)) return true;
        for (String suffix : TS_MODULE_SUFFIXES) {
            if (segmentEndsWith(normalizedFile, modulePath + suffix)) return true;
        }
        return false;
    }

    // path 가 suffix 와 정확히 같거나 '/'+suffix 로 끝나는지 — 부분 세그먼트 오매칭(button↔my-button) 방지.
    private static boolean segmentEndsWith(String path, String suffix) {
        return path.equals(suffix) || path.endsWith("/" + suffix);
    }
}
