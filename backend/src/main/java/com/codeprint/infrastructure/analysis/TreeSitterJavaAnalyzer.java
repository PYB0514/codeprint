// tree-sitter AST로 Java 함수 정의와 호출을 추출하는 분석기 (정규식보다 정확, 실패 시 폴백)
package com.codeprint.infrastructure.analysis;

import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterJava;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Java 소스를 tree-sitter로 파싱해 함수명과 함수별 호출 목록을 추출한다.
// 정규식 대비 이점: record 타입명을 함수로 오탐하지 않고, 인터페이스 추상 메서드를 정확히 인식하며,
// 주석·문자열 리터럴 내부의 가짜 식별자를 호출로 오인하지 않는다(AST가 토큰 종류를 구분).
class TreeSitterJavaAnalyzer extends AbstractTreeSitterAnalyzer {

    // tree-sitter 추출 결과 — 함수명 목록과 함수별 호출(callee) 목록, 함수명→정의 시작 줄(1-indexed), 함수명→식별자 시작 컬럼(0-indexed),
    // 필드명→선언 타입명(CIRCULAR_BEAN_DEPENDENCY의 빈 의존 그래프 구성용 — 기존엔 메서드 호출 수신자 해소용으로만 쓰고 버려졌음)
    record Result(List<String> functions, Map<String, List<String>> functionCalls, Map<String, Integer> functionLines,
                  Map<String, Integer> functionColumns, Map<String, String> fieldTypes) {}

    @Override
    protected TSLanguage createLanguage() {
        return new TreeSitterJava();
    }

    @Override
    protected String languageName() {
        return "Java";
    }

    // Java 소스 1개를 파싱해 함수·호출을 추출. native 로드/파싱 실패 시 Optional.empty() → 호출부가 정규식 폴백
    Optional<Result> parse(String content) {
        return parseTree(content, (root, src) -> {
            List<String> functions = new ArrayList<>();
            Map<String, Set<String>> calls = new LinkedHashMap<>();
            // 함수명 → 첫 정의의 시작 줄(1-indexed). 동명 오버로드는 첫 정의만 유지(줄 하나로 근사 — 오버로드별 구분은 범위 밖).
            Map<String, Integer> functionLines = new LinkedHashMap<>();
            // 함수명 → 식별자(이름) 시작 컬럼(0-indexed) — VS Code 인라인 경고 밑줄을 식별자 범위로 좁히는 데 사용
            Map<String, Integer> functionColumns = new LinkedHashMap<>();
            // functions 는 raw(중복 포함) 리스트 — 파일 내 동명 정의 수(머지 다중도)를 StaticCodeAnalyzer가 중앙에서 집계/디둡한다.
            // 클래스 필드는 어느 메서드에서든 가시하므로(선언 순서 무관) walk 전에 먼저 타입을 모은다.
            Map<String, String> fieldTypes = new LinkedHashMap<>();
            collectFieldTypes(root, src, fieldTypes);
            walk(root, src, null, functions, calls, fieldTypes, functionLines, functionColumns);

            Map<String, List<String>> functionCalls = new LinkedHashMap<>();
            calls.forEach((caller, callees) -> functionCalls.put(caller, new ArrayList<>(callees)));
            return new Result(functions, functionCalls, functionLines, functionColumns, fieldTypes);
        });
    }

    // 트리를 재귀 순회하며 method/constructor 정의를 수집하고, 호출을 가장 가까운 정의에 귀속.
    // scope = 현재 위치에서 보이는 변수명→타입(필드 + 파라미터 + 지역변수). method_invocation 수신자 해소에 사용.
    private void walk(TSNode node, byte[] src, String enclosing,
                      List<String> functions, Map<String, Set<String>> calls,
                      Map<String, String> scope, Map<String, Integer> functionLines,
                      Map<String, Integer> functionColumns) {
        String type = node.getType();

        if (type.equals("method_declaration")
                || type.equals("constructor_declaration")
                || type.equals("compact_constructor_declaration")) {
            TSNode nameNode = node.getChildByFieldName("name");
            String name = (nameNode != null && !nameNode.isNull()) ? text(nameNode, src) : "";
            if (!name.isEmpty()) {
                functions.add(name);
                // 식별자(nameNode) 자신의 위치 기준 — node(정의 전체)의 시작은 어노테이션(@Async 등)이 있으면
                // 그 줄부터라 col(식별자 컬럼)과 다른 줄을 가리키는 불일치가 생긴다(VS Code Range가 line+col을
                // 한 지점으로 합성하므로 반드시 같은 줄이어야 함) — line·col 모두 nameNode 기준으로 통일.
                functionLines.putIfAbsent(name, nameNode.getStartPoint().getRow() + 1);
                functionColumns.putIfAbsent(name, nameNode.getStartPoint().getColumn());
            }
            // 메서드 스코프 = 필드(전역) 복사본 + 이 메서드의 파라미터(+지역변수는 본문 순회 중 추가)
            Map<String, String> methodScope = new LinkedHashMap<>(scope);
            addParameterTypes(node, src, methodScope);
            String current = name.isEmpty() ? enclosing : name;
            int n = node.getChildCount();
            for (int i = 0; i < n; i++) {
                walk(node.getChild(i), src, current, functions, calls, methodScope, functionLines, functionColumns);
            }
            return;
        }

        if (type.equals("local_variable_declaration")) {
            // 지역변수 선언 — 이후(같은 메서드 스코프) 호출 수신자 해소를 위해 타입 등록
            String vtype = simpleTypeName(node.getChildByFieldName("type"), src);
            if (vtype != null) forEachDeclaratorName(node, src, nm -> scope.put(nm, vtype));
        } else if (type.equals("method_invocation")) {
            recordInvocation(node, src, enclosing, calls, scope);
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            walk(node.getChild(i), src, enclosing, functions, calls, scope, functionLines, functionColumns);
        }
    }

