// 코드 분석 시작 및 비동기 처리 애플리케이션 서비스
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;
import com.codeprint.infrastructure.analysis.*;
import com.codeprint.interfaces.websocket.AnalysisProgressHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AnalysisApplicationService {

    private final AnalysisRepository analysisRepository;
    private final ProjectRepository projectRepository;
    private final RepoCloner repoCloner;
    private final SourceFileWalker sourceFileWalker;
    private final StaticCodeAnalyzer staticCodeAnalyzer;
    private final GraphBuilder graphBuilder;
    private final AnalysisProgressHandler progressHandler;

    public AnalysisResult startAnalysis(UUID projectId) {
        AnalysisResult analysis = AnalysisResult.create(projectId);
        analysisRepository.save(analysis);
        runAnalysisAsync(analysis.getId());
        return analysis;
    }

    @Async
    public void runAnalysisAsync(UUID analysisId) {
        Path repoDir = null;
        try {
            AnalysisResult analysis = analysisRepository.findById(analysisId)
                    .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));

            analysis.start();
            analysisRepository.save(analysis);
            progressHandler.sendProgress(analysisId, 5, "RUNNING");

            Project project = projectRepository.findById(analysis.getProjectId())
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            // 1단계: 레포 클론
            progressHandler.sendProgress(analysisId, 10, "RUNNING");
            repoDir = repoCloner.clone(project.getGithubRepoUrl());
            progressHandler.sendProgress(analysisId, 30, "RUNNING");

            // 2단계: 소스 파일 수집
            List<Path> sourceFiles = sourceFileWalker.walk(repoDir);
            progressHandler.sendProgress(analysisId, 40, "RUNNING");

            // 3단계: 파일별 정적 분석
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

            // 4단계: 그래프 저장
            graphBuilder.build(analysis.getProjectId(), analysisId, parsedFiles);
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

    @Transactional(readOnly = true)
    public AnalysisResult getAnalysis(UUID analysisId) {
        return analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
    }
}
