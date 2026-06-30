// 소스 파일을 파싱하되 내용이 안 바뀐 파일은 캐시된 ParsedFile을 재사용 — 변경 파일만 재파싱(incremental)
package com.codeprint.infrastructure.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CachedParsedFileLoader {

    // StaticCodeAnalyzer 출력 의미나 ParsedFile 스키마가 바뀌면 이 값을 올려 기존 캐시를 전부 무효화한다.
    static final int ANALYZER_VERSION = 1;
    private static final Duration CACHE_TTL = Duration.ofDays(30);

    private final StaticCodeAnalyzer staticCodeAnalyzer;
    private final ParsedFileCachePort cache;

    // DB 접근(findAll/saveAll/evict)은 메인 스레드에서만, CPU 작업(해시·파싱)만 병렬 — JPA 세션이 스레드 바운드라 분리 필수.
    public List<ParsedFile> load(UUID projectId, Path repoDir, List<Path> sourceFiles) {
        // 1) 병렬 digest — 파일 읽기 + 상대경로 + 내용해시 (DB 없음)
        List<Digest> digests = sourceFiles.parallelStream()
                .map(file -> digest(repoDir, file))
                .filter(Objects::nonNull)
                .toList();

        // 2) 메인 스레드 배치 조회 — 내용 일치 캐시(hit) 회수
        Map<String, String> pathToHash = new LinkedHashMap<>();
        for (Digest d : digests) pathToHash.put(d.relPath(), d.hash());
        Map<String, ParsedFile> hits = cache.findAll(projectId, ANALYZER_VERSION, pathToHash);

        // 3) miss만 병렬 파싱 (DB 없음 — StaticCodeAnalyzer는 무상태)
        List<Digest> misses = digests.stream()
                .filter(d -> !hits.containsKey(d.relPath()))
                .toList();
        List<CachedParse> fresh = misses.parallelStream()
                .map(d -> parse(projectId, repoDir, d))
                .filter(Objects::nonNull)
                .toList();

        // 4) 메인 스레드 배치 저장 + 미사용 정리
        if (!fresh.isEmpty()) cache.saveAll(projectId, ANALYZER_VERSION, fresh);
        try {
            cache.evictOlderThan(projectId, Instant.now().minus(CACHE_TTL));
        } catch (RuntimeException e) {
            log.warn("파싱 캐시 정리 실패(무시): projectId={}", projectId, e);
        }

        log.info("파싱 캐시: 전체 {}개 중 hit {}, 재파싱(miss) {}", digests.size(), hits.size(), fresh.size());

        // 5) 원래 파일 순서를 보존해 결과 조립 (GraphBuilder 결과 불변)
        Map<String, ParsedFile> byPath = new HashMap<>(hits);
        for (CachedParse cp : fresh) byPath.put(cp.filePath(), cp.parsedFile());
        List<ParsedFile> result = new ArrayList<>(digests.size());
        for (Digest d : digests) {
            ParsedFile pf = byPath.get(d.relPath());
            if (pf != null) result.add(pf);
        }
        return result;
    }

    // 단일 파일의 digest 계산 — 읽기 실패 시 null(해당 파일만 제외)
    private Digest digest(Path repoDir, Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            String relPath = repoDir.relativize(file).toString().replace("\\", "/");
            return new Digest(file, relPath, ContentHash.sha256(bytes));
        } catch (Exception e) {
            log.warn("파일 읽기 실패: {}", file, e);
            return null;
        }
    }

    // miss 파일을 실제 파싱해 캐시 엔트리로 변환 — 파싱 실패 시 null
    private CachedParse parse(UUID projectId, Path repoDir, Digest d) {
        String lang = LanguageDetector.detect(d.file().getFileName().toString()).orElse("unknown");
        try {
            ParsedFile parsed = staticCodeAnalyzer.analyze(d.file(), repoDir, lang);
            return parsed == null ? null : new CachedParse(d.relPath(), d.hash(), parsed);
        } catch (Exception e) {
            log.warn("파일 분석 실패: {}", d.file(), e);
            return null;
        }
    }

    // 파일 + 분석 루트 기준 상대경로 + 내용해시
    private record Digest(Path file, String relPath, String hash) {}
}
