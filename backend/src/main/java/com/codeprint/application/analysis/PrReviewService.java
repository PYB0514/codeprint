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
import java.util.Set;
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

    // 봇 코멘트 식별 마커 — upsert 시 기존 Codeprint 코멘트를 찾는 키. GitHub에서 HTML 주석은 렌더되지 않음
    static final String REVIEW_MARKER = "<!-- codeprint-pr-review -->";

    // PR 리뷰 실행 — 소유권 검증 → PR head 분석 → 경고 감지 → PR 코멘트 게시 (LOW 제외)
    @Transactional
    public Map<String, Object> review(UUID projectId, int prNumber, UUID userId, String githubToken) {
        String repoUrl = analysisFacade.resolveOwnedRepoUrl(projectId, userId);
        String headBranch = gitHubApiClient.fetchPullRequestHeadBranch(repoUrl, prNumber, githubToken);

        UUID graphId = analyzeBranch(projectId, repoUrl, headBranch, githubToken);
        List<Map<String, Object>> suppressedFiltered = filterSuppressed(projectId, warningDetectionPort.detectWarnings(graphId));

        // diff-scope — PR이 변경한 파일에 속한 경고만 게시 (조회 실패 시 null → 전체 폴백)
        Set<String> changedFiles = fetchChangedFilesSafe(repoUrl, prNumber, githubToken);
        boolean diffScoped = changedFiles != null;
        List<Map<String, Object>> scoped = scopeToChangedFiles(suppressedFiltered, changedFiles);
        int outOfScopeCount = suppressedFiltered.size() - scoped.size();

        List<Map<String, Object>> warnings = scoped.stream()
                .filter(w -> !"LOW".equals(w.get("severity")))
                .toList();
        int lowFilteredCount = scoped.size() - warnings.size();

        String body = formatComment(headBranch, warnings, lowFilteredCount, outOfScopeCount, diffScoped);
        // 기존 Codeprint 코멘트가 있으면 갱신, 없으면 새로 작성 — 커밋 push마다 봇 코멘트 누적 방지
        String commentUrl = gitHubApiClient.upsertIssueComment(repoUrl, prNumber, body, REVIEW_MARKER, githubToken);
        log.info("PR 리뷰 코멘트 게시: repo={}, pr={}, 게시={}, LOW_생략={}, 변경외_제외={}, diffScope={}",
                repoUrl, prNumber, warnings.size(), lowFilteredCount, outOfScopeCount, diffScoped);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prNumber", prNumber);
        result.put("headBranch", headBranch);
        result.put("warningCount", warnings.size());
        result.put("lowFilteredCount", lowFilteredCount);
        result.put("outOfScopeCount", outOfScopeCount);
        result.put("diffScoped", diffScoped);
        result.put("commentUrl", commentUrl != null ? commentUrl : "");
        result.put("graphId", graphId.toString());
        return result;
    }

    // PR 변경 파일 집합을 조회 — 실패 시 null 반환(리뷰를 깨뜨리지 않고 전체 게시로 폴백)
    private Set<String> fetchChangedFilesSafe(String repoUrl, int prNumber, String githubToken) {
        try {
            return gitHubApiClient.fetchPullRequestChangedFiles(repoUrl, prNumber, githubToken);
        } catch (Exception e) {
            log.warn("PR 변경 파일 조회 실패 — diff-scope 없이 전체 경고 게시로 폴백: pr={}", prNumber, e);
            return null;
        }
    }

    // 경고를 PR 변경 파일에 속한 것만으로 좁힘 — changedFiles가 null(조회 실패)이면 그대로 반환(폴백).
    // file 필드가 없는 경고(위치 미상)는 변경 파일에 귀속할 수 없으므로 diff-scope에서 제외.
    static List<Map<String, Object>> scopeToChangedFiles(List<Map<String, Object>> warnings, Set<String> changedFiles) {
        if (changedFiles == null) return warnings;
        return warnings.stream()
                .filter(w -> changedFiles.contains(String.valueOf(w.get("file"))))
                .toList();
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

    // 경고 목록을 GitHub 마크다운 코멘트로 변환 — severity별 그룹, LOW·변경외 생략 시 안내 포함
    static String formatComment(String branch, List<Map<String, Object>> warnings, int lowExcludedCount,
                                int outOfScopeCount, boolean diffScoped) {
        StringBuilder sb = new StringBuilder();
        // 봇 코멘트 식별 마커 — upsert가 기존 코멘트를 찾는 키(GitHub에서 렌더되지 않음)
        sb.append(REVIEW_MARKER).append("\n");
        sb.append("## 🔍 Codeprint 구조 분석 — `").append(branch).append("` 브랜치");
        if (diffScoped) sb.append(" (이 PR이 변경한 파일 기준)");
        sb.append("\n\n");
        if (warnings.isEmpty()) {
            sb.append(diffScoped
                    ? "✅ 이 PR이 변경한 파일에서 감지된 구조 경고가 없습니다."
                    : "✅ 감지된 구조 경고가 없습니다.");
            if (lowExcludedCount > 0) {
                sb.append(" _(LOW 등급 ").append(lowExcludedCount).append("개는 생략)_");
            }
            sb.append("\n\n");
            if (outOfScopeCount > 0) {
                sb.append("> _변경 외 파일의 구조 경고 ").append(outOfScopeCount)
                        .append("개는 이 PR과 무관하여 제외했습니다._\n\n");
            }
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
                appendWarningLine(sb, w);
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
                    appendWarningLine(sb, w);
                }
            }
            sb.append("\n");
        }
        if (lowExcludedCount > 0) {
            sb.append("> _LOW 등급 경고 ").append(lowExcludedCount).append("개는 참고용으로 PR 코멘트에서 생략됩니다._\n\n");
        }
        if (outOfScopeCount > 0) {
            sb.append("> _변경 외 파일의 구조 경고 ").append(outOfScopeCount)
                    .append("개는 이 PR과 무관하여 제외했습니다._\n\n");
        }
        sb.append("---\n_Codeprint 자동 분석 · 구조 경고는 참고용입니다._\n");
        return sb.toString();
    }

    // backward-compat — LOW 생략 카운트만 받는 오버로드 (diff-scope 미적용)
    static String formatComment(String branch, List<Map<String, Object>> warnings, int lowExcludedCount) {
        return formatComment(branch, warnings, lowExcludedCount, 0, false);
    }

    // backward-compat — 테스트/직접 호출용
    static String formatComment(String branch, List<Map<String, Object>> warnings) {
        return formatComment(branch, warnings, 0, 0, false);
    }

    // 경고 한 줄 렌더 — 발생 파일 경로가 있으면 타입 뒤에 표시해 리뷰어가 위치를 바로 파악
    static void appendWarningLine(StringBuilder sb, Map<String, Object> w) {
        sb.append("- **").append(w.get("type")).append("**");
        Object file = w.get("file");
        if (file != null && !String.valueOf(file).isBlank()) {
            sb.append(" `").append(file).append("`");
        }
        sb.append(" — ").append(w.get("message")).append("\n");
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
