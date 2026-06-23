// tree-sitter AST로 Rust 함수 정의와 호출을 추출하는 분석기 (정규식보다 정확, 실패 시 폴백)
package com.codeprint.infrastructure.analysis;

import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterRust;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Rust 소스를 tree-sitter로 파싱해 함수명과 함수별 호출 목록을 추출한다.
// 정규식 대비 이점: impl 메서드·trait 시그니처를 정확히 인식하고, 중첩 클로저 안의 호출을 가장 가까운 정의에
// 귀속하며, 주석·문자열·매크로 토큰 속 식별자를 호출로 오인하지 않는다(AST가 토큰 종류를 구분).
class TreeSitterRustAnalyzer extends AbstractTreeSitterAnalyzer {

    // tree-sitter 추출 결과 — 함수명 목록, 함수별 호출(callee) 목록, 파일이 선언한 타입명 목록, 테스트 함수명 목록
    record Result(List<String> functions, Map<String, List<String>> functionCalls,
                  List<String> declaredTypes, List<String> testFunctions) {}

    @Override
    protected TSLanguage createLanguage() {
        return new TreeSitterRust();
    }

    @Override
    protected String languageName() {
        return "Rust";
    }

    // Rust 소스 1개를 파싱해 함수·호출을 추출. native 로드/파싱 실패 시 Optional.empty() → 호출부가 정규식 폴백
    Optional<Result> parse(String content) {
        return parseTree(content, (root, src) -> {
            List<String> functions = new ArrayList<>();
            Map<String, Set<String>> calls = new LinkedHashMap<>();
            // functions 는 raw(중복 포함) 리스트 — 파일 내 동명 정의 수(머지 다중도)를 StaticCodeAnalyzer가 중앙에서 집계/디둡한다.
            // 파일이 선언한 타입명(Rust는 파일명≠타입명이라 Type::method 해소에 필요)을 수집한다.
            List<String> declaredTypes = new ArrayList<>();
            List<String> testFunctions = new ArrayList<>();
            collectDeclaredTypes(root, src, declaredTypes);
            walk(root, src, null, functions, calls, new LinkedHashMap<>(), testFunctions, false);

            Map<String, List<String>> functionCalls = new LinkedHashMap<>();
            calls.forEach((caller, callees) -> functionCalls.put(caller, new ArrayList<>(callees)));
            return new Result(functions, functionCalls, declaredTypes, testFunctions);
        });
    }

    // struct/enum/trait/union/type 선언명을 수집 (impl 대상 타입 해소용)
    private void collectDeclaredTypes(TSNode node, byte[] src, List<String> declaredTypes) {
        String t = node.getType();
        if (t.equals("struct_item") || t.equals("enum_item") || t.equals("trait_item")
                || t.equals("union_item") || t.equals("type_item")) {
            TSNode nm = node.getChildByFieldName("name");
            if (nm != null && !nm.isNull()) {
                String name = text(nm, src);
                if (!name.isEmpty() && !declaredTypes.contains(name)) declaredTypes.add(name);
            }
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) collectDeclaredTypes(node.getChild(i), src, declaredTypes);
    }

