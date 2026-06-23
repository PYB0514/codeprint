// tree-sitter 언어별 분석기의 공통 골격(파서 설정·native 폴백·텍스트/호출 헬퍼)을 모은 추상 베이스
package com.codeprint.infrastructure.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

// 모든 TreeSitterXxxAnalyzer가 공유하던 보일러플레이트(언어 핸들 lazy 초기화, native 로드 실패 영구 폴백,
// 단일 파일 파싱 실패 폴백, UTF-8 텍스트 추출, 호출 집합 추가)를 한곳에 모은다.
// 언어별로 다른 것(walk 골격·노드 타입 문자열·타입 스코프 추적)은 각 subclass가 보유한다.
abstract class AbstractTreeSitterAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(AbstractTreeSitterAnalyzer.class);

    // native 라이브러리(.so/.dll) 로드 실패가 한 번이라도 확인되면 이후 호출은 즉시 폴백
    private volatile boolean nativeUnavailable = false;
    // 단일 언어 핸들 캐시 — 불변이라 공유 안전, 최초 1회만 생성(native 로드 트리거)
    private volatile TSLanguage cachedLanguage;

    // 이 분석기의 tree-sitter 언어 핸들 생성 — native 로드가 여기서 발생(LinkageError 가능)
    protected abstract TSLanguage createLanguage();

    // 로그 메시지에 쓰는 언어 표시명 (예: "Java", "Go")
    protected abstract String languageName();

    // 단일 언어 핸들 lazy 초기화 — 최초 접근 시 native 로드 발생
    protected final TSLanguage language() {
        TSLanguage local = cachedLanguage;
        if (local == null) {
            synchronized (this) {
                if (cachedLanguage == null) cachedLanguage = createLanguage();
                local = cachedLanguage;
            }
        }
        return local;
    }

    // 단일 언어 분석기용 파싱 템플릿 — language()로 핸들을 얻어 extractor에 (root, src)를 넘긴다
    protected final <T> Optional<T> parseTree(String content, BiFunction<TSNode, byte[], T> extractor) {
        return parseTree(content, this::language, extractor);
    }

    // 파싱 템플릿 — native 폴백을 중앙에서 처리. languageSupplier는 try 안에서 평가(native 로드 LinkageError 포착).
    // extractor가 트리 루트와 UTF-8 바이트로 결과를 만든다. 실패 시 Optional.empty() → 호출부가 폴백(정규식 등).
    protected final <T> Optional<T> parseTree(String content, Supplier<TSLanguage> languageSupplier,
                                              BiFunction<TSNode, byte[], T> extractor) {
        if (nativeUnavailable) return Optional.empty();
        try {
            TSParser parser = new TSParser();
            parser.setLanguage(languageSupplier.get());
            TSTree tree = parser.parseString(null, content);

            byte[] src = content.getBytes(StandardCharsets.UTF_8);
            return Optional.of(extractor.apply(tree.getRootNode(), src));
        } catch (LinkageError e) {
            // native 미로드 — 환경 전체에서 tree-sitter 비활성화하고 영구 폴백
            nativeUnavailable = true;
            log.warn("tree-sitter native 로드 실패 — {} 분석을 폴백으로 전환합니다.", languageName(), e);
            return Optional.empty();
        } catch (RuntimeException e) {
            // 단일 파일 파싱 실패 — 해당 파일만 폴백(전체 비활성화하지 않음)
            log.warn("tree-sitter {} 파싱 실패(파일 1건) — 폴백.", languageName(), e);
            return Optional.empty();
        }
    }

    // callee 를 호출자 집합에 추가 (자기 이름 호출=재귀는 제외 — DEAD_CODE 오탐 방지)
    protected static void add(Map<String, Set<String>> calls, String current, String callee) {
        if (!callee.isEmpty() && !callee.equals(current)) {
            calls.computeIfAbsent(current, k -> new LinkedHashSet<>()).add(callee);
        }
    }

    // 노드의 UTF-8 바이트 범위로 텍스트 추출 (한글 등 멀티바이트 안전)
    protected static String text(TSNode node, byte[] src) {
        int s = node.getStartByte();
        int e = node.getEndByte();
        if (s < 0 || e > src.length || s >= e) return "";
        return new String(src, s, e - s, StandardCharsets.UTF_8);
    }
}
