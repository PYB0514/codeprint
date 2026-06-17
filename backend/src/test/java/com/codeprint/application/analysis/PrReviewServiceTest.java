// PrReviewService.formatComment 단위 테스트 — PR 코멘트 마크다운 포맷 회귀 방지
package com.codeprint.application.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrReviewServiceTest {

    private Map<String, Object> warning(String type, String message, String severity) {
        return Map.of("type", type, "message", message, "severity", severity);
    }

    @Test
    @DisplayName("경고가 없으면 통과 메시지를 포함한다")
    void formatComment_noWarnings_passMessage() {
        String md = PrReviewService.formatComment("feature/x", List.of());

        assertThat(md).contains("feature/x");
        assertThat(md).contains("감지된 구조 경고가 없습니다");
    }

    @Test
    @DisplayName("경고를 severity별로 그룹핑하고 총 개수를 표시한다")
    void formatComment_groupsBySeverity() {
        List<Map<String, Object>> warnings = List.of(
                warning("DEAD_CODE", "사용되지 않는 함수 foo", "LOW"),
                warning("CYCLIC_IMPORT", "순환 의존 A↔B", "HIGH"),
                warning("HIGH_FAN_OUT", "호출 8개 bar", "MEDIUM")
        );

        String md = PrReviewService.formatComment("main", warnings);

        assertThat(md).contains("총 **3개**");
        assertThat(md).contains("🔴 HIGH (1)").contains("CYCLIC_IMPORT");
        assertThat(md).contains("🟡 MEDIUM (1)").contains("HIGH_FAN_OUT");
        assertThat(md).contains("🔵 LOW (1)").contains("DEAD_CODE");
    }

    @Test
    @DisplayName("경고에 file이 있으면 코멘트 라인에 발생 파일 경로를 표시한다")
    void formatComment_showsFilePath() {
        List<Map<String, Object>> warnings = List.of(
                Map.of("type", "CYCLIC_IMPORT", "message", "순환 의존 A↔B", "severity", "HIGH", "file", "src/A.java")
        );

        String md = PrReviewService.formatComment("main", warnings);

        assertThat(md).contains("CYCLIC_IMPORT").contains("`src/A.java`");
    }

    @Test
    @DisplayName("file이 없는 경고는 파일 경로 백틱 없이 정상 렌더된다")
    void formatComment_noFile_noBacktickPath() {
        List<Map<String, Object>> warnings = List.of(
                warning("CYCLIC_IMPORT", "순환 의존 A↔B", "HIGH")
        );

        String md = PrReviewService.formatComment("main", warnings);

        assertThat(md).contains("CYCLIC_IMPORT").doesNotContain("` — ");
    }

    @Test
    @DisplayName("severity가 3종에 없는 경고도 '기타'로 누락 없이 표시한다")
    void formatComment_unknownSeverity_listedUnderEtc() {
        List<Map<String, Object>> warnings = List.of(
                warning("WEIRD", "알 수 없는 심각도", "UNKNOWN")
        );

        String md = PrReviewService.formatComment("main", warnings);

        assertThat(md).contains("기타").contains("WEIRD");
    }

    @Test
    @DisplayName("LOW 생략 개수가 있으면 경고 본문 하단에 안내를 포함한다")
    void formatComment_lowExcluded_showsNote() {
        List<Map<String, Object>> warnings = List.of(
                warning("CYCLIC_IMPORT", "순환 의존 A↔B", "HIGH")
        );

        String md = PrReviewService.formatComment("main", warnings, 3);

        assertThat(md).contains("LOW 등급 경고 3개는 참고용으로 PR 코멘트에서 생략됩니다");
        assertThat(md).contains("CYCLIC_IMPORT");
    }

    @Test
    @DisplayName("경고가 없고 LOW만 생략됐으면 통과 메시지에 생략 안내가 붙는다")
    void formatComment_noHighMedium_lowExcluded_passWithNote() {
        String md = PrReviewService.formatComment("main", List.of(), 2);

        assertThat(md).contains("감지된 구조 경고가 없습니다");
        assertThat(md).contains("LOW 등급 2개는 생략");
    }
}
