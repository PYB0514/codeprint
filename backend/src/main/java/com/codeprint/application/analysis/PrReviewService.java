// PR 리뷰 — PR head 브랜치를 분석해 구조 경고를 PR 코멘트로 게시 (Tier 0 수동 트리거)
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.analysis.port.WarningDetectionPort;
import com.codeprint.infrastructure.analysis.GraphBuilder;
import com.codeprint.infrastructure.analysis.LanguageDetector;
import com.codeprint.infrastructure.analysis.ParsedFile;
import com.codeprint.infrastructure.analysis.RepoCloner;
import com.codeprint.infrastructure.analysis.SourceFileWalker;
import com.codeprint.infrastructure.analysis.StaticCodeAnalyzer;
import com.codeprint.infrastructure.analysis.WalkResult;
import com.codeprint.infrastructure.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrReviewService {

    private final AnalysisFacade analysisFacade;
    private final WarningDetectionPort warningDetectionPort;
    private final GitHubApiClient gitHubApiClient;
    private final AnalysisRepository analysisRepository;
    private final RepoCloner repoCloner;
    private final SourceFileWalker sourceFileWalker;
    private final StaticCodeAnalyzer staticCodeAnalyzer;
    private final GraphBuilder graphBuilder;

    // PR 리뷰 실행 — 소유권 검증 → PR head 분석 → 경고 감지 → PR 코멘트 게시
    @Transactional
    public Map<String, Object> review(UUID projectId, int prNumber, UUID userId, String githubToken) {
        String repoUrl = analysisFacade.resolveOwnedRepoUrl(projectId, userId);
        String headBranch = gitHubApiClient.fetchPullRequestHeadBranch(repoUrl, prNumber, githubToken);

        UUID graphId = analyzeBranch(projectId, repoUrl, headBranch, githubToken);
        List<Map<String, Object>> warnings = filterSuppressed(projectId, warningDetectionPort.detectWarnings(graphId));

        String body = formatComment(headBranch, warnings);
        String commentUrl = gitHubApiClient.postIssueComment(repoUrl, prNumber, body, githubToken);
        log.info("PR 리뷰 코멘트 게시: repo={}, pr={}, 경고={}", repoUrl, prNumber, warnings.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prNumber", prNumber);
        result.put("headBranch", headBranch);
        result.put("warningCount", warnings.size());
        result.put("commentUrl", commentUrl != null ? commentUrl : "");
        result.put("graphId", graphId.toString());
        return result;
    }

    // 프로젝트에서 숨김 처리된 경고를 PR 코멘트 대상에서 제외
    private List<Map<String, Object>> filterSuppressed(UUID projectId, List<Map<String, Object>> warnings) {
        java.util.Set<String> suppressed = warningDetectionPort.suppressedFingerprints(projectId);
        if (suppressed.isEmpty()) return warnings;
        return warnings.stream()
                .filter(w -> !suppressed.contains(String.valueOf(w.get("fingerprint"))))
                .toList();
    }

    // PR head 브랜치를 동기 분석 — 분석 레코드 생성·그래프 빌드 후 graphId 반환
    private UUID analyzeBranch(UUID projectId, String repoUrl, String branch, String token) {
        AnalysisResult analysis = AnalysisResult.create(projectId, branch);
        analysisRepository.save(analysis);
        Path repoDir = null;
        try {
            analysis.start();
            analysisRepository.save(analysis);

            repoDir = repoCloner.clone(repoUrl, branch);
            WalkResult walk = sourceFileWalker.walk(repoDir);
            final Path root = repoDir;
            List<ParsedFile> parsed = walk.files().stream()
                    .map(f -> {
                        String lang = LanguageDetector.detect(f.getFileName().toString()).orElse("unknown");
                        try {
                            return staticCodeAnalyzer.analyze(f, root, lang);
                        } catch (Exception e) {
                            log.warn("파일 분석 실패: {}", f, e);
                            return null;
                        }
                    })
                    .filter(pf -> pf != null)
                    .toList();

            UUID graphId = graphBuilder.build(projectId, analysis.getId(), parsed, walk.totalEligible()).getId();

            String sha = null;
            try {
                sha = gitHubApiClient.fetchLatestCommitSha(repoUrl, branch, token);
            } catch (Exception ignored) {}
            analysis.complete(sha);
            analysisRepository.save(analysis);
            return graphId;
        } catch (Exception e) {
            analysis.fail(e.getMessage());
            analysisRepository.save(analysis);
            throw new RuntimeException("PR 브랜치 분석 실패: " + branch, e);
        } finally {
            if (repoDir != null) repoCloner.deleteDir(repoDir);
        }
    }

    // 경고 목록을 GitHub 마크다운 코멘트로 변환 — severity별 그룹, 없으면 통과 표시
    static String formatComment(String branch, List<Map<String, Object>> warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🔍 Codeprint 구조 분석 — `").append(branch).append("` 브랜치\n\n");
        if (warnings.isEmpty()) {
            sb.append("✅ 감지된 구조 경고가 없습니다.\n\n");
            sb.append("---\n_Codeprint 자동 분석 · 구조 경고는 참고용입니다._\n");
            return sb.toString();
        }
        sb.append("총 **").append(warnings.size()).append("개**의 구조 경고가 감지되었습니다.\n\n");
        int shown = 0;
        for (String sev : List.of("HIGH", "MEDIUM", "LOW")) {
            List<Map<String, Object>> group = warnings.stream()
                    .filter(w -> sev.equals(String.valueOf(w.get("severity"))))
                    .toList();
            if (group.isEmpty()) continue;
            sb.append("### ").append(severityLabel(sev)).append(" (").append(group.size()).append(")\n");
            for (Map<String, Object> w : group) {
                sb.append("- **").append(w.get("type")).append("** — ").append(w.get("message")).append("\n");
            }
            sb.append("\n");
            shown += group.size();
        }
        // severity가 위 3종에 없는 경고(방어적) — 누락 없이 표시
        if (shown < warnings.size()) {
            sb.append("### 기타\n");
            for (Map<String, Object> w : warnings) {
                String sev = String.valueOf(w.get("severity"));
                if (!List.of("HIGH", "MEDIUM", "LOW").contains(sev)) {
                    sb.append("- **").append(w.get("type")).append("** — ").append(w.get("message")).append("\n");
                }
            }
            sb.append("\n");
        }
        sb.append("---\n_Codeprint 자동 분석 · 구조 경고는 참고용입니다._\n");
        return sb.toString();
    }

    // severity 코드 → 이모지 라벨
    static String severityLabel(String sev) {
        return switch (sev) {
            case "HIGH" -> "🔴 HIGH";
            case "MEDIUM" -> "🟡 MEDIUM";
            default -> "🔵 LOW";
        };
    }
}