    // 트리를 재귀 순회하며 함수·메서드·trait 시그니처 정의를 수집하고, 호출을 가장 가까운 정의에 귀속.
    // scope = 현재 위치에서 보이는 변수명→타입(self=impl 대상 타입 + 파라미터 + 지역변수). field_expression 수신자 해소에 사용.
    // inTestCtx = 이 노드가 #[test]/#[cfg(test)] mod 아래의 테스트 코드인지 — 테스트 함수를 HIGH_FAN_OUT에서 제외하기 위함.
    private void walk(TSNode node, byte[] src, String enclosing,
                      List<String> functions, Map<String, Set<String>> calls,
                      Map<String, String> scope, List<String> testFunctions, boolean inTestCtx) {
        String type = node.getType();
        String current = enclosing;
        Map<String, String> childScope = scope;

        if (type.equals("impl_item")) {
            // impl Foo { } / impl Trait for Foo { } — type 필드가 대상 타입. 메서드 본문에서 self가 그 타입.
            String implType = simpleTypeName(node.getChildByFieldName("type"), src);
            if (implType != null) {
                childScope = new LinkedHashMap<>(scope);
                childScope.put("self", implType);
            }
        } else if (type.equals("function_item") || type.equals("function_signature_item")) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && !nameNode.isNull()) {
                String name = text(nameNode, src);
                if (!name.isEmpty()) {
                    functions.add(name);
                    current = name;
                    // #[test] 함수 또는 #[cfg(test)] mod 내부 함수 — HIGH_FAN_OUT 제외 대상(테스트는 setup으로 호출 많음)
                    if (inTestCtx && !testFunctions.contains(name)) testFunctions.add(name);
                }
            }
            // 함수 스코프 = 바깥 스코프(self 등) 복사본 + 타입 명시 파라미터 (지역변수는 본문 순회 중 추가)
            childScope = new LinkedHashMap<>(scope);
            addParamTypes(node.getChildByFieldName("parameters"), src, childScope);
        } else if (type.equals("let_declaration")) {
            // let x: Foo = ... / let x = Foo { ... } — 명시 타입 또는 구조체 표현식에서 좌변 변수 타입 등록
            registerLetType(node, src, scope);
        } else if (type.equals("call_expression") && current != null) {
            recordCall(node, src, current, calls, scope);
        }

        // 자식 순회 — 직전 형제 attribute_item(#[test]·#[cfg(test)]·#[tokio::test])이 다음 함수/모듈을 테스트로 표시
        int n = node.getChildCount();
        boolean pendingTestAttr = false;
        for (int i = 0; i < n; i++) {
            TSNode child = node.getChild(i);
            if (child.getType().equals("attribute_item")) {
                if (text(child, src).contains("test")) pendingTestAttr = true;
                walk(child, src, current, functions, calls, childScope, testFunctions, inTestCtx);
                continue;
            }
            boolean childTestCtx = inTestCtx || pendingTestAttr;
            walk(child, src, current, functions, calls, childScope, testFunctions, childTestCtx);
            pendingTestAttr = false;
        }
    }

    // call_expression 의 callee 를 호출자(current)에 기록 — bare 식별자 / Path::method / receiver.method
    private void recordCall(TSNode call, byte[] src, String current, Map<String, Set<String>> calls,
                            Map<String, String> scope) {
        TSNode fn = call.getChildByFieldName("function");
        if (fn == null || fn.isNull()) return;
        String fnType = fn.getType();

        if (fnType.equals("identifier")) {
            // foo() — 소문자 시작만 호출로 기록(대문자 시작은 튜플 구조체·enum variant 생성자라 함수 노드와 매칭되지 않음)
            String name = text(fn, src);
            if (!name.isEmpty() && Character.isLowerCase(name.charAt(0))) {
                add(calls, current, name);
            }
        } else if (fnType.equals("scoped_identifier")) {
            // Type::method() / module::func() — name 필드가 호출 대상명
            TSNode nameNode = fn.getChildByFieldName("name");
            if (nameNode == null || nameNode.isNull()) return;
            String method = text(nameNode, src);
            if (method.isEmpty() || !Character.isLowerCase(method.charAt(0))) return;
            // path 가 대문자 단순 식별자(Type::method)면 "Type::method" 한정만 기록(Go/Python/TS AST와 동일).
            // 모듈 경로(소문자)·복합 경로면 bare 로 기록 — 로컬 함수명과 매칭되게.
            TSNode path = fn.getChildByFieldName("path");
            if (path != null && !path.isNull() && path.getType().equals("identifier")
                    && !text(path, src).isEmpty() && Character.isUpperCase(text(path, src).charAt(0))) {
                add(calls, current, text(path, src) + "::" + method);
            } else {
                add(calls, current, method);
            }
        } else if (fnType.equals("field_expression")) {
            // receiver.method() / self.method() — field 필드가 메서드명, value 필드가 수신자
            TSNode field = fn.getChildByFieldName("field");
            if (field == null || field.isNull()) return;
            String method = text(field, src);
            if (method.isEmpty() || !Character.isLowerCase(method.charAt(0))) return;
            // 수신자(self·변수)가 스코프에 있으면 선언 타입으로 "Type::method" 한정, 아니면 bare 유지
            String recvType = receiverType(fn.getChildByFieldName("value"), src, scope);
            add(calls, current, recvType != null ? recvType + "::" + method : method);
        }
    }

    // field_expression 수신자(value)에서 타깃 타입명을 추론 — self 또는 스코프 변수, 못 구하면 null(=bare 유지)
    private String receiverType(TSNode value, byte[] src, Map<String, String> scope) {
        if (value == null || value.isNull()) return null;
        String t = value.getType();
        // self.method() — self 노드 또는 identifier "self"
        if (t.equals("self")) return scope.get("self");
        if (t.equals("identifier")) {
            String recv = text(value, src);
            return recv.isEmpty() ? null : scope.get(recv);
        }
        return null;
    }

    // parameters의 타입 명시 파라미터(pattern: Type)에서 변수명→심플 타입명을 스코프에 등록
    private void addParamTypes(TSNode params, byte[] src, Map<String, String> scope) {
        if (params == null || params.isNull()) return;
        int n = params.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode p = params.getChild(i);
            if (!p.getType().equals("parameter")) continue;
            String vtype = simpleTypeName(p.getChildByFieldName("type"), src);
            TSNode pat = p.getChildByFieldName("pattern");
            if (vtype != null && pat != null && !pat.isNull() && pat.getType().equals("identifier")) {
                String nm = text(pat, src);
                if (!nm.isEmpty()) scope.put(nm, vtype);
            }
        }
    }

    // let x: Foo = ... (명시 타입) 또는 let x = Foo { ... } (구조체 표현식)에서 좌변 변수 타입 등록
    private void registerLetType(TSNode node, byte[] src, Map<String, String> scope) {
        TSNode pat = node.getChildByFieldName("pattern");
        if (pat == null || pat.isNull() || !pat.getType().equals("identifier")) return;
        String nm = text(pat, src);
        if (nm.isEmpty()) return;
        String vtype = simpleTypeName(node.getChildByFieldName("type"), src);
        if (vtype == null) {
            // 타입 어노테이션이 없으면 우변 구조체 표현식(Foo { ... })에서 추론
            TSNode value = node.getChildByFieldName("value");
            if (value != null && !value.isNull() && value.getType().equals("struct_expression")) {
                vtype = simpleTypeName(value.getChildByFieldName("name"), src);
            }
        }
        if (vtype != null) scope.put(nm, vtype);
    }

    // 타입 노드에서 심플 타입명 추출 — reference_type(&T·&mut T) 언래핑·generic_type 베이스명·type_identifier만.
    // scoped_type_identifier(module::T)·튜플·슬라이스 등은 null
    private String simpleTypeName(TSNode typeNode, byte[] src) {
        if (typeNode == null || typeNode.isNull()) return null;
        String t = typeNode.getType();
        if (t.equals("reference_type")) {
            // &Foo / &mut Foo — type 필드(또는 마지막 자식)로 언래핑
            TSNode inner = typeNode.getChildByFieldName("type");
            return simpleTypeName(inner != null && !inner.isNull() ? inner : typeNode.getChild(typeNode.getChildCount() - 1), src);
        }
        if (t.equals("generic_type")) {
            // Foo<T> — type 필드가 베이스(type_identifier 또는 scoped). 베이스명만
            return simpleTypeName(typeNode.getChildByFieldName("type"), src);
        }
        if (t.equals("type_identifier")) {
            String raw = text(typeNode, src);
            return raw.isEmpty() ? null : raw;
        }
        return null;
    }
}
