// 정적 분석 결과를 Graph/Node/Edge 엔티티로 변환하여 저장하는 빌더
package com.codeprint.infrastructure.analysis;

import com.codeprint.domain.graph.*;
import com.codeprint.domain.graph.port.SnapshotReferencePort;
import com.codeprint.domain.project.ProjectRepository;
import com.codeprint.infrastructure.adapter.FeaturedProjectProvisioningAdapter;
import com.codeprint.shared.topology.ServiceBoundary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;

@Component
@RequiredArgsConstructor
public class GraphBuilder {

    private final GraphRepository graphRepository;
    private final ProjectRepository projectRepository;
    private final SnapshotReferencePort snapshotReferencePort;

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
        "matches", "matcher",
        // Stream 최종 연산 — .collect(...)가 레포 내 동명 도메인 메서드(예: AdminMetricsQuery.collect)로
        // 오귀속되던 사각(엣지 정확도 패턴 B, 자기 레포 실측)
        "collect", "reduce",
        // Mockito/JUnit/AssertJ 정적 임포트 — 테스트 파일 전역에서 쓰이는 이름이라 폴백 시 레포 내 동명
        // 도메인 메서드(예: WebhookSignatureVerifier.verify)로 오귀속되기 가장 쉬운 이름들
        "verify", "verifyNoMoreInteractions", "verifyNoInteractions",
        "when", "thenReturn", "thenThrow", "doReturn", "doThrow", "doNothing",
        "given", "willReturn", "willThrow", "reset",
        "assertThat", "assertEquals", "assertTrue", "assertFalse",
        "assertNull", "assertNotNull", "assertThrows",
        // 빌더 패턴·정적 팩토리·엔트리포인트 공통 메서드명 — 리시버/컨텍스트 타입이 흔히 해소 안 돼
        // 전역 폴백에 빠지기 쉬운 이름들(WebClient/Bucket4j/JWT 빌더의 build(), Runnable.run(),
        // List.of()/Optional.of()류 정적 팩토리, Instant.from()류 변환, 타 클래스 main() 엔트리포인트).
        // 자기 레포 실측: GraphBuilder.build 인바운드 174건 중 ~170건이 이 폴백 오귀속으로 인한 phantom.
        "build", "run", "of", "from", "main"
    );

    // 한정 호출(targetClass::method)의 targetClass가 이 목록에 있으면 해소를 아예 시도하지 않는다(엣지 정확도
    // 패턴 B, qualified 호출용). 한정 호출은 bare 호출과 달리 import 스코프 없이 파일명/declaredTypes만으로
    // 매칭하므로, 레포가 JDK/테스트 프레임워크와 우연히 동명인 클래스를 따로 정의하면(예: 자체 DTO
    // "HttpResponse") 그쪽 메서드로 잘못 연결된다 — 실제 타깃은 JDK/외부 타입(그래프에 노드 없음).
    private static final Set<String> EXTERNAL_QUALIFYING_CLASS_NAMES = Set.of(
        "HttpResponse", "HttpRequest", "Optional", "Stream", "Collectors",
        "Arrays", "Collections", "Objects", "Matcher", "Pattern",
        "Files", "Paths", "Mockito", "Assertions", "Assert"
    );

    // 분석된 파일 목록으로 그래프와 노드/엣지를 생성하여 저장
    @Transactional
    public Graph build(UUID projectId, UUID analysisId, List<ParsedFile> parsedFiles) {
        return build(projectId, analysisId, parsedFiles, parsedFiles.size());
    }

    // 전체 대상 파일 수 포함 빌드 — MAX_FILES 절단 시 totalFileCount가 분석 파일 수보다 큼
    // 노드/엣지 수천 건을 단일 트랜잭션으로 묶어 배치·원자성 보장 — 호출자(AnalysisRunner/PrReviewService)는
    // git clone 등 네트워크·IO 구간을 트랜잭션 밖에 두기 위해 더 이상 이 메서드를 감싸는 @Transactional을 갖지 않는다.
    @Transactional
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

            Map<String, Object> fileMeta = new HashMap<>();
            if (pf.fileComment() != null) fileMeta.put("comment", pf.fileComment());
            // Spring 빈 스테레오타입 — CIRCULAR_BEAN_DEPENDENCY가 빈 파일만 골라 순환 판정 대상으로 삼는 데 사용
            if (pf.beanStereotype() != null) fileMeta.put("beanStereotype", pf.beanStereotype());
            if (!fileMeta.isEmpty()) fileNode.updateMetadata(fileMeta);
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
                if (pf.transactionalMethods() != null && pf.transactionalMethods().contains(funcName)) {
                    meta.put("isTransactional", true);
                }
                // 인터페이스 추상 메서드 — BROKEN_INTERFACE_CHAIN이 구현체 존재 여부를 판정하는 데 사용
                if (pf.interfaceMethods() != null && pf.interfaceMethods().contains(funcName)) {
                    meta.put("isInterface", true);
                    // @FeignClient 인터페이스는 Spring이 런타임에 프록시로 구현을 생성 — 소스에 구현체가
                    // 없는 게 정상이라(SPRING_DATA_BASE_METHODS와 같은 이유), BROKEN_INTERFACE_CHAIN 오탐 방지.
                    if (pf.feignClientTarget() != null) meta.put("isFrameworkInterface", true);
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
                // 정의 시작 줄(1-indexed, Java/TS/JS만) — VS Code 인라인 경고가 이 줄을 가리킴
                if (pf.functionLines() != null) {
                    Integer line = pf.functionLines().get(funcName);
                    if (line != null) meta.put("line", line);
                }
                // 정의 식별자 시작/끝 컬럼(0-indexed, Java/TS/JS만) — VS Code 인라인 경고 밑줄을 식별자 범위로 좁히는 데 사용
                if (pf.functionColumns() != null) {
                    Integer col = pf.functionColumns().get(funcName);
                    if (col != null) {
                        meta.put("col", col);
                        meta.put("endCol", col + funcName.length());
                    }
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

        // Java 클래스명 → ParsedFile 인덱스 — 상속 체인(extends) 조상 조회용(엣지 정확도 패턴 A')
        Map<String, ParsedFile> javaClassNameToFile = new HashMap<>();
        for (ParsedFile pf : parsedFiles) {
            if ("Java".equals(pf.language())) {
                javaClassNameToFile.put(extractFileNameWithoutExt(pf.filePath()), pf);
            }
        }

        // 빈(bean) 파일 간 필드 의존 엣지 생성 — CIRCULAR_BEAN_DEPENDENCY 판정용(BE-18·BE-19 재현 대상).
        // 필드 타입을 다른 빈 파일로 해소(인터페이스는 구현체 우선, FUNCTION_CALL 매칭과 동일 원칙),
        // @Lazy 주입 타입은 Spring이 프록시로 즉시 완전 생성을 미뤄 실제로 순환을 허용하므로 isLazy 메타로 표시.
        for (ParsedFile pf : parsedFiles) {
            if (pf.beanStereotype() == null) continue;
            UUID sourceFileId = fileNodeIds.get(pf.filePath());
            if (sourceFileId == null) continue;
            Set<String> lazyTypes = new HashSet<>(pf.lazyDependencyTypes());
            for (String fieldType : pf.fieldDependencyTypes()) {
                List<ParsedFile> implFiles = interfaceToImplFiles.get(fieldType);
                List<ParsedFile> targets = (implFiles != null && !implFiles.isEmpty())
                        ? implFiles
                        : (javaClassNameToFile.containsKey(fieldType) ? List.of(javaClassNameToFile.get(fieldType)) : List.of());
                for (ParsedFile target : targets) {
                    if (target.beanStereotype() == null) continue;
                    UUID targetFileId = fileNodeIds.get(target.filePath());
                    if (targetFileId == null || targetFileId.equals(sourceFileId)) continue;
                    String edgeId = extractFileName(pf.filePath()) + "-fielddep-" + extractFileName(target.filePath()) + "-" + fieldType;
                    Edge depEdge = Edge.create(graphId, edgeId, EdgeType.FIELD_DEPENDENCY, sourceFileId, targetFileId);
                    if (lazyTypes.contains(fieldType)) {
                        depEdge.updateMetadata(Map.of("isLazy", true));
                    }
                    graphRepository.saveEdge(depEdge);
                }
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
                    boolean isJava = "Java".equals(callerFile.language());
                    // 자기 파일엔 정의가 없지만 상속 체인의 조상이 실제로 정의하는 경우(예: 자식이 호출하는
                    // text()가 부모 AbstractTreeSitterAnalyzer에 있음) 미리 조회 — 일반 resolveBareCall(전역
                    // 동명 탐색)에 맡기면 무관한 동명 함수로 phantom 연결될 수 있다(엣지 정확도 패턴 A').
                    ParsedFile inheritedMatch = (isJava && targetClass == null)
                            ? resolveInheritedCall(callerFile, calleeFunc, javaClassNameToFile) : null;

                    ParsedFile bestMatch;
                    if (targetClass != null) {
                        if (EXTERNAL_QUALIFYING_CLASS_NAMES.contains(targetClass)) continue;
                        bestMatch = resolveQualifiedCall(callerFile, calleeFunc, targetClass, parsedFiles);
                    } else if (isJava && callerFile.functions().contains(calleeFunc)) {
                        // 자기 파일에 이미 동명 정의가 있으면 Java 의미론상 그게 확정 우선 — cross-file 동명 후보는
                        // 아예 보지 않음(위 sameFile 마커 엣지로 이미 정확히 기록됨). 안 그러면 resolveBareCall이
                        // 전역 폴백으로 엉뚱한 동명 함수를 골라 phantom cross-file 엣지가 중복 생성됨(패턴 A).
                        continue;
                    } else if (inheritedMatch != null) {
                        bestMatch = inheritedMatch;
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

            // 파일명만 쓰면 서로 다른 서비스가 동일 파일명(예: 여러 서비스의 CustomerRepository.java)을 쓸 때
            // usedDbEdgeIds가 전역 Set이라 두 번째 서비스의 엣지가 조용히 드롭된다 — 전체 상대경로로 유일성 보장
            // (2026-07-17, SHARED_DATABASE_ACCESS 벤치 픽스처 작성 중 발견. 같은 엔티티를 다루는 서비스일수록
            // 파일명이 같아지기 쉬워 하필 이 룰이 가장 필요한 시나리오에서 recall이 깨지는 조합이었다).
            String fileBase = pf.filePath();

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
            // 파일명만 쓰면 usedDbEdgeIds가 전역 Set이라 서로 다른 서비스의 동일 파일명(예: 여러 서비스의
            // db.py)에서 두 번째 서비스의 엣지가 조용히 드롭된다 — line 441과 동일 원인, 전체 상대경로로 유일성 보장
            // (2026-07-21, A-2 dedup 버그 잔존 3곳 중 하나로 재발견·수정, decisions/DECISIONS_ANALYSIS.md 참조)
            String fileBase = pf.filePath();

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
            // 파일명만 쓰면 usedDbEdgeIds가 전역 Set이라 서로 다른 서비스의 동일 파일명(예: 여러 서비스의
            // models.py)에서 두 번째 서비스의 엣지가 조용히 드롭된다 — line 441과 동일 원인, 전체 상대경로로 유일성 보장
            // (2026-07-21, A-2 dedup 버그 잔존 3곳 중 하나로 재발견·수정, decisions/DECISIONS_ANALYSIS.md 참조)
            String fileBase = pf.filePath();

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

        // API_ENDPOINT 노드 생성 + FILE → API_ENDPOINT 엣지 (컨트롤러 경로 표면 시각화 — 파일 단위 1차 완료).
        // 처리 함수까지 연결하는 함수 단위 엣지(2차, Java/Kotlin·JS/TS(Express+NestJS)·Python·Go — Ruby는 제외, 사유는
        // StaticCodeAnalyzer.extractControllerMappingFunctions 주석 참조) — controllerMappingFunctions로 해소된
        // 매핑은 API_ENDPOINT → FUNCTION을 FUNCTION_CALL 타입으로 생성(프론트 흐름재생이 API_ENDPOINT를
        // FUNCTION과 동일하게 FUNCTION_CALL 엣지 소스/타깃으로 취급하도록 이미 준비돼 있어 신규 타입 불필요).
        Set<String> usedApiEndpointEdgeIds = new HashSet<>();
        for (ParsedFile pf : parsedFiles) {
            if (pf.controllerMappings().isEmpty()) continue;
            UUID controllerFileId = fileNodeIds.get(pf.filePath());
            if (controllerFileId == null) continue;

            for (String mapping : new LinkedHashSet<>(pf.controllerMappings())) {
                Node endpointNode = Node.create(graphId, NodeType.API_ENDPOINT, mapping, pf.filePath(), pf.language());
                graphRepository.saveNode(endpointNode);

                String edgeId = extractFileName(pf.filePath()) + "-endpoint-" + mapping;
                if (!usedApiEndpointEdgeIds.contains(edgeId)) {
                    usedApiEndpointEdgeIds.add(edgeId);
                    Edge endpointEdge = Edge.create(graphId, edgeId, EdgeType.CONTAINS, controllerFileId, endpointNode.getId());
                    graphRepository.saveEdge(endpointEdge);
                }

                String handlerFunc = pf.controllerMappingFunctions().get(mapping);
                if (handlerFunc != null) {
                    UUID handlerFuncId = funcNodeIds.get(pf.filePath() + "::" + handlerFunc);
                    if (handlerFuncId != null) {
                        String handlerEdgeId = extractFileName(pf.filePath()) + "-handles-" + mapping;
                        graphRepository.saveEdge(Edge.create(graphId, handlerEdgeId, EdgeType.FUNCTION_CALL, endpointNode.getId(), handlerFuncId));
                    }
                }
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

        // 모노레포 서비스 간 동기 호출(WebClient/RestTemplate) → SERVICE_CALL 엣지 생성 — 대상 서비스의
        // 대표 파일(해당 서비스에서 처음 발견된 파일, parsedFiles 순회 순서가 결정론적이라 대표 선택도 결정론적)에 연결.
        // 정확한 엔드포인트 매칭이 아니라 "서비스 A→B 호출 존재"만 필요해 대표 파일 하나로 충분(서비스 호출 체인 깊이 판정용).
        Map<String, UUID> serviceRepresentativeFile = new java.util.LinkedHashMap<>();
        for (ParsedFile pf : parsedFiles) {
            String service = ServiceBoundary.serviceOf(pf.filePath());
            if (service == null) continue;
            UUID fileId = fileNodeIds.get(pf.filePath());
            if (fileId == null) continue;
            serviceRepresentativeFile.putIfAbsent(service, fileId);
        }

        // FeignClient 인터페이스 파일(@FeignClient(name=...)) → 논리 서비스명. WebClient/RestTemplate과 달리
        // FeignClient는 DI로 주입돼 쓰이므로 직접 호출문 대신, 이미 만들어지는 IMPORT 매칭(isImportMatch)을
        // 재사용해 "이 인터페이스를 import하는 파일 = 그 서비스를 호출하는 파일"로 단순화한다.
        Map<String, String> feignTargetByFilePath = new HashMap<>();
        for (ParsedFile pf : parsedFiles) {
            if (pf.feignClientTarget() != null) feignTargetByFilePath.put(pf.filePath(), pf.feignClientTarget());
        }

        Set<String> usedServiceCallEdgeIds = new HashSet<>();
        for (ParsedFile pf : parsedFiles) {
            List<String> logicalServices = new ArrayList<>(pf.serviceCalls());
            if (!feignTargetByFilePath.isEmpty()) {
                for (String importPath : pf.imports()) {
                    for (Map.Entry<String, String> feignEntry : feignTargetByFilePath.entrySet()) {
                        if (isImportMatch(pf.filePath(), importPath, feignEntry.getKey())) {
                            logicalServices.add(feignEntry.getValue());
                        }
                    }
                }
            }
            if (logicalServices.isEmpty()) continue;
            UUID callerFileId = fileNodeIds.get(pf.filePath());
            if (callerFileId == null) continue;
            String callerService = ServiceBoundary.serviceOf(pf.filePath());

            for (String logicalService : logicalServices) {
                UUID targetFileId = null;
                String targetServiceName = null;
                for (Map.Entry<String, UUID> entry : serviceRepresentativeFile.entrySet()) {
                    if (entry.getKey().toLowerCase().contains(logicalService.toLowerCase())) {
                        targetFileId = entry.getValue();
                        targetServiceName = entry.getKey();
                        break;
                    }
                }
                // 대상 서비스를 못 찾았거나(외부 API 등) 같은 서비스 자기 호출이면 제외
                if (targetFileId == null || targetFileId.equals(callerFileId)) continue;
                if (targetServiceName.equalsIgnoreCase(callerService)) continue;

                // 파일명만 쓰면 서로 다른 서비스의 동일 파일명(예: 여러 서비스의 Client.java)이 같은 대상
                // 서비스를 호출할 때 usedServiceCallEdgeIds에서 두 번째 서비스의 엣지가 조용히 드롭된다 —
                // line 441의 usedDbEdgeIds와 같은 원인, 전체 상대경로로 유일성 보장(A-2 dedup 버그 잔존 3곳 중 하나)
                String edgeId = pf.filePath() + "-servicecall-" + targetServiceName;
                if (!usedServiceCallEdgeIds.contains(edgeId)) {
                    usedServiceCallEdgeIds.add(edgeId);
                    graphRepository.saveEdge(Edge.create(graphId, edgeId, EdgeType.SERVICE_CALL, callerFileId, targetFileId));
                }
            }
        }

        // 보존 정책 — 방금 만든 버전 포함 비고정 최근 N개만 유지, 초과분 삭제 (cascade로 노드/엣지/코멘트/스타일/프리셋 함께 제거)
        // analysis 컨텍스트가 graph 애플리케이션 서비스를 주입받지 않도록, 그래프 저장을 이미 담당하는 빌더에서 도메인 정책을 직접 적용
        // 시스템(갤러리) 계정 프로젝트는 축소된 보존 개수 적용 + 공유 게시물 스냅샷이 참조 중인 그래프는 보호(§18.8-④)
        int maxRecent = isSystemOwned(projectId) ? GraphRetentionPolicy.MAX_RECENT_SYSTEM : GraphRetentionPolicy.MAX_RECENT;
        Set<UUID> protectedGraphIds = snapshotReferencePort.findReferencedGraphIds(projectId);
        GraphRetentionPolicy.selectEvictable(graphRepository.findByProjectId(projectId), maxRecent, protectedGraphIds)
                .forEach(old -> graphRepository.deleteById(old.getId()));

        return graph;
    }

    // 프로젝트 소유자가 오늘의 공개레포 시스템 계정인지 확인 — 축소된 보존 개수 적용 여부 판단용
    private boolean isSystemOwned(UUID projectId) {
        return projectRepository.findById(projectId)
                .map(project -> FeaturedProjectProvisioningAdapter.SYSTEM_USER_ID.equals(project.getUserId()))
                .orElse(false);
    }

    // Repository 메서드명 목록에서 수행하는 CRUD 타입 집합 반환
    private Set<EdgeType> detectCrudTypes(List<String> methods) {
        Set<EdgeType> types = new java.util.LinkedHashSet<>();
        for (String method : methods) {
            String m = method.toLowerCase();
            if (m.startsWith("find") || m.startsWith("get") || m.startsWith("count")
                    || m.startsWith("exists") || m.startsWith("load") || m.startsWith("fetch")
                    || m.startsWith("read") || m.startsWith("list") || m.startsWith("search")
                    // JPQL 집계 함수 관용구 — 이게 없으면 접두사 미매칭 폴백(READ+WRITE 동시 추가)을 타
                    // read-only 집계 쿼리가 DB_WRITE로도 잘못 표시됨(엣지 정확도 4차 감사 패턴 D)
                    || m.startsWith("sum") || m.startsWith("avg") || m.startsWith("total")
                    || m.startsWith("min") || m.startsWith("max")) {
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

    // Java 상속 체인(extends)을 타고 올라가 calleeFunc를 정의한 가장 가까운 조상 파일을 찾는다(엣지 정확도 패턴 A').
    // 순환 상속(잘못된 소스나 분석 오류) 방어를 위해 방문한 클래스명을 추적해 무한루프를 막는다.
    private ParsedFile resolveInheritedCall(ParsedFile callerFile, String calleeFunc,
                                            Map<String, ParsedFile> javaClassNameToFile) {
        Set<String> visited = new HashSet<>();
        visited.add(extractFileNameWithoutExt(callerFile.filePath()));
        ParsedFile current = callerFile;
        while (true) {
            String parentName = current.extendedClass();
            if (parentName == null || !visited.add(parentName)) return null;
            ParsedFile parent = javaClassNameToFile.get(parentName);
            if (parent == null) return null;
            if (parent.functions().contains(calleeFunc)) return parent;
            current = parent;
        }
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
