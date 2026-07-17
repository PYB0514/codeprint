// CachedParsedFileLoader 테스트 — hit 재사용 / miss만 재파싱 / 순서 보존 / analyzerVersion 전달
package com.codeprint.infrastructure.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CachedParsedFileLoaderTest {

    @Mock
    private StaticCodeAnalyzer analyzer;
    @Mock
    private ParsedFileCachePort cache;

    private CachedParsedFileLoader loader;

    private final UUID projectId = UUID.randomUUID();

    private CachedParsedFileLoader newLoader() {
        return new CachedParsedFileLoader(analyzer, cache);
    }

    // 전부 miss면 모든 파일을 파싱하고 일괄 저장하며, 입력 순서를 보존한다
    @Test
    @DisplayName("전부 miss — 모든 파일 파싱 + 일괄 저장 + 순서 보존")
    void allMiss_parsesAll_savesAll_preservesOrder() throws Exception {
        loader = newLoader();
        Path a = write(repoDir, "a.java", "class A {}");
        Path b = write(repoDir, "b.java", "class B {}");
        when(cache.findAll(eq(projectId), anyInt(), anyMap())).thenReturn(Map.of());
        when(analyzer.analyze(any(), eq(repoDir), anyString()))
                .thenAnswer(inv -> pf(rel(inv.getArgument(0))));

        List<ParsedFile> result = loader.load(projectId, repoDir, List.of(a, b));

        assertThat(result).extracting(ParsedFile::filePath).containsExactly("a.java", "b.java");
        verify(cache).saveAll(eq(projectId), anyInt(), argThat(list -> list.size() == 2));
        verify(cache).evictOlderThan(eq(projectId), any());
    }

    // hit인 파일은 캐시 결과를 재사용하고 파싱하지 않으며, miss만 파싱·저장한다
    @Test
    @DisplayName("부분 hit — hit는 파싱 안 함, miss만 재파싱·저장")
    void partialHit_reusesCache_parsesOnlyMiss() throws Exception {
        loader = newLoader();
        Path a = write(repoDir, "a.java", "class A {}");
        Path b = write(repoDir, "b.java", "class B {}");
        ParsedFile cachedA = pf("a.java");
        when(cache.findAll(eq(projectId), anyInt(), anyMap())).thenReturn(Map.of("a.java", cachedA));
        when(analyzer.analyze(any(), eq(repoDir), anyString()))
                .thenAnswer(inv -> pf(rel(inv.getArgument(0))));

        List<ParsedFile> result = loader.load(projectId, repoDir, List.of(a, b));

        assertThat(result).extracting(ParsedFile::filePath).containsExactly("a.java", "b.java");
        verify(analyzer, never()).analyze(eq(a), any(), any());
        verify(analyzer, times(1)).analyze(eq(b), any(), any());
        ArgumentCaptor<List<CachedParse>> saved = ArgumentCaptor.forClass(List.class);
        verify(cache).saveAll(eq(projectId), anyInt(), saved.capture());
        assertThat(saved.getValue()).extracting(CachedParse::filePath).containsExactly("b.java");
    }

    // 캐시 조회·저장에 코드의 ANALYZER_VERSION 상수가 그대로 전달된다(버전 변경 시 무효화의 전제)
    @Test
    @DisplayName("ANALYZER_VERSION이 캐시 조회·저장에 전달된다")
    void passesAnalyzerVersion() throws Exception {
        loader = newLoader();
        Path a = write(repoDir, "a.java", "class A {}");
        when(cache.findAll(eq(projectId), anyInt(), anyMap())).thenReturn(Map.of());
        when(analyzer.analyze(any(), any(), anyString())).thenAnswer(inv -> pf(rel(inv.getArgument(0))));

        loader.load(projectId, repoDir, List.of(a));

        verify(cache).findAll(eq(projectId), eq(CachedParsedFileLoader.ANALYZER_VERSION), anyMap());
        verify(cache).saveAll(eq(projectId), eq(CachedParsedFileLoader.ANALYZER_VERSION), anyList());
    }

    // 파일 크기 상한(2MB) 초과 파일은 읽지 않고 제외되며, 정상 파일은 그대로 처리된다
    @Test
    @DisplayName("파일 크기 상한 초과 — 분석에서 제외되고 정상 파일은 영향받지 않는다")
    void oversizedFile_excluded_othersUnaffected() throws Exception {
        loader = newLoader();
        Path small = write(repoDir, "small.java", "class A {}");
        Path huge = repoDir.resolve("huge.java");
        Files.write(huge, new byte[(int) CachedParsedFileLoader.MAX_FILE_SIZE_BYTES + 1]);
        when(cache.findAll(eq(projectId), anyInt(), anyMap())).thenReturn(Map.of());
        when(analyzer.analyze(any(), eq(repoDir), anyString()))
                .thenAnswer(inv -> pf(rel(inv.getArgument(0))));

        List<ParsedFile> result = loader.load(projectId, repoDir, List.of(small, huge));

        assertThat(result).extracting(ParsedFile::filePath).containsExactly("small.java");
        verify(analyzer, never()).analyze(eq(huge), any(), any());
    }

    // ParsedFile 필드 수가 바뀌면 실패 — 이 값을 갱신할 때 반드시 ANALYZER_VERSION도 함께 올릴 것
    // (B-16 재발: 필드만 추가하고 버전 미인상 시 구캐시가 신필드 null 역직렬화 → NPE, serviceCalls로 2회차 발생)
    @Test
    @DisplayName("ParsedFile 필드 수 변경 시 ANALYZER_VERSION 동반 증가를 상기시킨다")
    void parsedFileFieldCount_tripwireForAnalyzerVersion() {
        assertThat(ParsedFile.class.getRecordComponents()).hasSize(30);
    }

    @TempDir
    Path repoDir;

    private String rel(Path file) {
        return repoDir.relativize(file).toString().replace("\\", "/");
    }

    private Path write(Path dir, String name, String content) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, content);
        return f;
    }

    // 파일 경로만 의미 있는 최소 ParsedFile
    private ParsedFile pf(String relPath) {
        return new ParsedFile(relPath, "Java", List.of("f"), List.of(), null, Map.of(), Map.of(),
                List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), List.of());
    }
}
