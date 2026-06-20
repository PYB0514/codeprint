// tree-sitter AST로 PHP 함수 정의와 호출을 추출하는 분석기 (정규식보다 정확, 실패 시 폴백)
package com.codeprint.infrastructure.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterPhp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// PHP 소스를 tree-sitter로 파싱해 함수명과 함수별 호출 목록을 추출한다.
// 정규식 대비 이점: 정규식이 PHP의 ->(인스턴스)·::(정적) 호출을 못 잡던 것을 정확히 인식하고, 중첩 클로저 안의
// 호출을 가장 가까운 정의에 귀속하며, 주석·문자열 속 식별자를 호출로 오인하지 않는다(AST가 토큰 종류를 구분).
class TreeSitterPhpAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(TreeSitterPhpAnalyzer.class);

    // native 라이브러리(.so/.dll) 로드 실패가 한 번이라도 확인되면 이후 호출은 즉시 폴백
    private volatile boolean nativeUnavailable = false;
    // 언어 핸들은 불변이라 공유 안전 — 최초 1회만 생성(native 로드 트리거)
    private volatile TSLanguage language;

    // tree-sitter 추출 결과 — 함수명 목록과 함수별 호출(callee) 목록
    record Result(List<String> functions, Map<String, List<String>> functionCalls) {}

    // PHP 소스 1개를 파싱해 함수·호출을 추출. native 로드/파싱 실패 시 Optional.empty() → 호출부가 정규식 폴백
    Optional<Result> parse(String content) {
        if (nativeUnavailable) return Optional.empty();
        try {
            TSParser parser = new TSParser();
            parser.setLanguage(language());
            TSTree tree = parser.parseString(null, content);

            byte[] src = content.getBytes(StandardCharsets.UTF_8);
            List<String> functions = new ArrayList<>();
            Set<String> functionSet = new LinkedHashSet<>();
            Map<String, Set<String>> calls = new LinkedHashMap<>();
            walk(tree.getRootNode(), src, null, functionSet, calls);
            functions.addAll(functionSet);

            Map<String, List<String>> functionCalls = new LinkedHashMap<>();
            calls.forEach((caller, callees) -> functionCalls.put(caller, new ArrayList<>(callees)));
            return Optional.of(new Result(functions, functionCalls));
        } catch (LinkageError e) {
            // native 미로드 — 환경 전체에서 tree-sitter 비활성화하고 정규식으로 영구 폴백
            nativeUnavailable = true;
            log.warn("tree-sitter native 로드 실패 — PHP 분석을 정규식 폴백으로 전환합니다.", e);
            return Optional.empty();
        } catch (RuntimeException e) {
            // 단일 파일 파싱 실패 — 해당 파일만 정규식 폴백(전체 비활성화하지 않음)
            log.warn("tree-sitter PHP 파싱 실패(파일 1건) — 정규식 폴백.", e);
            return Optional.empty();
        }
    }

    // 언어 핸들 lazy 초기화 — 최초 접근 시 native 로드 발생
    private TSLanguage language() {
        TSLanguage local = language;
        if (local == null) {
            synchronized (this) {
                if (language == null) language = new TreeSitterPhp();
                local = language;
            }
        }
        return local;
    }

    // 트리를 재귀 순회하며 함수·메서드 정의를 수집하고, 호출을 가장 가까운 정의에 귀속
    private void walk(TSNode node, byte[] src, String enclosing,
                      Set<String> functions, Map<String, Set<String>> calls) {
        String type = node.getType();
        String current = enclosing;

        // 최상위 함수(function_definition)와 클래스 메서드(method_declaration) — 둘 다 name 필드 보유
        if (type.equals("function_definition") || type.equals("method_declaration")) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && !nameNode.isNull()) {
                String name = text(nameNode, src);
                if (!name.isEmpty()) {
                    functions.add(name);
                    current = name;
                }
            }
        } else if (current != null) {
            if (type.equals("function_call_expression")) {
                // foo() — function 필드가 단순 name 일 때만 기록(변수 함수 $fn() 등은 제외)
                TSNode fn = node.getChildByFieldName("function");
                if (fn != null && !fn.isNull() && fn.getType().equals("name")) {
                    add(calls, current, text(fn, src));
                }
            } else if (type.equals("member_call_expression") || type.equals("nullsafe_member_call_expression")) {
                // $obj->method() / $obj?->method() — 수신자가 변수라 bare 메서드명으로 기록(로컬 메서드명과 매칭)
                TSNode nameNode = node.getChildByFieldName("name");
                if (nameNode != null && !nameNode.isNull() && nameNode.getType().equals("name")) {
                    add(calls, current, text(nameNode, src));
                }
            } else if (type.equals("scoped_call_expression")) {
                // Class::method() / self::method() — scope 가 대문자 클래스면 "Class::method" 한정, self/static/parent 는 bare
                TSNode nameNode = node.getChildByFieldName("name");
                if (nameNode != null && !nameNode.isNull() && nameNode.getType().equals("name")) {
                    String method = text(nameNode, src);
                    if (!method.isEmpty()) {
                        TSNode scope = node.getChildByFieldName("scope");
                        if (scope != null && !scope.isNull() && scope.getType().equals("name")
                                && !text(scope, src).isEmpty() && Character.isUpperCase(text(scope, src).charAt(0))) {
                            add(calls, current, text(scope, src) + "::" + method);
                        } else {
                            add(calls, current, method);
                        }
                    }
                }
            }
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            walk(node.getChild(i), src, current, functions, calls);
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
