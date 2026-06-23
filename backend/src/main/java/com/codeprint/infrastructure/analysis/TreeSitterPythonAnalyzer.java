// tree-sitter AST로 Python 함수 정의와 호출을 추출하는 분석기 (정규식보다 정확, 실패 시 폴백)
package com.codeprint.infrastructure.analysis;

import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterPython;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Python 소스를 tree-sitter로 파싱해 함수명과 함수별 호출 목록을 추출한다.
// 정규식 대비 이점: 중첩 함수·메서드 호출을 가장 가까운 정의에 정확히 귀속(정규식은 def 위치 경계로 갈라 오귀속),
// 주석·docstring·문자열 리터럴 속 가짜 식별자를 호출로 오인하지 않는다(AST가 토큰 종류를 구분, B-10 근본 해소).
class TreeSitterPythonAnalyzer extends AbstractTreeSitterAnalyzer {

    // tree-sitter 추출 결과 — 함수명 목록, 함수별 호출(callee) 목록, 파일이 선언한 클래스명 목록
    record Result(List<String> functions, Map<String, List<String>> functionCalls, List<String> declaredTypes) {}

    @Override
    protected TSLanguage createLanguage() {
        return new TreeSitterPython();
    }

    @Override
    protected String languageName() {
        return "Python";
    }

    // Python 소스 1개를 파싱해 함수·호출을 추출. native 로드/파싱 실패 시 Optional.empty() → 호출부가 정규식 폴백
    Optional<Result> parse(String content) {
        return parseTree(content, (root, src) -> {
            List<String> functions = new ArrayList<>();
            Map<String, Set<String>> calls = new LinkedHashMap<>();
            // functions 는 raw(중복 포함) 리스트 — 파일 내 동명 정의 수(머지 다중도)를 StaticCodeAnalyzer가 중앙에서 집계/디둡한다.
            // 파일이 선언한 클래스명(파일명≠클래스명이라 Type::method 해소에 필요) + self.attr 타입(self.x=ClassName()/self.x:Type)을 walk 전에 모은다.
            List<String> declaredTypes = new ArrayList<>();
            Map<String, String> selfFields = new LinkedHashMap<>();
            collectTypesAndSelfFields(root, src, declaredTypes, selfFields);
            walk(root, src, null, functions, calls, selfFields, new LinkedHashMap<>());

            Map<String, List<String>> functionCalls = new LinkedHashMap<>();
            calls.forEach((caller, callees) -> functionCalls.put(caller, new ArrayList<>(callees)));
            return new Result(functions, functionCalls, declaredTypes);
        });
    }

