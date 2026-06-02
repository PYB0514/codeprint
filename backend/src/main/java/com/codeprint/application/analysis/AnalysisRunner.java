// 분석 비동기 실행 빈 — 자기 호출 문제 방지를 위해 별도 빈으로 분리
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.infrastructure.analysis.*;
import com.codeprint.interfaces.websocket.AnalysisProgressHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisRunner {

    private final AnalysisRepository analysisRepository;
    private final RepoCloner repoCloner;
    private final SourceFileWalker sourceFileWalker;
    private final StaticCodeAnalyzer staticCodeAnalyzer;
    private final GraphBuilder graphBuilder;
    private final AnalysisProgressHandler progressHandler;

    @Async
    @Transactional
    public void run(UUID analysisId, UUID projectId, String githubRepoUrl) {
        Path repoDir = null;
        try {
            // 새 트랜잭션에서 조회 — outer 트랜잭션 커밋 후 확실히 존재
            AnalysisResult analysis = waitForAnalysis(analysisId);

            analysis.start();
            analysisRepository.save(analysis);
            progressHandler.sendProgress(analysisId, 5, "RUNNING");

            log.info("분석 시작: analysisId={}, repo={}", analysisId, githubRepoUrl);

            progressHandler.sendProgress(analysisId, 10, "RUNNING");
            repoDir = repoCloner.clone(githubRepoUrl);
            log.info("클론 완료: {}", repoDir);
            progressHandler.sendProgress(analysisId, 30, "RUNNING");

            List<Path> sourceFiles = sourceFileWalker.walk(repoDir);
            log.info("소스 파일 수: {}", sourceFiles.size());
            progressHandler.sendProgress(analysisId, 40, "RUNNING");

            final Path finalRepoDir = repoDir;
            List<ParsedFile> parsedFiles = sourceFiles.stream()
                    .map(file -> {
                        String lang = LanguageDetector.detect(file.getFileName().toString()).orElse("unknown");
                        try {
                            return staticCodeAnalyzer.analyze(file, finalRepoDir, lang);
                        } catch (Exception e) {
                            log.warn("파일 분석 실패: {}", file, e);
                            return null;
                        }
                    })
                    .filter(pf -> pf != null)
                    .toList();
            progressHandler.sendProgress(analysisId, 70, "RUNNING");

            graphBuilder.build(projectId, analysisId, parsedFiles);
            progressHandler.sendProgress(analysisId, 95, "RUNNING");

            analysis.complete();
            analysisRepository.save(analysis);
            progressHandler.sendProgress(analysisId, 100, "DONE");

            log.info("분석 완료: analysisId={}, files={}", analysisId, parsedFiles.size());

        } catch (Exception e) {
            log.error("분석 실패: analysisId={}", analysisId, e);
            try {
                AnalysisResult analysis = analysisRepository.findById(analysisId).orElse(null);
                if (analysis != null) {
                    analysis.fail(e.getMessage());
                    analysisRepository.save(analysis);
                }
                progressHandler.sendProgress(analysisId, 0, "FAILED");
            } catch (Exception ex) {
                log.error("분석 실패 상태 저장 중 오류", ex);
            }
        } finally {
            if (repoDir != null) {
                repoCloner.deleteDir(repoDir);
            }
        }
    }

    // outer 트랜잭션 커밋 대기 — 최대 3초 재시도
    private AnalysisResult waitForAnalysis(UUID analysisId) throws InterruptedException {
        for (int i = 0; i < 6; i++) {
            var result = analysisRepository.findById(analysisId);
            if (result.isPresent()) return result.get();
            Thread.sleep(500);
        }
        throw new IllegalArgumentException("Analysis not found after retries: " + analysisId);
    }
}
