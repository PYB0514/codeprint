// tree-sitter AST로 C# 함수 정의와 호출을 추출하는 분석기 (정규식보다 정확, 실패 시 폴백)
package com.codeprint.infrastructure.analysis;

import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterCSharp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// C# 소스를 tree-sitter로 파싱해 함수명과 함수별 호출 목록을 추출한다.
// 정규식 대비 이점: 로컬 함수·인터페이스 추상 메서드를 정확히 인식하고, 중첩 람다 안의 호출을 가장 가까운 정의에
// 귀속하며, 주석·문자열 리터럴 속 식별자를 호출로 오인하지 않는다(AST가 토큰 종류를 구분).
class TreeSitterCSharpAnalyzer extends AbstractTreeSitterAnalyzer {

    // tree-sitter 추출 결과 — 함수명 목록과 함수별 호출(callee) 목록
    record Result(List<String> functions, Map<String, List<String>> functionCalls) {}

    @Override
    protected TSLanguage createLanguage() {
        return new TreeSitterCSharp();
    }

    @Override
    protected String languageName() {
        return "C#";
    }

    // C# 소스 1개를 파싱해 함수·호출을 추출. native 로드/파싱 실패 시 Optional.empty() → 호출부가 정규식 폴백
    Optional<Result> parse(String content) {
        return parseTree(content, (root, src) -> {
            List<String> functions = new ArrayList<>();
            Map<String, Set<String>> calls = new LinkedHashMap<>();
            // functions 는 raw(중복 포함) 리스트 — 파일 내 동명 정의 수(머지 다중도)를 StaticCodeAnalyzer가 중앙에서 집계/디둡한다.
            // 클래스 필드는 메서드 어디서든 가시(선언 순서 무관)하므로 walk 전에 먼저 타입을 모은다.
            Map<String, String> fieldTypes = new LinkedHashMap<>();
            collectFieldTypes(root, src, fieldTypes);
            walk(root, src, null, functions, calls, fieldTypes);

            Map<String, List<String>> functionCalls = new LinkedHashMap<>();
            calls.forEach((caller, callees) -> functionCalls.put(caller, new ArrayList<>(callees)));
            return new Result(functions, functionCalls);
        });
    }

    // 트리를 재귀 순회하며 메서드/생성자/로컬 함수 정의를 수집하고, 호출을 가장 가까운 정의에 귀속.
    // scope = 현재 위치에서 보이는 변수명→타입(필드 + 파라미터 + 지역변수). 호출 수신자 타입 해소에 사용.
    private void walk(TSNode node, byte[] src, String enclosing,
                      List<String> functions, Map<String, Set<String>> calls,
                      Map<String, String> scope) {
        String type = node.getType();

        // 메서드·생성자·로컬 함수 — 모두 name 필드 보유 (인터페이스 추상 메서드도 method_declaration)
        if (type.equals("method_declaration")
                || type.equals("constructor_declaration")
                || type.equals("local_function_statement")) {
            TSNode nameNode = node.getChildByFieldName("name");
            String rawName = (nameNode != null && !nameNode.isNull()) ? text(nameNode, src) : "";
            // #if/#endif 전처리기 지시문 주변에서 그래머가 노드 경계를 잘못 잡아 공백 섞인 깨진 텍스트가
            // 나오는 경우가 있어(예: "id IgnoreCultureForTypedAttribu") 유효 식별자만 함수로 인정
            String name = isValidIdentifier(rawName) ? rawName : "";
            if (!name.isEmpty()) functions.add(name);
            // 메서드 스코프 = 필드(전역) 복사본 + 이 메서드의 파라미터(+지역변수는 본문 순회 중 추가)
            Map<String, String> methodScope = new LinkedHashMap<>(scope);
            addParameterTypes(node, src, methodScope);
            String current = name.isEmpty() ? enclosing : name;
            int n = node.getChildCount();
            for (int i = 0; i < n; i++) {
                walk(node.getChild(i), src, current, functions, calls, methodScope);
            }
            return;
        }

        if (type.equals("local_declaration_statement")) {
            // 지역변수 선언 — 이후(같은 메서드 스코프) 호출 수신자 해소를 위해 타입 등록 (var=implicit_type는 스킵)
            TSNode varDecl = firstChildOfType(node, "variable_declaration");
            if (varDecl != null) {
                String vtype = simpleTypeName(varDecl.getChildByFieldName("type"), src);
                if (vtype != null) forEachDeclaratorName(varDecl, src, nm -> scope.put(nm, vtype));
            }
        } else if (type.equals("invocation_expression") && enclosing != null) {
            recordCall(node, src, enclosing, calls, scope);
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            walk(node.getChild(i), src, enclosing, functions, calls, scope);
        }
    }

    // invocation_expression 의 callee 를 호출자(current)에 기록 — 수신자 타입을 알면 "Type::method", 모르면 bare
    private void recordCall(TSNode call, byte[] src, String current, Map<String, Set<String>> calls, Map<String, String> scope) {
        TSNode fn = call.getChildByFieldName("function");
        if (fn == null || fn.isNull()) return;
        String fnType = fn.getType();

        if (fnType.equals("identifier")) {
            // Foo() — C#은 메서드명이 PascalCase 관례라 대문자 호출도 기록(new 인스턴스화는 object_creation_expression이라 제외됨)
            add(calls, current, text(fn, src));
        } else if (fnType.equals("member_access_expression")) {
            // obj.Method() / Type.Method() / this._field.Method() — 메서드명은 name 필드
            TSNode nameNode = fn.getChildByFieldName("name");
            if (nameNode == null || nameNode.isNull()) return;
            String method = text(nameNode, src);
            if (method.isEmpty()) return;
            String recvType = receiverType(fn.getChildByFieldName("expression"), src, scope);
            add(calls, current, recvType != null ? recvType + "::" + method : method);
        }
    }

