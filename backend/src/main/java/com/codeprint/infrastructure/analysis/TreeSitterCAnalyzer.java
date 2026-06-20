// tree-sitter AST로 C 함수 정의와 호출을 추출하는 분석기 (C는 정규식 미지원이라 AST가 유일한 정밀 경로)
package com.codeprint.infrastructure.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterC;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// C 소스를 tree-sitter로 파싱해 함수명과 함수별 호출 목록을 추출한다.
// C 함수명은 선언자(declarator) 체인에 중첩된다: function_definition → function_declarator → identifier
// (포인터 반환 시 pointer_declarator가 한 겹 더 감싼다). 체인을 풀어 식별자를 찾는다.
// 주석·문자열·매크로 토큰 속 식별자를 호출로 오인하지 않는다(AST가 토큰 종류를 구분).
class TreeSitterCAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(TreeSitterCAnalyzer.class);

    // native 라이브러리(.so/.dll) 로드 실패가 한 번이라도 확인되면 이후 호출은 즉시 폴백
    private volatile boolean nativeUnavailable = false;
    // 언어 핸들은 불변이라 공유 안전 — 최초 1회만 생성(native 로드 트리거)
    private volatile TSLanguage language;

    // tree-sitter 추출 결과 — 함수명 목록과 함수별 호출(callee) 목록
    record Result(List<String> functions, Map<String, List<String>> functionCalls) {}

    // C 소스 1개를 파싱해 함수·호출을 추출. native 로드/파싱 실패 시 Optional.empty() → 호출부가 빈 결과 폴백
    Optional<Result> parse(String content) {
        if (nativeUnavailable) return Optional.empty();
        try {
            TSParser parser = new TSParser();
            parser.setLanguage(language());
            TSTree tree = parser.parseString(null, content);

            byte[] src = content.getBytes(StandardCharsets.UTF_8);
            List<String> functions = new ArrayList<>();
            Map<String, Set<String>> calls = new LinkedHashMap<>();
            walk(tree.getRootNode(), src, null, functions, calls);

            Map<String, List<String>> functionCalls = new LinkedHashMap<>();
            calls.forEach((caller, callees) -> functionCalls.put(caller, new ArrayList<>(callees)));
            return Optional.of(new Result(functions, functionCalls));
        } catch (LinkageError e) {
            // native 미로드 — 환경 전체에서 tree-sitter 비활성화하고 폴백
            nativeUnavailable = true;
            log.warn("tree-sitter native 로드 실패 — C 분석을 폴백으로 전환합니다.", e);
            return Optional.empty();
        } catch (RuntimeException e) {
            // 단일 파일 파싱 실패 — 해당 파일만 폴백(전체 비활성화하지 않음)
            log.warn("tree-sitter C 파싱 실패(파일 1건) — 폴백.", e);
            return Optional.empty();
        }
    }

    // 언어 핸들 lazy 초기화 — 최초 접근 시 native 로드 발생
    private TSLanguage language() {
        TSLanguage local = language;
        if (local == null) {
            synchronized (this) {
                if (language == null) language = new TreeSitterC();
                local = language;
            }
        }
        return local;
    }

    // 트리를 재귀 순회하며 함수 정의를 수집하고, 호출을 가장 가까운 정의에 귀속
    private void walk(TSNode node, byte[] src, String enclosing,
                      List<String> functions, Map<String, Set<String>> calls) {
        String type = node.getType();
        String current = enclosing;

        // 함수 정의(본문 있음) — 선언자 체인을 풀어 함수명 추출. 함수 포인터 선언(declaration)은 정의가 아니라 제외됨.
        if (type.equals("function_definition")) {
            String name = functionName(node, src);
            if (name != null && !name.isEmpty()) {
                functions.add(name);
                current = name;
            }
        } else if (type.equals("call_expression") && current != null) {
            // foo() — function 필드가 단순 식별자일 때만 기록(함수 포인터·복합 표현 호출은 제외)
            TSNode fn = node.getChildByFieldName("function");
            if (fn != null && !fn.isNull() && fn.getType().equals("identifier")) {
                add(calls, current, text(fn, src));
            }
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            walk(node.getChild(i), src, current, functions, calls);
        }
    }

    // function_definition 의 선언자 체인(pointer/function/parenthesized_declarator)을 풀어 함수명 식별자를 찾는다
    private String functionName(TSNode funcDef, byte[] src) {
        TSNode d = funcDef.getChildByFieldName("declarator");
        int guard = 0;
        while (d != null && !d.isNull() && guard++ < 16) {
            if (d.getType().equals("identifier")) return text(d, src);
            d = d.getChildByFieldName("declarator");
        }
        return null;
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
