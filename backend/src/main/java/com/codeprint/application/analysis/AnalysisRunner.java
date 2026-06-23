// 분석 비동기 실행 빈 — 자기 호출 문제 방지를 위해 별도 빈으로 분리
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.infrastructure.analysis.*;
import com.codeprint.infrastructure.github.GitHubApiClient;
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
    private final GitHubApiClient gitHubApiClient;
    private final RepoCloner repoCloner;
    private final SourceFileWalker sourceFileWalker;
    private final StaticCodeAnalyzer staticCodeAnalyzer;
    private final GraphBuilder graphBuilder;
    private final AnalysisProgressHandler progressHandler;

    // 레포 클론 → 파일 수집 → 정적 분석 → 그래프 빌드를 비동기로 실행
    @Async
    @Transactional
    public void run(UUID analysisId, UUID projectId, String githubRepoUrl, String branch, String githubAccessToken) {
        Path repoDir = null;
        try {
            // 새 트랜잭션에서 조회 — outer 트랜잭션 커밋 후 확실히 존재
            AnalysisResult analysis = waitForAnalysis(analysisId);

            analysis.start();
            analysisRepository.save(analysis);
            progressHandler.sendProgress(analysisId, 5, "RUNNING");

            log.info("분석 시작: analysisId={}, repo={}", analysisId, githubRepoUrl);

            progressHandler.sendProgress(analysisId, 10, "RUNNING");
            repoDir = repoCloner.clone(githubRepoUrl, branch);
            log.info("클론 완료: {}", repoDir);
            progressHandler.sendProgress(analysisId, 30, "RUNNING");

            WalkResult walkResult = sourceFileWalker.walk(repoDir);
            List<Path> sourceFiles = walkResult.files();
            log.info("소스 파일 수: {} (전체 대상 {})", sourceFiles.size(), walkResult.totalEligible());
            progressHandler.sendProgress(analysisId, 40, "RUNNING");

            final Path finalRepoDir = repoDir;
            // tree-sitter 파싱은 파일별 독립·CPU 바운드 — 병렬 처리로 대형 레포 파싱 시간 단축.
            // StaticCodeAnalyzer는 호출당 무상태(파서는 호출마다 새로 생성, 가변 인스턴스/정적 필드 없음)라 동시 호출 안전.
            // toList()는 인코딩 순서를 보존하므로 parsedFiles 순서는 순차 처리와 동일(GraphBuilder 결과 불변).
            List<ParsedFile> parsedFiles = sourceFiles.parallelStream()
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

            graphBuilder.build(projectId, analysisId, parsedFiles, walkResult.totalEligible());
            progressHandler.sendProgress(analysisId, 95, "RUNNING");

            // 분석 완료 시점의 브랜치 최신 커밋 SHA 저장
            String commitSha = null;
            try {
                if (branch != null) {
                    commitSha = gitHubApiClient.fetchLatestCommitSha(githubRepoUrl, branch, githubAccessToken);
                }
            } catch (Exception e) {
                log.warn("커밋 SHA 조회 실패 (무시): {}", e.getMessage());
            }

            analysis.complete(commitSha);
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

    // outer 트랜잭션 커밋 대기 후 분석 레코드를 조회 (최대 3초 재시도)
    private AnalysisResult waitForAnalysis(UUID analysisId) throws InterruptedException {
        for (int i = 0; i < 6; i++) {
            var result = analysisRepository.findById(analysisId);
            if (result.isPresent()) return result.get();
            Thread.sleep(500);
        }
        throw new IllegalArgumentException("Analysis not found after retries: " + analysisId);
    }
}