    // 호출 수신자(expression)에서 타깃 클래스 심플명을 추론 — 못 구하면 null(=bare 호출 유지)
    private String receiverType(TSNode expr, byte[] src, Map<String, String> scope) {
        if (expr == null || expr.isNull()) return null;
        String t = expr.getType();
        if (t.equals("identifier")) {
            String recv = text(expr, src);
            if (recv.isEmpty()) return null;
            // 대문자 단순 식별자 = Type.Method() 정적 호출 → 클래스명 그대로 (기존 동작 보존)
            if (Character.isUpperCase(recv.charAt(0))) return recv;
            // 인스턴스 변수(소문자·_field) → 선언 타입으로 해소(필드/파라미터/지역변수)
            return scope.get(recv);
        }
        // this._field.Method() — 내부 member_access의 name이 필드명
        if (t.equals("member_access_expression")) {
            TSNode inner = expr.getChildByFieldName("expression");
            if (inner != null && !inner.isNull() && inner.getType().equals("this_expression")) {
                TSNode nm = expr.getChildByFieldName("name");
                if (nm != null && !nm.isNull()) return scope.get(text(nm, src));
            }
        }
        return null;
    }

    // 클래스 필드 + primary constructor 파라미터에서 변수명→타입 수집 — 메서드 어디서든 가시하므로 walk 전에 모은다
    private void collectFieldTypes(TSNode node, byte[] src, Map<String, String> fieldTypes) {
        String t = node.getType();
        if (t.equals("field_declaration")) {
            // C#은 field_declaration이 variable_declaration을 감싼다 (Java와 달리 한 겹 더)
            TSNode varDecl = firstChildOfType(node, "variable_declaration");
            if (varDecl != null) {
                String vtype = simpleTypeName(varDecl.getChildByFieldName("type"), src);
                if (vtype != null) forEachDeclaratorName(varDecl, src, nm -> fieldTypes.putIfAbsent(nm, vtype));
            }
        } else if (t.equals("class_declaration") || t.equals("record_declaration") || t.equals("struct_declaration")) {
            // C# 12 primary constructor 파라미터(클래스 헤더, 필드명 없는 parameter_list)는 필드처럼 인스턴스 메서드 전역에서 가시
            addParamListTypes(firstChildOfType(node, "parameter_list"), src, fieldTypes);
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            collectFieldTypes(node.getChild(i), src, fieldTypes);
        }
    }

    // 메서드 파라미터의 (이름→타입)을 스코프에 등록 (method_declaration은 parameters 필드로 parameter_list 보유)
    private void addParameterTypes(TSNode methodNode, byte[] src, Map<String, String> scope) {
        addParamListTypes(methodNode.getChildByFieldName("parameters"), src, scope);
    }

    // parameter_list의 각 parameter (이름→타입)을 스코프에 등록
    private void addParamListTypes(TSNode paramList, byte[] src, Map<String, String> scope) {
        if (paramList == null || paramList.isNull()) return;
        int n = paramList.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode p = paramList.getChild(i);
            if (!p.getType().equals("parameter")) continue;
            String vtype = simpleTypeName(p.getChildByFieldName("type"), src);
            TSNode nm = p.getChildByFieldName("name");
            if (vtype != null && nm != null && !nm.isNull()) {
                String s = text(nm, src);
                if (!s.isEmpty()) scope.put(s, vtype);
            }
        }
    }

    // variable_declaration의 모든 variable_declarator 이름에 대해 동작 수행 (Type a, b; 다중 선언 대응)
    private void forEachDeclaratorName(TSNode varDecl, byte[] src, java.util.function.Consumer<String> action) {
        int n = varDecl.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = varDecl.getChild(i);
            if (c.getType().equals("variable_declarator")) {
                TSNode nm = c.getChildByFieldName("name");
                if (nm != null && !nm.isNull()) {
                    String s = text(nm, src);
                    if (!s.isEmpty()) action.accept(s);
                }
            }
        }
    }

    // 타입 노드에서 매칭 가능한 심플 클래스명 추출 — identifier 또는 generic_name(베이스명), nullable(Foo?)은 언래핑.
    // predefined_type(int/string)·implicit_type(var)·qualified_name·배열은 null. PascalCase(대문자)만.
    private String simpleTypeName(TSNode typeNode, byte[] src) {
        if (typeNode == null || typeNode.isNull()) return null;
        String t = typeNode.getType();
        String raw;
        if (t.equals("nullable_type")) {
            // Contributor? / List<Order>? — 내부 타입으로 언래핑(현대 C# nullable 참조 타입 만연)
            TSNode inner = typeNode.getChildByFieldName("type");
            return simpleTypeName(inner != null && !inner.isNull() ? inner : typeNode.getChild(0), src);
        } else if (t.equals("identifier")) {
            raw = text(typeNode, src);
        } else if (t.equals("generic_name")) {
            // IRepository<Order> → 베이스명 IRepository (프로젝트 파일 없으면 매칭 0=phantom 회피, 있으면 정확 연결)
            TSNode base = typeNode.getChild(0);
            raw = (base != null && !base.isNull() && base.getType().equals("identifier")) ? text(base, src) : "";
        } else {
            return null;
        }
        if (raw.isEmpty() || !Character.isUpperCase(raw.charAt(0))) return null;
        return raw;
    }

    // 노드의 직계 자식 중 지정 타입의 첫 노드 (없으면 null)
    private TSNode firstChildOfType(TSNode node, String type) {
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = node.getChild(i);
            if (c.getType().equals(type)) return c;
        }
        return null;
    }
}
