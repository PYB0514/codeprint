// tree-sitter AST로 Ruby 함수 정의와 호출을 추출하는 분석기 (정규식보다 정확, 실패 시 폴백)
package com.codeprint.infrastructure.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterRuby;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Ruby 소스를 tree-sitter로 파싱해 함수명과 함수별 호출 목록을 추출한다.
// 정규식 대비 이점: 싱글톤 메서드(def self.x)를 정확히 인식하고, 블록·중첩 안의 호출을 가장 가까운 정의에
// 귀속하며, 주석·문자열·심볼 속 식별자를 호출로 오인하지 않는다(AST가 토큰 종류를 구분).
// 한계: 인자·수신자 없는 bare 호출(foo)은 Ruby 문법상 지역변수와 구분 불가라 identifier로 파싱돼 호출로 세지 않는다(정규식도 동일).
class TreeSitterRubyAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(TreeSitterRubyAnalyzer.class);

    // native 라이브러리(.so/.dll) 로드 실패가 한 번이라도 확인되면 이후 호출은 즉시 폴백
    private volatile boolean nativeUnavailable = false;
    // 언어 핸들은 불변이라 공유 안전 — 최초 1회만 생성(native 로드 트리거)
    private volatile TSLanguage language;

    // tree-sitter 추출 결과 — 함수명 목록과 함수별 호출(callee) 목록
    record Result(List<String> functions, Map<String, List<String>> functionCalls) {}

    // Ruby 소스 1개를 파싱해 함수·호출을 추출. native 로드/파싱 실패 시 Optional.empty() → 호출부가 정규식 폴백
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
            log.warn("tree-sitter native 로드 실패 — Ruby 분석을 정규식 폴백으로 전환합니다.", e);
            return Optional.empty();
        } catch (RuntimeException e) {
            // 단일 파일 파싱 실패 — 해당 파일만 정규식 폴백(전체 비활성화하지 않음)
            log.warn("tree-sitter Ruby 파싱 실패(파일 1건) — 정규식 폴백.", e);
            return Optional.empty();
        }
    }

    // 언어 핸들 lazy 초기화 — 최초 접근 시 native 로드 발생
    private TSLanguage language() {
        TSLanguage local = language;
        if (local == null) {
            synchronized (this) {
                if (language == null) language = new TreeSitterRuby();
                local = language;
            }
        }
        return local;
    }

    // 트리를 재귀 순회하며 메서드·싱글톤 메서드 정의를 수집하고, 호출을 가장 가까운 정의에 귀속
    private void walk(TSNode node, byte[] src, String enclosing,
                      List<String> functions, Map<String, Set<String>> calls) {
        String type = node.getType();
        String current = enclosing;

        // 일반 메서드(method)와 싱글톤 메서드(def self.x = singleton_method) — 둘 다 name 필드 보유
        if (type.equals("method") || type.equals("singleton_method")) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && !nameNode.isNull()) {
                String name = text(nameNode, src);
                if (!name.isEmpty()) {
                    functions.add(name);
                    current = name;
                }
            }
        } else if (type.equals("call") && current != null) {
            recordCall(node, src, current, calls);
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            walk(node.getChild(i), src, current, functions, calls);
        }
    }

    // call 의 callee 를 호출자(current)에 기록 — bare 메서드명 또는 Constant::method 형식
    private void recordCall(TSNode call, byte[] src, String current, Map<String, Set<String>> calls) {
        TSNode method = call.getChildByFieldName("method");
        if (method == null || method.isNull()) return;
        String name = text(method, src);
        if (name.isEmpty()) return;

        // 수신자가 상수(Ruby 상수는 항상 대문자 시작 — Logger·User 등)면 "Constant::method" 한정 호출로 기록(Java/Go/C# AST와 동일).
        // self·인스턴스 변수·배열 등 비-상수 수신자, 또는 수신자 없음이면 bare 로 기록 — 로컬 메서드명과 매칭되게.
        TSNode receiver = call.getChildByFieldName("receiver");
        if (receiver != null && !receiver.isNull() && receiver.getType().equals("constant")) {
            String recv = text(receiver, src);
            if (!recv.isEmpty()) {
                add(calls, current, recv + "::" + name);
                return;
            }
        }
        add(calls, current, name);
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
