// tree-sitter AST로 C++ 함수 정의와 호출을 추출하는 분석기 (C++는 정규식 미지원이라 AST가 유일한 정밀 경로)
package com.codeprint.infrastructure.analysis;

import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterCpp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// C++ 소스를 tree-sitter로 파싱해 함수명과 함수별 호출 목록을 추출한다.
// C 대비 추가 처리: 클래스 메서드(field_identifier)·아웃오브라인 정의(Foo::bar=qualified_identifier)·연산자
// 오버로드(operator_name)·소멸자(destructor_name)·템플릿/네임스페이스 중첩. 선언자 체인을 풀어 함수명을 찾는다.
// 호출 귀속 규약은 C#/Java AST와 동일: bare 식별자, 대문자 수신자만 "Type::method" 한정(GraphBuilder가 split 매칭).
class TreeSitterCppAnalyzer extends AbstractTreeSitterAnalyzer {

    // 에러 복구 중 키워드가 식별자로 오인되어 함수명/호출로 누출되는 것을 차단(예: 불투명 매크로 뒤 `namespace X {`를
    // function_definition으로 오파싱 → declarator가 identifier "namespace"). 정상 코드의 함수명은 절대 키워드일 수 없다.
    private static final Set<String> CPP_KEYWORDS = Set.of(
            "namespace", "class", "struct", "enum", "union", "template", "typename", "using", "typedef",
            "public", "private", "protected", "friend", "virtual", "explicit", "inline", "static", "extern",
            "const", "constexpr", "consteval", "constinit", "mutable", "volatile", "register", "thread_local",
            "return", "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue",
            "goto", "try", "catch", "throw", "new", "delete", "sizeof", "alignof", "decltype", "this",
            "true", "false", "nullptr", "operator", "void", "auto", "signed", "unsigned"
    );

    // tree-sitter 추출 결과 — 함수명 목록과 함수별 호출(callee) 목록
    record Result(List<String> functions, Map<String, List<String>> functionCalls) {}

    @Override
    protected TSLanguage createLanguage() {
        return new TreeSitterCpp();
    }

    @Override
    protected String languageName() {
        return "C++";
    }

    // C++ 소스 1개를 파싱해 함수·호출을 추출. native 로드/파싱 실패 시 Optional.empty() → 호출부가 빈 결과 폴백
    Optional<Result> parse(String content) {
        return parseTree(content, (root, src) -> {
            List<String> functions = new ArrayList<>();
            Map<String, Set<String>> calls = new LinkedHashMap<>();
            // functions 는 raw(중복 포함) 리스트 — 파일 내 동명 정의 수(머지 다중도)를 StaticCodeAnalyzer가 중앙에서 집계/디둡한다.
            walk(root, src, null, functions, calls);

            Map<String, List<String>> functionCalls = new LinkedHashMap<>();
            calls.forEach((caller, callees) -> functionCalls.put(caller, new ArrayList<>(callees)));
            return new Result(functions, functionCalls);
        });
    }

    // 트리를 재귀 순회하며 함수 정의를 수집하고, 호출을 가장 가까운 정의에 귀속
    private void walk(TSNode node, byte[] src, String enclosing,
                      List<String> functions, Map<String, Set<String>> calls) {
        String type = node.getType();
        String current = enclosing;

        // 함수 정의(본문 있음) — 선언자 체인을 풀어 함수명 추출. 순수 가상 선언(field_declaration)·함수 포인터는 정의가 아니라 제외됨.
        if (type.equals("function_definition")) {
            String name = functionName(node, src);
            if (name != null && !name.isEmpty() && !CPP_KEYWORDS.contains(name)) {
                functions.add(name);
                current = name;
            }
        } else if (type.equals("call_expression") && current != null) {
            String callee = calleeName(node.getChildByFieldName("function"), src);
            if (callee != null) addCall(calls, current, callee);
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            walk(node.getChild(i), src, current, functions, calls);
        }
    }