    // 트리를 재귀 순회하며 함수 정의를 수집하고, 호출을 가장 가까운 정의에 귀속.
    // selfFields = self.attr→타입(클래스 전역, pre-pass), scope = 지역변수/파라미터 bare 이름→타입(메서드별).
    private void walk(TSNode node, byte[] src, String enclosing,
                      List<String> functions, Map<String, Set<String>> calls,
                      Map<String, String> selfFields, Map<String, String> scope) {
        String type = node.getType();
        String current = enclosing;
        Map<String, String> childScope = scope;

        // def·async def·메서드 모두 function_definition (async 는 토큰 자식)
        if (type.equals("function_definition")) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && !nameNode.isNull()) {
                String name = text(nameNode, src);
                if (!name.isEmpty()) {
                    functions.add(name);
                    current = name;
                }
            }
            // 메서드 스코프 = 새 맵 + 이 함수의 타입힌트 파라미터(지역변수는 본문 순회 중 추가)
            childScope = new LinkedHashMap<>();
            addParams(node, src, childScope);
        } else if (type.equals("assignment")) {
            // 지역변수 타입 등록 — left=identifier 인 v: Type 또는 v = ClassName(...)
            TSNode left = node.getChildByFieldName("left");
            if (left != null && !left.isNull() && left.getType().equals("identifier")) {
                String vtype = assignedType(node, src);
                if (vtype != null) scope.put(text(left, src), vtype);
            }
        } else if (type.equals("call") && current != null) {
            recordCall(node, src, current, calls, selfFields, scope);
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            walk(node.getChild(i), src, current, functions, calls, selfFields, childScope);
        }
    }

    // call 노드의 callee 를 호출자(current)에 기록 — 수신자 타입을 알면 "Type::method", 모르면 bare
    private void recordCall(TSNode call, byte[] src, String current, Map<String, Set<String>> calls,
                            Map<String, String> selfFields, Map<String, String> scope) {
        TSNode fn = call.getChildByFieldName("function");
        if (fn == null || fn.isNull()) return;
        String fnType = fn.getType();

        if (fnType.equals("identifier")) {
            // foo() — 소문자 시작만 호출로 기록(대문자 시작은 클래스 인스턴스화라 함수 노드와 매칭되지 않음)
            String name = text(fn, src);
            if (!name.isEmpty() && Character.isLowerCase(name.charAt(0))) {
                add(calls, current, name);
            }
        } else if (fnType.equals("attribute")) {
            // obj.method() / Receiver.method() / self.attr.method() — 메서드명은 attribute 필드
            TSNode attr = fn.getChildByFieldName("attribute");
            if (attr == null || attr.isNull()) return;
            String method = text(attr, src);
            if (method.isEmpty() || !Character.isLowerCase(method.charAt(0))) return;
            String recvType = receiverType(fn.getChildByFieldName("object"), src, selfFields, scope);
            add(calls, current, recvType != null ? recvType + "::" + method : method);
        }
    }

    // 호출 수신자(object)에서 타깃 클래스 심플명을 추론 — 못 구하면 null(=bare 호출 유지)
    private String receiverType(TSNode obj, byte[] src, Map<String, String> selfFields, Map<String, String> scope) {
        if (obj == null || obj.isNull()) return null;
        String t = obj.getType();
        if (t.equals("identifier")) {
            String recv = text(obj, src);
            if (recv.isEmpty()) return null;
            // 대문자 단순 식별자 = Class.method() → 클래스명 그대로 (기존 동작 보존)
            if (Character.isUpperCase(recv.charAt(0))) return recv;
            // 소문자 변수 → 선언 타입으로 해소(파라미터/지역변수)
            return scope.get(recv);
        }
        // self.attr.method() — object가 attribute(object=self)면 attr명으로 self 필드 타입 조회
        if (t.equals("attribute")) {
            TSNode inner = obj.getChildByFieldName("object");
            if (inner != null && !inner.isNull() && inner.getType().equals("identifier")
                    && text(inner, src).equals("self")) {
                TSNode a = obj.getChildByFieldName("attribute");
                if (a != null && !a.isNull()) return selfFields.get(text(a, src));
            }
        }
        return null;
    }

    // 클래스 선언명(declaredTypes) + self.attr 타입(self.x=ClassName()/self.x:Type)을 walk 전에 수집
    private void collectTypesAndSelfFields(TSNode node, byte[] src, List<String> declaredTypes, Map<String, String> selfFields) {
        String t = node.getType();
        if (t.equals("class_definition")) {
            TSNode nm = node.getChildByFieldName("name");
            if (nm != null && !nm.isNull()) {
                String name = text(nm, src);
                if (!name.isEmpty() && !declaredTypes.contains(name)) declaredTypes.add(name);
            }
        } else if (t.equals("assignment")) {
            // self.attr = ClassName(...) 또는 self.attr: Type — left=attribute(object=self)
            TSNode left = node.getChildByFieldName("left");
            if (left != null && !left.isNull() && left.getType().equals("attribute")) {
                TSNode lo = left.getChildByFieldName("object");
                TSNode la = left.getChildByFieldName("attribute");
                if (lo != null && !lo.isNull() && lo.getType().equals("identifier") && text(lo, src).equals("self")
                        && la != null && !la.isNull()) {
                    String vtype = assignedType(node, src);
                    if (vtype != null) selfFields.putIfAbsent(text(la, src), vtype);
                }
            }
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) collectTypesAndSelfFields(node.getChild(i), src, declaredTypes, selfFields);
    }

    // 함수 타입힌트 파라미터(typed_parameter/typed_default_parameter)의 이름→타입을 스코프에 등록
    private void addParams(TSNode funcNode, byte[] src, Map<String, String> scope) {
        TSNode params = funcNode.getChildByFieldName("parameters");
        if (params == null || params.isNull()) return;
        int n = params.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode p = params.getChild(i);
            String pt = p.getType();
            if (!pt.equals("typed_parameter") && !pt.equals("typed_default_parameter")) continue;
            String vtype = typeOfTypeNode(p.getChildByFieldName("type"), src);
            // 이름은 typed_parameter의 첫 identifier 자식 (필드명 없음)
            String nm = firstIdentifierText(p, src);
            if (vtype != null && !nm.isEmpty()) scope.put(nm, vtype);
        }
    }

    // 대입에서 타입 추론 — 어노테이션(type 필드) 우선, 없으면 right=ClassName(...) 생성자 호출명
    private String assignedType(TSNode assignment, byte[] src) {
        String fromAnn = typeOfTypeNode(assignment.getChildByFieldName("type"), src);
        if (fromAnn != null) return fromAnn;
        TSNode right = assignment.getChildByFieldName("right");
        if (right != null && !right.isNull() && right.getType().equals("call")) {
            TSNode callee = right.getChildByFieldName("function");
            if (callee != null && !callee.isNull() && callee.getType().equals("identifier")) {
                String s = text(callee, src);
                if (!s.isEmpty() && Character.isUpperCase(s.charAt(0))) return s; // ClassName(...) 인스턴스화
            }
        }
        return null;
    }

    // type 노드(어노테이션)의 심플 클래스명 — 직계 identifier(대문자)만, subscript(Optional[X]·List[X])는 스킵
    private String typeOfTypeNode(TSNode typeNode, byte[] src) {
        if (typeNode == null || typeNode.isNull()) return null;
        int n = typeNode.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = typeNode.getChild(i);
            if (c.getType().equals("identifier")) {
                String s = text(c, src);
                return (!s.isEmpty() && Character.isUpperCase(s.charAt(0))) ? s : null;
            }
        }
        return null;
    }

    // 노드의 첫 identifier 자식 텍스트 (없으면 빈 문자열)
    private String firstIdentifierText(TSNode node, byte[] src) {
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            TSNode c = node.getChild(i);
            if (c.getType().equals("identifier")) return text(c, src);
        }
        return "";
    }
}
