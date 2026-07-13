// tree-sitter AST로 Swift 함수 정의와 호출을 추출하는 분석기 (정규식보다 정확, 실패 시 폴백)
package com.codeprint.infrastructure.analysis;

import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterSwift;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Swift 소스를 tree-sitter로 파싱해 함수명과 함수별 호출 목록을 추출한다.
// 정규식 대비 이점: 생성자(init)·프로토콜 메서드를 정확히 인식하고, 중첩 클로저 안의 호출을 가장 가까운 정의에
// 귀속하며, 주석·문자열 보간(\(expr)) 속 식별자를 호출로 오인하지 않는다(AST가 토큰 종류를 구분).
class TreeSitterSwiftAnalyzer extends AbstractTreeSitterAnalyzer {

    // tree-sitter 추출 결과 — 함수명 목록과 함수별 호출(callee) 목록, 함수명→정의 시작 줄(1-indexed), 함수명→식별자 시작 컬럼(0-indexed)
    record Result(List<String> functions, Map<String, List<String>> functionCalls,
                  Map<String, Integer> functionLines, Map<String, Integer> functionColumns) {}

    @Override
    protected TSLanguage createLanguage() {
        return new TreeSitterSwift();
    }

    @Override
    protected String languageName() {
        return "Swift";
    }

    // Swift 소스 1개를 파싱해 함수·호출을 추출. native 로드/파싱 실패 시 Optional.empty() → 호출부가 정규식 폴백
    Optional<Result> parse(String content) {
        return parseTree(content, (root, src) -> {
            List<String> functions = new ArrayList<>();
            Map<String, Set<String>> calls = new LinkedHashMap<>();
            // functions 는 raw(중복 포함) 리스트 — 파일 내 동명 정의 수(머지 다중도)를 StaticCodeAnalyzer가 중앙에서 집계/디둡한다.
            // 함수명 → 첫 정의의 시작 줄(1-indexed)·식별자 시작 컬럼(0-indexed) — VS Code 인라인 경고용
            Map<String, Integer> functionLines = new LinkedHashMap<>();
            Map<String, Integer> functionColumns = new LinkedHashMap<>();
            walk(root, src, null, functions, calls, functionLines, functionColumns);

            Map<String, List<String>> functionCalls = new LinkedHashMap<>();
            calls.forEach((caller, callees) -> functionCalls.put(caller, new ArrayList<>(callees)));
            return new Result(functions, functionCalls, functionLines, functionColumns);
        });
    }

    // 트리를 재귀 순회하며 함수·생성자·프로토콜 메서드 정의를 수집하고, 호출을 가장 가까운 정의에 귀속
    private void walk(TSNode node, byte[] src, String enclosing,
                      List<String> functions, Map<String, Set<String>> calls,
                      Map<String, Integer> functionLines, Map<String, Integer> functionColumns) {
        String type = node.getType();
        String current = enclosing;

        // 일반 함수·프로토콜 메서드 시그니처 — 첫 simple_identifier 자식이 이름
        if (type.equals("function_declaration") || type.equals("protocol_function_declaration")) {
            TSNode nameNode = childOfType(node, "simple_identifier");
            if (nameNode != null) {
                String name = text(nameNode, src);
                if (!name.isEmpty()) {
                    functions.add(name);
                    functionLines.putIfAbsent(name, nameNode.getStartPoint().getRow() + 1);
                    functionColumns.putIfAbsent(name, nameNode.getStartPoint().getColumn());
                    current = name;
                }
            }
        } else if (type.equals("init_declaration")) {
            // 생성자 — Swift는 Type(...) 형태로 호출되나 정의 노드명은 init 으로 기록(정규식이 못 잡던 것)
            // "init" 키워드 자체(anonymous 토큰)를 식별자 위치로 사용 — modifier(public/required 등)가 앞에 붙어도 정확한 위치
            TSNode initTok = childOfType(node, "init");
            functions.add("init");
            functionLines.putIfAbsent("init", (initTok != null ? initTok : node).getStartPoint().getRow() + 1);
            functionColumns.putIfAbsent("init", (initTok != null ? initTok : node).getStartPoint().getColumn());
            current = "init";
        } else if (type.equals("call_expression") && current != null) {
            recordCall(node, src, current, calls);
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            walk(node.getChild(i), src, current, functions, calls, functionLines, functionColumns);
        }
    }

    // call_expression 의 callee 를 호출자(current)에 기록 — bare 식별자 또는 navigation_expression(수신자.메서드)
    private void recordCall(TSNode call, byte[] src, String current, Map<String, Set<String>> calls) {
        TSNode callee = callee(call);
        if (callee == null) return;
        String calleeType = callee.getType();

        if (calleeType.equals("simple_identifier")) {
            // foo() / Type(...) — bare 호출(인스턴스화도 call_expression 형태). INSTANTIATION 엣지는 별도 추출 경로가 담당.
            add(calls, current, text(callee, src));
        } else if (calleeType.equals("navigation_expression")) {
            // recv.method() — navigation_suffix 의 simple_identifier 가 메서드명
            TSNode suffix = childOfType(callee, "navigation_suffix");
            if (suffix == null) return;
            String method = firstSimpleIdentifier(suffix, src);
            if (method == null) return;
            // 수신자가 대문자 시작 단순 식별자(Type·enum: Logger·User)면 "Type::method" 한정 호출로 기록(Ruby 상수 휴리스틱과 동형).
            // 소문자 변수·self·체인 수신자면 bare 메서드명으로 기록 — 로컬 메서드명과 매칭되게.
            TSNode operand = callee.getChild(0);
            if (operand != null && !operand.isNull() && operand.getType().equals("simple_identifier")) {
                String recv = text(operand, src);
                if (!recv.isEmpty() && Character.isUpperCase(recv.charAt(0))) {
                    add(calls, current, recv + "::" + method);
                    return;
                }
            }
            add(calls, current, method);
        }
    }

    // call_expression 의 호출 대상 노드(simple_identifier 또는 navigation_expression) — call_suffix 등은 건너뜀
    private TSNode callee(TSNode call) {
        int n = call.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = call.getChild(i);
            String t = c.getType();
            if (t.equals("simple_identifier") || t.equals("navigation_expression")) return c;
        }
        return null;
    }

    // 노드의 직계 자식 중 첫 simple_identifier 텍스트 (없으면 null) — 함수명·메서드명 추출용
    private String firstSimpleIdentifier(TSNode node, byte[] src) {
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = node.getChild(i);
            if (c.getType().equals("simple_identifier")) {
                String name = text(c, src);
                if (!name.isEmpty()) return name;
            }
        }
        return null;
    }

    // 노드의 직계 자식 중 지정 타입의 첫 노드 (없으면 null)
    private TSNode childOfType(TSNode node, String type) {
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = node.getChild(i);
            if (c.getType().equals(type)) return c;
        }
        return null;
    }
}
