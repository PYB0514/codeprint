// GitHubApiClient.matchMarkerCommentId 단위 테스트 — 봇 코멘트 upsert 식별 로직 회귀 방지
package com.codeprint.infrastructure.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubApiClientTest {

    private static final String MARKER = "<!-- codeprint-pr-review -->";
    private final ObjectMapper om = new ObjectMapper();

    // JSON 문자열을 JsonNode로 파싱
    private JsonNode parse(String json) {
        try {
            return om.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("마커가 포함된 코멘트의 id를 반환한다")
    void matchMarkerCommentId_found() {
        JsonNode arr = parse("[{\"id\": 100, \"body\": \"사람 코멘트\"},"
                + " {\"id\": 200, \"body\": \"" + MARKER + "\\n## Codeprint 분석\"}]");

        Long id = GitHubApiClient.matchMarkerCommentId(arr, MARKER);

        assertThat(id).isEqualTo(200L);
    }

    @Test
    @DisplayName("마커가 없으면 null을 반환한다 (새 코멘트 작성으로 폴백)")
    void matchMarkerCommentId_notFound() {
        JsonNode arr = parse("[{\"id\": 100, \"body\": \"사람 코멘트\"},"
                + " {\"id\": 200, \"body\": \"또 다른 사람 코멘트\"}]");

        Long id = GitHubApiClient.matchMarkerCommentId(arr, MARKER);

        assertThat(id).isNull();
    }

    @Test
    @DisplayName("빈 배열이면 null을 반환한다")
    void matchMarkerCommentId_emptyArray() {
        Long id = GitHubApiClient.matchMarkerCommentId(parse("[]"), MARKER);

        assertThat(id).isNull();
    }

    @Test
    @DisplayName("마커 코멘트가 여러 개면 첫 번째 id를 반환한다")
    void matchMarkerCommentId_firstMatch() {
        JsonNode arr = parse("[{\"id\": 300, \"body\": \"" + MARKER + " 첫 번째\"},"
                + " {\"id\": 400, \"body\": \"" + MARKER + " 두 번째\"}]");

        Long id = GitHubApiClient.matchMarkerCommentId(arr, MARKER);

        assertThat(id).isEqualTo(300L);
    }

    @Test
    @DisplayName("배열이 아니거나 null이면 null을 반환한다 (방어적)")
    void matchMarkerCommentId_nonArray() {
        assertThat(GitHubApiClient.matchMarkerCommentId(null, MARKER)).isNull();
        assertThat(GitHubApiClient.matchMarkerCommentId(parse("{\"message\": \"Not Found\"}"), MARKER)).isNull();
    }

    @Test
    @DisplayName("extractSnippet — 대상 줄 앞뒤 contextLines만큼 잘라 반환한다")
    void extractSnippet_middleLine_trimsContext() {
        String content = "l1\nl2\nl3\nl4\nl5\nl6\nl7\nl8\nl9\nl10";

        String snippet = GitHubApiClient.extractSnippet(content, 5, 2);

        assertThat(snippet).isEqualTo("l3\nl4\nl5\nl6\nl7");
    }

    @Test
    @DisplayName("extractSnippet — 파일 시작 근처 줄이면 앞쪽을 0으로 클램프한다")
    void extractSnippet_nearStart_clampsToZero() {
        String content = "l1\nl2\nl3\nl4\nl5";

        String snippet = GitHubApiClient.extractSnippet(content, 1, 2);

        assertThat(snippet).isEqualTo("l1\nl2\nl3");
    }

    @Test
    @DisplayName("extractSnippet — content가 null이면 null을 반환한다")
    void extractSnippet_nullContent_returnsNull() {
        assertThat(GitHubApiClient.extractSnippet(null, 5, 2)).isNull();
    }

    @Test
    @DisplayName("extractSnippet — line이 파일 범위를 벗어나면 null을 반환한다")
    void extractSnippet_lineOutOfRange_returnsNull() {
        String content = "l1\nl2\nl3";

        assertThat(GitHubApiClient.extractSnippet(content, 100, 2)).isNull();
    }

    @Test
    @DisplayName("parseOpenPullRequests — number/head.sha/updated_at을 추출한다")
    void parseOpenPullRequests_extractsFields() {
        JsonNode arr = parse("[{\"number\": 42, \"updated_at\": \"2026-07-17T01:00:00Z\","
                + " \"head\": {\"sha\": \"abc123\"}}]");

        var result = GitHubApiClient.parseOpenPullRequests(arr);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).number()).isEqualTo(42);
        assertThat(result.get(0).headSha()).isEqualTo("abc123");
        assertThat(result.get(0).updatedAt()).isEqualTo(java.time.Instant.parse("2026-07-17T01:00:00Z"));
    }

    @Test
    @DisplayName("parseOpenPullRequests — head.sha 누락 항목은 건너뛴다")
    void parseOpenPullRequests_missingHeadSha_skipped() {
        JsonNode arr = parse("[{\"number\": 1, \"updated_at\": \"2026-07-17T01:00:00Z\", \"head\": {}}]");

        var result = GitHubApiClient.parseOpenPullRequests(arr);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("parseOpenPullRequests — 빈 배열이면 빈 목록을 반환한다")
    void parseOpenPullRequests_emptyArray() {
        assertThat(GitHubApiClient.parseOpenPullRequests(parse("[]"))).isEmpty();
    }

    @Test
    @DisplayName("hasContext — 일치하는 context가 있으면 true")
    void hasContext_found() {
        JsonNode arr = parse("[{\"context\": \"other/check\"}, {\"context\": \"codeprint/structure\"}]");

        assertThat(GitHubApiClient.hasContext(arr, "codeprint/structure")).isTrue();
    }

    @Test
    @DisplayName("hasContext — 일치하는 context가 없으면 false")
    void hasContext_notFound() {
        JsonNode arr = parse("[{\"context\": \"other/check\"}]");

        assertThat(GitHubApiClient.hasContext(arr, "codeprint/structure")).isFalse();
    }

    @Test
    @DisplayName("hasContext — 배열이 null이면 false")
    void hasContext_nullArray() {
        assertThat(GitHubApiClient.hasContext(null, "codeprint/structure")).isFalse();
    }
}