    // 호출을 기록 — 수신자 타입을 알면 "Type::method"로, 모르면 bare name으로(폴백 recall 보존)
    private void recordInvocation(TSNode node, byte[] src, String current,
                                  Map<String, Set<String>> calls, Map<String, String> scope) {
        if (current == null) return;
        TSNode nameNode = node.getChildByFieldName("name");
        if (nameNode == null || nameNode.isNull()) return;
        String name = text(nameNode, src);
        if (name.isEmpty()) return;
        String callee = name;
        String recvType = receiverType(node.getChildByFieldName("object"), src, scope);
        if (recvType != null) callee = recvType + "::" + name;
        add(calls, current, callee);
    }

    // 호출 수신자(object)에서 타깃 클래스 심플명을 추론 — 못 구하면 null(=bare 호출 유지)
    private String receiverType(TSNode obj, byte[] src, Map<String, String> scope) {
        if (obj == null || obj.isNull()) return null;
        String t = obj.getType();
        if (t.equals("identifier")) {
            String recv = text(obj, src);
            if (recv.isEmpty()) return null;
            // 대문자 단순 식별자 = ClassName.method() 정적 호출 → 클래스명 그대로
            if (Character.isUpperCase(recv.charAt(0))) return recv;
            // 소문자 = 변수 → 선언 타입으로 해소(필드/파라미터/지역변수)
            return scope.get(recv);
        }
        // this.repo.method() — field_access의 field 이름으로 필드 타입 조회
        if (t.equals("field_access")) {
            TSNode field = obj.getChildByFieldName("field");
            if (field != null && !field.isNull()) return scope.get(text(field, src));
        }
        return null;
    }

    // 클래스 필드 선언에서 변수명→타입(심플명) 수집 — 메서드 어디서든 가시하므로 walk 전에 모은다
    private void collectFieldTypes(TSNode node, byte[] src, Map<String, String> fieldTypes) {
        if (node.getType().equals("field_declaration")) {
            String vtype = simpleTypeName(node.getChildByFieldName("type"), src);
            if (vtype != null) forEachDeclaratorName(node, src, nm -> fieldTypes.putIfAbsent(nm, vtype));
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            collectFieldTypes(node.getChild(i), src, fieldTypes);
        }
    }

    // 메서드 파라미터의 (이름→타입)을 스코프에 등록
    private void addParameterTypes(TSNode methodNode, byte[] src, Map<String, String> scope) {
        TSNode params = methodNode.getChildByFieldName("parameters");
        if (params == null || params.isNull()) return;
        int n = params.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode p = params.getChild(i);
            if (!p.getType().equals("formal_parameter")) continue;
            String vtype = simpleTypeName(p.getChildByFieldName("type"), src);
            TSNode nm = p.getChildByFieldName("name");
            if (vtype != null && nm != null && !nm.isNull()) {
                String s = text(nm, src);
                if (!s.isEmpty()) scope.put(s, vtype);
            }
        }
    }

    // 선언 노드의 모든 variable_declarator 이름에 대해 동작 수행 (int a, b; 다중 선언 대응)
    private void forEachDeclaratorName(TSNode declNode, byte[] src, java.util.function.Consumer<String> action) {
        int n = declNode.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = declNode.getChild(i);
            if (c.getType().equals("variable_declarator")) {
                TSNode nm = c.getChildByFieldName("name");
                if (nm != null && !nm.isNull()) {
                    String s = text(nm, src);
                    if (!s.isEmpty()) action.accept(s);
                }
            }
        }
    }

    // 타입 노드에서 매칭 가능한 심플 클래스명 추출 — type_identifier(대문자 시작)만, 제네릭/배열/primitive는 null
    private String simpleTypeName(TSNode typeNode, byte[] src) {
        if (typeNode == null || typeNode.isNull()) return null;
        if (!typeNode.getType().equals("type_identifier")) return null;
        String raw = text(typeNode, src);
        if (raw.isEmpty() || !Character.isUpperCase(raw.charAt(0))) return null;
        return raw;
    }
}
