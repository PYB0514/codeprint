// build/에 파싱 결과를 영속화하는 캐시 — CLI가 매번 새 프로세스로 뜨는 로컬 도구도 재실행 간 캐시를 재사용하게 함
package com.codeprint.tools;

import com.codeprint.infrastructure.analysis.CachedParse;
import com.codeprint.infrastructure.analysis.ParsedFile;
import com.codeprint.infrastructure.analysis.ParsedFileCachePort;
import com.codeprint.infrastructure.analysis.ParsedFileJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FileParsedFileCachePort implements ParsedFileCachePort {

    private static final Path CACHE_FILE = Path.of("build", "codeprint-local", "parse-cache.json");

    private final ParsedFileJsonCodec codec = new ParsedFileJsonCodec();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> store; // key -> encoded ParsedFile JSON

    public FileParsedFileCachePort() {
        store = load();
    }

    // 내용 해시가 일치하는 캐시만 반환 (hit) — InMemoryParsedFileCachePort와 동일 키 스킴
    @Override
    public Map<String, ParsedFile> findAll(UUID projectId, int analyzerVersion, Map<String, String> pathToHash) {
        Map<String, ParsedFile> hits = new HashMap<>();
        pathToHash.forEach((relPath, hash) -> {
            String encoded = store.get(key(projectId, analyzerVersion, relPath, hash));
            if (encoded != null) hits.put(relPath, codec.decode(encoded));
        });
        return hits;
    }

    // 새로 파싱된 결과를 캐시 파일에 병합 후 즉시 디스크에 기록
    @Override
    public void saveAll(UUID projectId, int analyzerVersion, List<CachedParse> entries) {
        if (entries.isEmpty()) return;
        for (CachedParse entry : entries) {
            store.put(key(projectId, analyzerVersion, entry.filePath(), entry.contentHash()), codec.encode(entry.parsedFile()));
        }
        persist();
    }

    // 로컬 CLI 캐시는 build/ 삭제로 언제든 초기화 가능해 별도 만료 정리 불필요
    @Override
    public void evictOlderThan(UUID projectId, Instant cutoff) {
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> load() {
        try {
            if (Files.isRegularFile(CACHE_FILE)) {
                return new HashMap<>(mapper.readValue(Files.readString(CACHE_FILE, StandardCharsets.UTF_8), Map.class));
            }
        } catch (Exception e) {
            // 손상된 캐시 파일은 무시하고 빈 캐시로 새로 시작
        }
        return new HashMap<>();
    }

    private void persist() {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            Files.writeString(CACHE_FILE, mapper.writeValueAsString(store), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("cache write failed (ignored): " + e.getMessage());
        }
    }

    private String key(UUID projectId, int analyzerVersion, String relPath, String hash) {
        return projectId + ":" + analyzerVersion + ":" + relPath + ":" + hash;
    }
}
