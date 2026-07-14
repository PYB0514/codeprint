// PrReviewService.formatComment 단위 테스트 — PR 코멘트 마크다운 포맷 회귀 방지
package com.codeprint.application.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PrReviewServiceTest {

    private Map<String, Object> warning(String type, String message, String severity) {
        return Map.of("type", type, "message", message, "severity", severity);
    }

    private Map<String, Object> warningInFile(String type, String severity, String file) {
        return Map.of("type", type, "message", type + " msg", "severity", severity, "file", file);
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

    @Test
    @DisplayName("diff-scope — PR이 변경한 파일에 속한 경고만 남기고 나머지는 제외한다")
    void scopeToChangedFiles_keepsOnlyChangedFiles() {
        List<Map<String, Object>> warnings = List.of(
                warningInFile("CYCLIC_IMPORT", "HIGH", "src/A.java"),
                warningInFile("DB_LAYER_BYPASS", "HIGH", "src/B.java"),
                warningInFile("CROSS_DOMAIN_CALL", "MEDIUM", "src/C.java")
        );

        List<Map<String, Object>> scoped = PrReviewService.scopeToChangedFiles(
                warnings, Set.of("src/A.java", "src/C.java"));

        assertThat(scoped).hasSize(2);
        assertThat(scoped).extracting(w -> w.get("file"))
                .containsExactlyInAnyOrder("src/A.java", "src/C.java");
    }

    @Test
    @DisplayName("diff-scope — file 필드가 없는 경고는 변경 파일에 귀속 불가하여 제외한다")
    void scopeToChangedFiles_dropsFilelessWarnings() {
        List<Map<String, Object>> warnings = List.of(
                warning("DEAD_CODE", "위치 미상 안내", "LOW")
        );

        List<Map<String, Object>> scoped = PrReviewService.scopeToChangedFiles(
                warnings, Set.of("src/A.java"));

        assertThat(scoped).isEmpty();
    }

    @Test
    @DisplayName("diff-scope — 변경 파일 조회 실패(null)면 전체를 그대로 반환(폴백)")
    void scopeToChangedFiles_nullFallsBackToAll() {
        List<Map<String, Object>> warnings = List.of(
                warningInFile("CYCLIC_IMPORT", "HIGH", "src/A.java")
        );

        List<Map<String, Object>> scoped = PrReviewService.scopeToChangedFiles(warnings, null);

        assertThat(scoped).hasSize(1);
    }

    @Test
    @DisplayName("diff-scope 코멘트 — 제목에 변경 파일 기준 표기 + 변경외 제외 안내")
    void formatComment_diffScoped_titleAndOutOfScopeNote() {
        List<Map<String, Object>> warnings = List.of(
                warning("CYCLIC_IMPORT", "순환 의존 A↔B", "HIGH")
        );

        String md = PrReviewService.formatComment("main", warnings, 0, 4, true);

        assertThat(md).contains("이 PR이 변경한 파일 기준");
        assertThat(md).contains("변경 외 파일의 구조 경고 4개는 이 PR과 무관하여 제외");
    }

    @Test
    @DisplayName("diff-scope 코멘트 — 변경 파일에 경고가 없으면 그에 맞는 통과 메시지")
    void formatComment_diffScoped_empty_passMessage() {
        String md = PrReviewService.formatComment("main", List.of(), 0, 2, true);

        assertThat(md).contains("이 PR이 변경한 파일에서 감지된 구조 경고가 없습니다");
        assertThat(md).contains("변경 외 파일의 구조 경고 2개");
    }

    @Test
    @DisplayName("upsert 식별 마커 — 모든 코멘트 본문(경고 있음/없음)에 마커를 포함한다")
    void formatComment_includesUpsertMarker() {
        String withWarnings = PrReviewService.formatComment("main",
                List.of(warning("CYCLIC_IMPORT", "순환 의존 A↔B", "HIGH")));
        String empty = PrReviewService.formatComment("main", List.of());

        assertThat(withWarnings).contains(PrReviewService.REVIEW_MARKER);
        assertThat(empty).contains(PrReviewService.REVIEW_MARKER);
    }

    // 0/1/2단계 게이트 등급 — 기본 설정(1단계 켬·2단계 끔, 신규 프로젝트 기본값)
    private static final ProjectGateSettings DEFAULT_SETTINGS = new ProjectGateSettings(true, false);

    @Test
    @DisplayName("게이트 판정 — 0단계(correctness, 미분류 HIGH)는 프로젝트 설정과 무관하게 항상 failure")
    void gateState_correctnessAlwaysGates() {
        List<Map<String, Object>> warnings = List.of(
                warning("MISSING_TRANSACTIONAL_DELETE", "트랜잭션 누락", "HIGH"));
        assertThat(PrReviewService.gateState(warnings, DEFAULT_SETTINGS)).isEqualTo("failure");
        // 1단계·2단계를 전부 꺼도 0단계는 여전히 막는다
        assertThat(PrReviewService.gateState(warnings, new ProjectGateSettings(false, false))).isEqualTo("failure");
    }

    @Test
    @DisplayName("게이트 판정 — 1단계(architecture)는 기본 켬 상태에서 failure")
    void gateState_architectureGatesByDefault() {
        List<Map<String, Object>> warnings = List.of(
                warning("HIGH_FAN_OUT", "호출 8개", "MEDIUM"),
                warning("CROSS_CONTEXT_IMPORT", "경계 위반", "HIGH"));
        assertThat(PrReviewService.gateState(warnings, DEFAULT_SETTINGS)).isEqualTo("failure");
    }

    @Test
    @DisplayName("게이트 판정 — 1단계를 끄면 architecture 위반은 안 막는다(레거시 완충)")
    void gateState_architectureDisabled_noFailure() {
        List<Map<String, Object>> warnings = List.of(
                warning("CROSS_CONTEXT_IMPORT", "경계 위반", "HIGH"));
        ProjectGateSettings architectureOff = new ProjectGateSettings(false, false);
        assertThat(PrReviewService.gateState(warnings, architectureOff)).isEqualTo("success");
    }

    @Test
    @DisplayName("게이트 판정 — 2단계(experimental)는 기본 꺼짐이라 안 막는다")
    void gateState_experimentalDisabledByDefault_noFailure() {
        List<Map<String, Object>> warnings = List.of(
                warning("INTERFACES_IMPORTS_INFRA", "인프라 직접 의존", "HIGH"));
        assertThat(PrReviewService.gateState(warnings, DEFAULT_SETTINGS)).isEqualTo("success");
    }

    @Test
    @DisplayName("게이트 판정 — 2단계를 켜면 experimental 위반도 막는다")
    void gateState_experimentalEnabled_failure() {
        List<Map<String, Object>> warnings = List.of(
                warning("INTERFACES_IMPORTS_INFRA", "인프라 직접 의존", "HIGH"));
        ProjectGateSettings experimentalOn = new ProjectGateSettings(true, true);
        assertThat(PrReviewService.gateState(warnings, experimentalOn)).isEqualTo("failure");
    }

    @Test
    @DisplayName("게이트 판정 — HIGH가 없으면(MEDIUM만/빈 목록) success")
    void gateState_successWithoutHigh() {
        assertThat(PrReviewService.gateState(List.of(
                warning("HIGH_FAN_OUT", "호출 8개", "MEDIUM")), DEFAULT_SETTINGS)).isEqualTo("success");
        assertThat(PrReviewService.gateState(List.of(), DEFAULT_SETTINGS)).isEqualTo("success");
    }
}
