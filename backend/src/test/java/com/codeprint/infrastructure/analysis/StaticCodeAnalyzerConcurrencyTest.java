// StaticCodeAnalyzer 동시성 안전 테스트 — 파싱 병렬화(AnalysisRunner.parallelStream) 결과가 순차와 동일함을 보장
package com.codeprint.infrastructure.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class StaticCodeAnalyzerConcurrencyTest {

    private StaticCodeAnalyzer analyzer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        analyzer = new StaticCodeAnalyzer();
    }

    // 서로 다른 클래스/함수/호출을 가진 N개 자바 파일 생성 (파서가 의미 있게 동작하도록)
    private List<Path> writeManyJavaFiles(int n) throws IOException {
        for (int i = 0; i < n; i++) {
            Path file = tempDir.resolve("Svc" + i + ".java");
            Files.writeString(file, """
                    package com.example;
                    public class Svc%d {
                        private final Helper%d helper = new Helper%d();
                        // 작업 처리
                        public int run%d(int x) {
                            int y = helper.calc%d(x);
                            return validate%d(y);
                        }
                        // 검증
                        private int validate%d(int v) {
                            return v > 0 ? v : 0;
                        }
                    }
                    """.formatted(i, i, i, i, i, i, i));
        }
        return IntStream.range(0, n).mapToObj(i -> tempDir.resolve("Svc" + i + ".java")).toList();
    }

    private ParsedFile analyzeOne(Path file) {
        try {
            return analyzer.analyze(file, tempDir, "Java");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("병렬 파싱 결과가 순차 파싱 결과와 파일별로 동일 (동시 호출 안전)")
    void parallelAnalysisEqualsSequential() throws IOException {
        List<Path> files = writeManyJavaFiles(40);

        Map<Path, ParsedFile> sequential = new LinkedHashMap<>();
        for (Path f : files) {
            sequential.put(f, analyzeOne(f));
        }

        // AnalysisRunner와 동일한 병렬 처리 — 여러 스레드가 같은 analyzer 인스턴스를 동시 호출
        Map<Path, ParsedFile> parallel = files.parallelStream()
                .collect(LinkedHashMap::new, (m, f) -> m.put(f, analyzeOne(f)), Map::putAll);

        assertThat(parallel).hasSize(sequential.size());
        for (Path f : files) {
            // ParsedFile은 record — 필드 단위 equals. 동시성 깨짐 시 함수/호출 누락·혼선으로 불일치.
            assertThat(parallel.get(f))
                    .as("파일 %s의 병렬 결과가 순차와 동일해야 함", f.getFileName())
                    .isEqualTo(sequential.get(f));
        }
    }

    @Test
    @DisplayName("병렬 파싱이 각 파일의 함수·호출을 정확히 추출 (내용 누락 없음)")
    void parallelAnalysisExtractsContent() throws IOException {
        List<Path> files = writeManyJavaFiles(30);

        Map<Path, ParsedFile> parallel = files.parallelStream()
                .collect(LinkedHashMap::new, (m, f) -> m.put(f, analyzeOne(f)), Map::putAll);

        // 각 파일은 run{i}·validate{i} 두 함수를 가짐 — 동시 호출에도 정확히 잡혀야 함
        for (int i = 0; i < files.size(); i++) {
            ParsedFile pf = parallel.get(files.get(i));
            assertThat(pf.functions()).contains("run" + i, "validate" + i);
        }
    }
}