    // function_definition 의 선언자 체인을 풀어 함수명을 찾는다.
    // 체인: pointer/reference/parenthesized/function_declarator 를 declarator 필드로 내려가며,
    // 리프가 identifier(자유함수·생성자)·field_identifier(클래스 메서드)·operator_name·destructor_name 이면 그 텍스트,
    // qualified_identifier(Foo::bar 아웃오브라인 정의)면 name 필드로 더 내려가 메서드명만 취한다.
    private String functionName(TSNode funcDef, byte[] src) {
        TSNode d = funcDef.getChildByFieldName("declarator");
        int guard = 0;
        while (d != null && !d.isNull() && guard++ < 24) {
            switch (d.getType()) {
                case "pointer_declarator", "reference_declarator",
                     "parenthesized_declarator", "function_declarator" -> {
                    // 보통 inner 선언자는 "declarator" 필드에 있으나, reference_declarator(`& f`)는 필드 태그가 없어
                    // 자식 스캔으로 폴백한다(`&`·타입·parameter_list 등 비-선언자 노드는 건너뜀).
                    TSNode next = d.getChildByFieldName("declarator");
                    d = (next != null && !next.isNull()) ? next : firstDeclaratorChild(d);
                }
                case "qualified_identifier" -> d = d.getChildByFieldName("name");
                case "identifier", "field_identifier", "operator_name", "destructor_name" -> {
                    return text(d, src);
                }
                default -> { return null; }
            }
        }
        return null;
    }

    // 선언자 래퍼의 자식 중 다음 선언자/이름 노드를 찾는다 (필드 태그가 없는 reference_declarator 폴백용)
    private TSNode firstDeclaratorChild(TSNode wrapper) {
        int n = wrapper.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = wrapper.getChild(i);
            switch (c.getType()) {
                case "pointer_declarator", "reference_declarator", "parenthesized_declarator",
                     "function_declarator", "identifier", "field_identifier",
                     "operator_name", "destructor_name", "qualified_identifier" -> {
                    return c;
                }
                default -> { /* &·*·타입·parameter_list 등은 건너뜀 */ }
            }
        }
        return null;
    }

    // call_expression 의 function 노드에서 callee 이름을 결정. 기록 불가(함수 포인터·복합 표현)면 null.
    private String calleeName(TSNode fn, byte[] src) {
        if (fn == null || fn.isNull()) return null;
        switch (fn.getType()) {
            case "identifier" -> {
                // foo() — bare 호출
                return text(fn, src);
            }
            case "field_expression" -> {
                // obj.method() / obj->method() — 메서드명은 field 필드. 대문자 수신자(Type)만 "Type::method" 한정.
                TSNode field = fn.getChildByFieldName("field");
                if (field == null || field.isNull()) return null;
                String method = text(field, src);
                if (method.isEmpty()) return null;
                TSNode recv = fn.getChildByFieldName("argument");
                if (recv != null && !recv.isNull() && recv.getType().equals("identifier")) {
                    String r = text(recv, src);
                    if (!r.isEmpty() && Character.isUpperCase(r.charAt(0))) return r + "::" + method;
                }
                return method;
            }
            case "qualified_identifier" -> {
                // Foo::bar() / ns::func() — 메서드명은 최종 name, scope 가 대문자(클래스/타입)면 한정 호출.
                String method = qualifiedLeafName(fn, src);
                if (method == null || method.isEmpty()) return null;
                TSNode scope = fn.getChildByFieldName("scope");
                if (scope != null && !scope.isNull()) {
                    String s = text(scope, src);
                    if (!s.isEmpty() && Character.isUpperCase(s.charAt(0))) return s + "::" + method;
                }
                return method;
            }
            case "template_function" -> {
                // identity<int>() — name 필드가 실제 함수명 노드(identifier·qualified_identifier)
                return calleeName(fn.getChildByFieldName("name"), src);
            }
            default -> {
                return null;
            }
        }
    }

    // qualified_identifier(중첩 가능: A::B::c)의 최종 메서드명만 추출 — name 필드를 끝까지 따라간다
    private String qualifiedLeafName(TSNode q, byte[] src) {
        TSNode d = q;
        int guard = 0;
        while (d != null && !d.isNull() && guard++ < 24) {
            switch (d.getType()) {
                case "qualified_identifier" -> d = d.getChildByFieldName("name");
                case "template_function" -> d = d.getChildByFieldName("name");
                case "identifier", "field_identifier", "operator_name", "destructor_name" -> {
                    return text(d, src);
                }
                default -> { return null; }
            }
        }
        return null;
    }

    // CPP_KEYWORDS에 걸리는 callee(에러 복구 오파싱 누출)는 제외하고 베이스 add()로 위임 — C++ 고유 가드
    private void addCall(Map<String, Set<String>> calls, String current, String callee) {
        if (!CPP_KEYWORDS.contains(callee)) add(calls, current, callee);
    }
}
