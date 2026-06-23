// tree-sitter AST로 Go 함수 정의와 호출을 추출하는 분석기 (정규식보다 정확, 실패 시 폴백)
package com.codeprint.infrastructure.analysis;

import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterGo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Go 소스를 tree-sitter로 파싱해 함수명과 함수별 호출 목록을 추출한다.
// 정규식 대비 이점: 리시버 메서드(func (r T) M())를 정확히 인식하고, 중첩 클로저 안의 호출을 가장 가까운 정의에
// 귀속하며, 주석·문자열 리터럴(import 경로 등) 속 식별자를 호출로 오인하지 않는다(AST가 토큰 종류를 구분).
class TreeSitterGoAnalyzer extends AbstractTreeSitterAnalyzer {

    // tree-sitter 추출 결과 — 함수명 목록과 함수별 호출(callee) 목록
    record Result(List<String> functions, Map<String, List<String>> functionCalls) {}

    @Override
    protected TSLanguage createLanguage() {
        return new TreeSitterGo();
    }

    @Override
    protected String languageName() {
        return "Go";
    }

    // Go 소스 1개를 파싱해 함수·호출을 추출. native 로드/파싱 실패 시 Optional.empty() → 호출부가 정규식 폴백
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

    // 트리를 재귀 순회하며 함수·메서드 정의를 수집하고, 호출을 가장 가까운 정의에 귀속
    private void walk(TSNode node, byte[] src, String enclosing,
                      List<String> functions, Map<String, Set<String>> calls) {
        String type = node.getType();
        String current = enclosing;

        // 일반 함수와 리시버 메서드 — 둘 다 name 필드 보유
        if (type.equals("function_declaration") || type.equals("method_declaration")) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && !nameNode.isNull()) {
                String name = text(nameNode, src);
                if (!name.isEmpty()) {
                    functions.add(name);
                    current = name;
                }
            }
        } else if (type.equals("call_expression") && current != null) {
            recordCall(node, src, current, calls);
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            walk(node.getChild(i), src, current, functions, calls);
        }
    }

    // call_expression 의 callee 를 호출자(current)에 기록 — bare 식별자 또는 Receiver.method 형식
    private void recordCall(TSNode call, byte[] src, String current, Map<String, Set<String>> calls) {
        TSNode fn = call.getChildByFieldName("function");
        if (fn == null || fn.isNull()) return;
        String fnType = fn.getType();

        if (fnType.equals("identifier")) {
            // foo() / Foo() — Go는 인스턴스화도 함수 호출 형태(T{} 복합 리터럴은 call_expression 아님)라 그대로 기록
            add(calls, current, text(fn, src));
        } else if (fnType.equals("selector_expression")) {
            // pkg.Func() / obj.Method() / Type.Method() — 메서드명은 field
            TSNode field = fn.getChildByFieldName("field");
            if (field == null || field.isNull()) return;
            String method = text(field, src);
            if (method.isEmpty()) return;
            // 피연산자가 대문자 단순 식별자(Type.Method)면 "Type::method" 한정 호출만 기록(Java/Python/TS AST와 동일).
            // 패키지명은 보통 소문자(fmt·http)라 bare 로 기록 — 로컬 함수명과 거의 충돌하지 않음.
            TSNode operand = fn.getChildByFieldName("operand");
            if (operand != null && !operand.isNull() && operand.getType().equals("identifier")
                    && !text(operand, src).isEmpty() && Character.isUpperCase(text(operand, src).charAt(0))) {
                add(calls, current, text(operand, src) + "::" + method);
            } else {
                add(calls, current, method);
            }
        }
    }
}
