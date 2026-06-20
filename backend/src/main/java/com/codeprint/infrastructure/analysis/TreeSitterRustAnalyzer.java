// tree-sitter AST로 Rust 함수 정의와 호출을 추출하는 분석기 (정규식보다 정확, 실패 시 폴백)
package com.codeprint.infrastructure.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterRust;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Rust 소스를 tree-sitter로 파싱해 함수명과 함수별 호출 목록을 추출한다.
// 정규식 대비 이점: impl 메서드·trait 시그니처를 정확히 인식하고, 중첩 클로저 안의 호출을 가장 가까운 정의에
// 귀속하며, 주석·문자열·매크로 토큰 속 식별자를 호출로 오인하지 않는다(AST가 토큰 종류를 구분).
class TreeSitterRustAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(TreeSitterRustAnalyzer.class);

    // native 라이브러리(.so/.dll) 로드 실패가 한 번이라도 확인되면 이후 호출은 즉시 폴백
    private volatile boolean nativeUnavailable = false;
    // 언어 핸들은 불변이라 공유 안전 — 최초 1회만 생성(native 로드 트리거)
    private volatile TSLanguage language;

    // tree-sitter 추출 결과 — 함수명 목록과 함수별 호출(callee) 목록
    record Result(List<String> functions, Map<String, List<String>> functionCalls) {}

    // Rust 소스 1개를 파싱해 함수·호출을 추출. native 로드/파싱 실패 시 Optional.empty() → 호출부가 정규식 폴백
    Optional<Result> parse(String content) {
        if (nativeUnavailable) return Optional.empty();
        try {
            TSParser parser = new TSParser();
            parser.setLanguage(language());
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
            log.warn("tree-sitter native 로드 실패 — Rust 분석을 정규식 폴백으로 전환합니다.", e);
            return Optional.empty();
        } catch (RuntimeException e) {
            // 단일 파일 파싱 실패 — 해당 파일만 정규식 폴백(전체 비활성화하지 않음)
            log.warn("tree-sitter Rust 파싱 실패(파일 1건) — 정규식 폴백.", e);
            return Optional.empty();
        }
    }

    // 언어 핸들 lazy 초기화 — 최초 접근 시 native 로드 발생
    private TSLanguage language() {
        TSLanguage local = language;
        if (local == null) {
            synchronized (this) {
                if (language == null) language = new TreeSitterRust();
                local = language;
            }
        }
        return local;
    }

    // 트리를 재귀 순회하며 함수·메서드·trait 시그니처 정의를 수집하고, 호출을 가장 가까운 정의에 귀속
    private void walk(TSNode node, byte[] src, String enclosing,
                      List<String> functions, Map<String, Set<String>> calls) {
        String type = node.getType();
        String current = enclosing;

        // 일반 함수·impl 메서드(function_item)와 trait 메서드 시그니처(function_signature_item) — 둘 다 name 필드 보유
        if (type.equals("function_item") || type.equals("function_signature_item")) {
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

    // call_expression 의 callee 를 호출자(current)에 기록 — bare 식별자 / Path::method / receiver.method
    private void recordCall(TSNode call, byte[] src, String current, Map<String, Set<String>> calls) {
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
            // receiver.method() / self.method() — field 필드가 메서드명
            TSNode field = fn.getChildByFieldName("field");
            if (field == null || field.isNull()) return;
            String method = text(field, src);
            if (!method.isEmpty() && Character.isLowerCase(method.charAt(0))) {
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
