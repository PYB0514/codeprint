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
    // v2(2026-07-10): ParsedFile에 controllerMappingFunctions 필드 추가(API_ENDPOINT 함수 단위 확장) — 미인상 시
    // 구스키마 캐시 JSON을 신스키마 레코드로 역직렬화하다 실패하거나(필드 초과) null 필드로 조용히 깨짐(필드 누락).
    // v3(2026-07-10): 스키마는 그대로지만 controllerMappingFunctions의 "출력 의미"가 바뀜(JS/TS·Python·Go
    // 처리 함수 해소 로직 추가) — 미인상 시 기존에 캐시된 파일은 여전히 구로직 결과(빈 맵)를 반환해 새 코드가
    // 아예 실행되지 않는 채로 조용히 무효화됨(B-16과 같은 부류, 이번엔 역직렬화 실패가 아니라 스테일 값).
    static final int ANALYZER_VERSION = 3;
    private static final Duration CACHE_TTL = Duration.ofDays(30);
    // 미니파이드 번들·생성 파일 등 비정상적으로 큰 파일이 파싱 파이프라인 메모리를 잡아먹는 것 방지 — 이 이상은 분석 제외
    static final long MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024;

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

    // 단일 파일의 digest 계산 — 크기 상한 초과·읽기 실패 시 null(해당 파일만 제외)
    private Digest digest(Path repoDir, Path file) {
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_SIZE_BYTES) {
                log.warn("파일 크기 상한 초과({}byte > {}byte) — 분석 제외: {}", size, MAX_FILE_SIZE_BYTES, file);
                return null;
            }
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
