// 분석 비동기 실행 빈 — 자기 호출 문제 방지를 위해 별도 빈으로 분리
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.infrastructure.analysis.*;
import com.codeprint.infrastructure.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

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
    private final CachedParsedFileLoader cachedParsedFileLoader;
    private final GraphBuilder graphBuilder;

    // 레포 클론 → 파일 수집 → 정적 분석 → 그래프 빌드를 비동기로 실행
    // 메서드 전체를 트랜잭션으로 감싸지 않는다 — git clone·GitHub API 왕복(수십초~수 분)까지 DB 커넥션을 물고
    // 있으면 HikariCP 유휴 커넥션이 그 사이 만료돼 EOFException으로 죽는다(GATE_GAPS.md [G-6]). DB 작업이
    // 필요한 구간(캐시 조회+배치 저장은 CachedParsedFileLoader.load, 그래프 저장은 GraphBuilder.build)만
    // 각자 자체 @Transactional로 좁게 감싸고, 나머지 repository.save() 호출은 Spring Data 기본 자체 트랜잭션에 맡긴다.
    @Async
    public void run(UUID analysisId, UUID projectId, String githubRepoUrl, String branch, String githubAccessToken) {
        Path repoDir = null;
        try {
            // 매 재시도가 각자 자체 트랜잭션(REQUIRED 기본) — outer 트랜잭션 커밋 후 확실히 존재
            AnalysisResult analysis = waitForAnalysis(analysisId);

            analysis.start();
            analysisRepository.save(analysis);

            log.info("분석 시작: analysisId={}, repo={}", analysisId, githubRepoUrl);

            repoDir = repoCloner.clone(githubRepoUrl, branch);
            log.info("클론 완료: {}", repoDir);

            WalkResult walkResult = sourceFileWalker.walk(repoDir);
            List<Path> sourceFiles = walkResult.files();
            log.info("소스 파일 수: {} (전체 대상 {})", sourceFiles.size(), walkResult.totalEligible());

            // 변경된 파일만 재파싱하고 안 바뀐 파일은 캐시된 ParsedFile을 재사용(incremental) — 순서 보존(GraphBuilder 결과 불변)
            List<ParsedFile> parsedFiles = cachedParsedFileLoader.load(projectId, repoDir, sourceFiles);

            graphBuilder.build(projectId, analysisId, parsedFiles, walkResult.totalEligible());

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

            log.info("분석 완료: analysisId={}, files={}", analysisId, parsedFiles.size());

        } catch (Exception e) {
            log.error("분석 실패: analysisId={}", analysisId, e);
            try {
                AnalysisResult analysis = analysisRepository.findById(analysisId).orElse(null);
                if (analysis != null) {
                    analysis.fail(e.getMessage());
                    analysisRepository.save(analysis);
                }
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
