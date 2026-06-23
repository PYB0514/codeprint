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

    // tree-sitter 추출 결과 — 함수명 목록, 함수별 호출(callee) 목록, 파일이 선언한 타입명 목록
    record Result(List<String> functions, Map<String, List<String>> functionCalls, List<String> declaredTypes) {}

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
            // 파일이 선언한 타입명(Go는 파일명≠타입명이라 Type::method 해소에 필요)을 수집한다.
            List<String> declaredTypes = new ArrayList<>();
            collectDeclaredTypes(root, src, declaredTypes);
            walk(root, src, null, functions, calls, new LinkedHashMap<>());

            Map<String, List<String>> functionCalls = new LinkedHashMap<>();
            calls.forEach((caller, callees) -> functionCalls.put(caller, new ArrayList<>(callees)));
            return new Result(functions, functionCalls, declaredTypes);
        });
    }

    // type_declaration 내부 type_spec/type_alias의 선언 타입명(struct·interface 등)을 수집
    private void collectDeclaredTypes(TSNode node, byte[] src, List<String> declaredTypes) {
        String t = node.getType();
        if (t.equals("type_spec") || t.equals("type_alias")) {
            TSNode nm = node.getChildByFieldName("name");
            if (nm != null && !nm.isNull()) {
                String name = text(nm, src);
                if (!name.isEmpty() && !declaredTypes.contains(name)) declaredTypes.add(name);
            }
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) collectDeclaredTypes(node.getChild(i), src, declaredTypes);
    }

    // 트리를 재귀 순회하며 함수·메서드 정의를 수집하고, 호출을 가장 가까운 정의에 귀속.
    // scope = 현재 위치에서 보이는 변수명→타입(리시버 + 파라미터 + 지역변수). selector_expression 수신자 해소에 사용.
    private void walk(TSNode node, byte[] src, String enclosing,
                      List<String> functions, Map<String, Set<String>> calls,
                      Map<String, String> scope) {
        String type = node.getType();
        String current = enclosing;
        Map<String, String> childScope = scope;

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
            // 함수 스코프 = 새 맵 + 리시버(메서드)·파라미터 (지역변수는 본문 순회 중 추가)
            childScope = new LinkedHashMap<>();
            addParamListTypes(node.getChildByFieldName("receiver"), src, childScope);
            addParamListTypes(node.getChildByFieldName("parameters"), src, childScope);
        } else if (type.equals("short_var_declaration")) {
            // x := Foo{} / x := &Foo{} — 복합 리터럴 대입에서 좌변 변수 타입 등록
            registerShortVarTypes(node, src, scope);
        } else if (type.equals("var_declaration") || type.equals("const_declaration")) {
            // var x Foo — var_spec의 명시 타입 등록
            registerVarSpecTypes(node, src, scope);
        } else if (type.equals("call_expression") && current != null) {
            recordCall(node, src, current, calls, scope);
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            walk(node.getChild(i), src, current, functions, calls, childScope);
        }
    }

    // call_expression 의 callee 를 호출자(current)에 기록 — bare 식별자 또는 Receiver.method 형식
    private void recordCall(TSNode call, byte[] src, String current, Map<String, Set<String>> calls,
                            Map<String, String> scope) {
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
            TSNode operand = fn.getChildByFieldName("operand");
            if (operand != null && !operand.isNull() && operand.getType().equals("identifier")) {
                String recv = text(operand, src);
                String recvType = scope.get(recv);
                if (recvType != null) {
                    // 수신자가 스코프에 있는 변수(리시버 c·파라미터·지역변수) → 선언 타입으로 "Type::method" 한정
                    add(calls, current, recvType + "::" + method);
                    return;
                }
                // 대문자 단순 식별자(Type.Method)면 "Type::method" 한정 호출만 기록(기존 동작 보존).
                // 패키지명은 보통 소문자(fmt·http)라 bare 로 기록 — 로컬 함수명과 거의 충돌하지 않음.
                if (!recv.isEmpty() && Character.isUpperCase(recv.charAt(0))) {
                    add(calls, current, recv + "::" + method);
                    return;
                }
            }
            add(calls, current, method);
        }
    }

    // parameter_list(리시버·파라미터)의 각 선언에서 변수명→심플 타입명을 스코프에 등록
    private void addParamListTypes(TSNode paramList, byte[] src, Map<String, String> scope) {
        if (paramList == null || paramList.isNull()) return;
        int n = paramList.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode p = paramList.getChild(i);
            if (!p.getType().equals("parameter_declaration")) continue;
            String vtype = simpleTypeName(p.getChildByFieldName("type"), src);
            if (vtype == null) continue;
            // 한 선언에 여러 이름(func(a, b *T))이 같은 타입을 공유 — name 필드 외 모든 identifier 자식
            int pn = p.getChildCount();
            for (int j = 0; j < pn; j++) {
                TSNode c = p.getChild(j);
                if (c.getType().equals("identifier")) {
                    String nm = text(c, src);
                    if (!nm.isEmpty()) scope.put(nm, vtype);
                }
            }
        }
    }

    // short_var_declaration(x := Foo{} / &Foo{})의 좌변 변수에 복합 리터럴 타입을 등록
    private void registerShortVarTypes(TSNode node, byte[] src, Map<String, String> scope) {
        TSNode left = node.getChildByFieldName("left");
        TSNode right = node.getChildByFieldName("right");
        if (left == null || left.isNull() || right == null || right.isNull()) return;
        // 좌·우 식 목록을 위치 매칭 (단일 대입이 대부분)
        int ln = left.getChildCount();
        int idx = 0;
        for (int i = 0; i < ln; i++) {
            TSNode lc = left.getChild(i);
            if (!lc.getType().equals("identifier")) continue;
            TSNode rc = nthExpression(right, idx++);
            String vtype = compositeLiteralType(rc, src);
            if (vtype != null) {
                String nm = text(lc, src);
                if (!nm.isEmpty()) scope.put(nm, vtype);
            }
        }
    }

    // var_declaration/const_declaration 내부 var_spec의 명시 타입을 좌변 변수에 등록
    private void registerVarSpecTypes(TSNode node, byte[] src, Map<String, String> scope) {
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode spec = node.getChild(i);
            if (!spec.getType().equals("var_spec") && !spec.getType().equals("const_spec")) continue;
            String vtype = simpleTypeName(spec.getChildByFieldName("type"), src);
            if (vtype == null) continue;
            int sn = spec.getChildCount();
            for (int j = 0; j < sn; j++) {
                TSNode c = spec.getChild(j);
                if (c.getType().equals("identifier")) {
                    String nm = text(c, src);
                    if (!nm.isEmpty()) scope.put(nm, vtype);
                }
            }
        }
    }

    // expression_list의 idx번째 식 (없으면 null)
    private TSNode nthExpression(TSNode exprList, int idx) {
        int n = exprList.getChildCount();
        int count = 0;
        for (int i = 0; i < n; i++) {
            TSNode c = exprList.getChild(i);
            if (c.getType().equals(",")) continue;
            if (count++ == idx) return c;
        }
        return null;
    }

    // 우변이 복합 리터럴(Foo{} / &Foo{})이면 타입명, 아니면 null (함수 호출 반환 타입은 추론 불가)
    private String compositeLiteralType(TSNode expr, byte[] src) {
        if (expr == null || expr.isNull()) return null;
        TSNode lit = expr;
        if (lit.getType().equals("unary_expression")) {
            // &Foo{} — operand가 복합 리터럴
            TSNode operand = lit.getChildByFieldName("operand");
            lit = (operand != null && !operand.isNull()) ? operand : lit;
        }
        if (!lit.getType().equals("composite_literal")) return null;
        return simpleTypeName(lit.getChildByFieldName("type"), src);
    }

    // 타입 노드에서 심플 타입명 추출 — pointer_type(*T) 언래핑, type_identifier만. qualified/slice/map/배열은 null
    private String simpleTypeName(TSNode typeNode, byte[] src) {
        if (typeNode == null || typeNode.isNull()) return null;
        String t = typeNode.getType();
        if (t.equals("pointer_type")) {
            // *Context — 내부 타입으로 언래핑
            TSNode inner = typeNode.getChild(typeNode.getChildCount() - 1);
            return simpleTypeName(inner, src);
        }
        if (t.equals("type_identifier")) {
            String raw = text(typeNode, src);
            return raw.isEmpty() ? null : raw;
        }
        return null;
    }
}
