// 벤치 케이스 픽스처를 StaticCodeAnalyzer→GraphBuilder→GraphWarningService.detect() 풀 파이프라인으로 실행
package com.codeprint.bench;

import com.codeprint.application.graph.GraphWarningService;
import com.codeprint.domain.graph.ArchitectureIntent;
import com.codeprint.domain.graph.Edge;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.graph.Node;
import com.codeprint.infrastructure.analysis.GraphBuilder;
import com.codeprint.infrastructure.analysis.LanguageDetector;
import com.codeprint.infrastructure.analysis.ParsedFile;
import com.codeprint.infrastructure.analysis.SourceFileWalker;
import com.codeprint.infrastructure.analysis.StaticCodeAnalyzer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// BENCH_SPEC.md §0: LocalAnalyzer(캐시/CLI)와 별개로, 벤치는 캐시 없이 매번 순수 파싱→그래프→탐지를 수행한다.
// 목적이 "탐지기 로직"이 아니라 "상류 레이어(분석기 메타 추출·GraphBuilder 배선)까지 포함한 파이프라인 검증"이기 때문.
public final class BenchPipelineRunner {

    private BenchPipelineRunner() {
    }

    // fixtureDir 안의 소스 파일을 전부 분석해 경고 목록을 반환 (intent 없음)
    public static List<Map<String, Object>> run(Path fixtureDir) {
        return run(fixtureDir, null);
    }

    // fixtureDir 안의 소스 파일을 전부 분석해 경고 목록을 반환 (의도 아키텍처 포함)
    public static List<Map<String, Object>> run(Path fixtureDir, ArchitectureIntent intent) {
        UUID projectId = UUID.randomUUID();
        SourceFileWalker walker = new SourceFileWalker();
        List<Path> files;
        try {
            files = walker.walk(fixtureDir).files();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("벤치 픽스처 파일 목록 조회 실패: " + fixtureDir, e);
        }

        StaticCodeAnalyzer analyzer = new StaticCodeAnalyzer();
        List<ParsedFile> parsedFiles = new ArrayList<>();
        for (Path file : files) {
            String lang = LanguageDetector.detect(file.getFileName().toString()).orElse("unknown");
            try {
                ParsedFile parsed = analyzer.analyze(file, fixtureDir, lang);
                if (parsed != null) parsedFiles.add(parsed);
            } catch (Exception e) {
                throw new IllegalStateException("벤치 픽스처 파싱 실패: " + file, e);
            }
        }

        InMemoryGraphRepository repo = new InMemoryGraphRepository();
        GraphBuilder builder = new GraphBuilder(repo);
        Graph graph = builder.build(projectId, UUID.randomUUID(), parsedFiles);
        List<Node> nodes = repo.findNodesByGraphId(graph.getId());
        List<Edge> edges = repo.findEdgesByGraphId(graph.getId());

        GraphWarningService warningService = new GraphWarningService();
        return warningService.detect(nodes, edges, intent);
    }
}
