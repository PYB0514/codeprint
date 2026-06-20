// tree-sitter AST로 TypeScript/JavaScript 함수 정의와 호출을 추출하는 분석기 (정규식보다 정확, 실패 시 폴백)
package com.codeprint.infrastructure.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterTsx;
import org.treesitter.TreeSitterTypescript;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// TypeScript 소스를 tree-sitter로 파싱해 함수명과 함수별 호출 목록을 추출한다.
// 정규식 대비 이점: 정규식은 `function` 키워드 없는 클래스 메서드(`name(){}`)를 전혀 못 잡지만 AST는 method_definition으로
// 정확히 인식한다. 중첩/화살표 함수 안의 호출을 가장 가까운 정의에 귀속하고, 주석·문자열 속 식별자를 호출로 오인하지 않는다.
// .tsx(JSX 포함)는 별도 tsx 그래머로 파싱해야 파싱 오류가 없다 — 확장자로 그래머를 고른다.
class TreeSitterTypescriptAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(TreeSitterTypescriptAnalyzer.class);

    // native 라이브러리(.so/.dll) 로드 실패가 한 번이라도 확인되면 이후 호출은 즉시 폴백
    private volatile boolean nativeUnavailable = false;
    // 언어 핸들은 불변이라 공유 안전 — ts/tsx 각각 최초 1회만 생성(native 로드 트리거)
    private volatile TSLanguage tsLanguage;
    private volatile TSLanguage tsxLanguage;

    // tree-sitter 추출 결과 — 함수명 목록과 함수별 호출(callee) 목록
    record Result(List<String> functions, Map<String, List<String>> functionCalls) {}

    // TypeScript 소스 1개를 파싱해 함수·호출을 추출. tsx=true면 JSX 그래머 사용. 실패 시 Optional.empty() → 정규식 폴백
    Optional<Result> parse(String content, boolean tsx) {
        if (nativeUnavailable) return Optional.empty();
        try {
            TSParser parser = new TSParser();
            parser.setLanguage(tsx ? tsxLanguage() : tsLanguage());
            TSTree tree = parser.parseString(null, content);

            byte[] src = content.getBytes(StandardCharsets.UTF_8);
            List<String> functions = new ArrayList<>();
            Map<String, Set<String>> calls = new LinkedHashMap<>();
            // functions 는 raw(중복 포함) 리스트 — 파일 내 동명 정의 수(머지 다중도)를 StaticCodeAnalyzer가 중앙에서 집계/디둡한다.
            walk(tree.getRootNode(), src, null, functions, calls);

            Map<String, List<String>> functionCalls = new LinkedHashMap<>();
            calls.forEach((caller, callees) -> functionCalls.put(caller, new ArrayList<>(callees)));
            return Optional.of(new Result(functions, functionCalls));
        } catch (LinkageError e) {
            // native 미로드 — 환경 전체에서 tree-sitter 비활성화하고 정규식으로 영구 폴백
            nativeUnavailable = true;
            log.warn("tree-sitter native 로드 실패 — TypeScript 분석을 정규식 폴백으로 전환합니다.", e);
            return Optional.empty();
        } catch (RuntimeException e) {
            // 단일 파일 파싱 실패 — 해당 파일만 정규식 폴백(전체 비활성화하지 않음)
            log.warn("tree-sitter TypeScript 파싱 실패(파일 1건) — 정규식 폴백.", e);
            return Optional.empty();
        }
    }

    // ts 언어 핸들 lazy 초기화 — 최초 접근 시 native 로드 발생
    private TSLanguage tsLanguage() {
        TSLanguage local = tsLanguage;
        if (local == null) {
            synchronized (this) {
                if (tsLanguage == null) tsLanguage = new TreeSitterTypescript();
                local = tsLanguage;
            }
        }
        return local;
    }

    // tsx 언어 핸들 lazy 초기화 — .tsx(JSX) 파일용
    private TSLanguage tsxLanguage() {
        TSLanguage local = tsxLanguage;
        if (local == null) {
            synchronized (this) {
                if (tsxLanguage == null) tsxLanguage = new TreeSitterTsx();
                local = tsxLanguage;
            }
        }
        return local;
    }

    // 트리를 재귀 순회하며 함수 정의를 수집하고, 호출을 가장 가까운 정의에 귀속
    private void walk(TSNode node, byte[] src, String enclosing,
                      List<String> functions, Map<String, Set<String>> calls) {
        String type = node.getType();
        String current = enclosing;

        switch (type) {
            // 이름 필드를 직접 가진 정의 — 일반 함수·제너레이터·클래스 메서드·오버로드 시그니처·인터페이스/추상 메서드
            case "function_declaration", "generator_function_declaration", "method_definition",
                 "function_signature", "method_signature", "abstract_method_signature" -> {
                String name = nameOf(node, src);
                if (!name.isEmpty()) {
                    functions.add(name);
                    current = name;
                }
            }
            // 화살표/함수표현식을 바인딩에 대입 — 이름은 바인딩(변수·필드)에서 온다
            case "variable_declarator", "public_field_definition" -> {
                if (isFunctionValue(node.getChildByFieldName("value"))) {
                    String name = nameOf(node, src);
                    if (!name.isEmpty()) {
                        functions.add(name);
                        current = name;
                    }
                }
            }
            // 멤버/식별자에 함수표현식 대입 — CommonJS의 핵심 패턴(exports.foo=function(){}, Proto.prototype.bar=function(){})
            case "assignment_expression" -> {
                if (isFunctionValue(node.getChildByFieldName("right"))) {
                    String name = assignmentTargetName(node.getChildByFieldName("left"), src);
                    if (!name.isEmpty()) {
                        functions.add(name);
                        current = name;
                    }
                }
            }
            case "call_expression" -> {
                if (current != null) recordCall(node, src, current, calls);
            }
            default -> { /* 그 외 노드는 순회만 */ }
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            walk(node.getChild(i), src, current, functions, calls);
        }
    }

    // value 노드가 화살표 함수/함수 표현식인지 — 바인딩을 함수 정의로 볼지 판정
    private boolean isFunctionValue(TSNode value) {
        if (value == null || value.isNull()) return false;
        String t = value.getType();
        return t.equals("arrow_function") || t.equals("function_expression") || t.equals("function");
    }

    // 대입 좌변에서 함수 이름 추출 — 멤버 대입(obj.foo / A.prototype.bar)은 끝 속성명, 단순 식별자는 그 이름
    private String assignmentTargetName(TSNode left, byte[] src) {
        if (left == null || left.isNull()) return "";
        String t = left.getType();
        if (t.equals("identifier")) return text(left, src);
        if (t.equals("member_expression")) {
            TSNode prop = left.getChildByFieldName("property");
            if (prop != null && !prop.isNull()) return text(prop, src);
        }
        return ""; // 그 외(subscript 등)는 안정적 이름이 없어 제외
    }

    // 노드의 name 필드 텍스트 — 식별자(identifier/property_identifier)일 때만 (구조분해 패턴 등 제외)
    private String nameOf(TSNode node, byte[] src) {
        TSNode nameNode = node.getChildByFieldName("name");
        if (nameNode == null || nameNode.isNull()) return "";
        String t = nameNode.getType();
        if (!t.equals("identifier") && !t.equals("property_identifier")) return "";
        return text(nameNode, src);
    }

    // call_expression 의 callee 를 호출자(current)에 기록 — bare 식별자 또는 Receiver.method 형식
    private void recordCall(TSNode call, byte[] src, String current, Map<String, Set<String>> calls) {
        TSNode fn = call.getChildByFieldName("function");
        if (fn == null || fn.isNull()) return;
        String fnType = fn.getType();

        if (fnType.equals("identifier")) {
            // foo() — 호출은 호출(TS 인스턴스화는 new_expression이라 대소문자 필터 불필요)
            add(calls, current, text(fn, src));
        } else if (fnType.equals("member_expression")) {
            // obj.method() / Receiver.method() — 메서드명은 property 필드
            TSNode prop = fn.getChildByFieldName("property");
            if (prop == null || prop.isNull()) return;
            String method = text(prop, src);
            if (method.isEmpty()) return;
            // 수신자가 대문자 단순 식별자(Class.method)면 "Class::method" 한정 호출만 기록(Java/Python AST와 동일).
            TSNode obj = fn.getChildByFieldName("object");
            if (obj != null && !obj.isNull() && obj.getType().equals("identifier")
                    && !text(obj, src).isEmpty() && Character.isUpperCase(text(obj, src).charAt(0))) {
                add(calls, current, text(obj, src) + "::" + method);
            } else {
                add(calls, current, method);
            }
        }
    }

    // callee 를 호출자 집합에 추가 (자기 이름 호출=재귀는 제외 — DEAD_CODE 오탐 방지)
    private void add(Map<String, Set<String>> calls, String current, String callee) {
        if (!callee.isEmpty() && !callee.equals(current)) {
            calls.computeIfAbsent(current, k -> new LinkedHashSet<>()).add(callee);
        }
    }

    // 노드의 UTF-8 바이트 범위로 텍스트 추출 (한글 등 멀티바이트 안전)
    private String text(TSNode node, byte[] src) {
        int s = node.getStartByte();
        int e = node.getEndByte();
        if (s < 0 || e > src.length || s >= e) return "";
        return new String(src, s, e - s, StandardCharsets.UTF_8);
    }
}
