// AdminDigestService.computeDigest 단위 테스트 — 실패율·전일 대비 급변 이상 판정 회귀 방지
package com.codeprint.application.admin;

import com.codeprint.domain.admin.DailyStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDigestServiceTest {

    // computeDigest는 주입 의존성을 쓰지 않으므로 모두 null 주입
    private final AdminDigestService service = new AdminDigestService(null, null, null);
    private final LocalDate date = LocalDate.of(2026, 6, 14);

    private DailyMetrics metrics(int activeUsers, int analysesTotal, int analysesFailed) {
        return new DailyMetrics(0, activeUsers, 0, analysesTotal, analysesFailed, 0, 0L, 0);
    }

    private DailyStats snapshot(int activeUsers, int analysesTotal) {
        return DailyStats.create(date.minusDays(1), 0, activeUsers, 0, analysesTotal, 0, 0, 0L, 0);
    }

    @Test
    @DisplayName("분석 실패율 20% 초과(3/10) — 이상 신호로 감지")
    void failureRateHigh_flagged() {
        Digest d = service.computeDigest(date, metrics(0, 10, 3), null, 0);
        assertThat(d.hasAnomaly()).isTrue();
        assertThat(d.anomalies()).anyMatch(a -> a.contains("분석 실패율"));
    }

    @Test
    @DisplayName("분석 실패율 임계 이하(1/10=10%) — 이상 없음")
    void failureRateLow_notFlagged() {
        Digest d = service.computeDigest(date, metrics(0, 10, 1), null, 0);
        assertThat(d.anomalies()).noneMatch(a -> a.contains("분석 실패율"));
    }

    @Test
    @DisplayName("분석 수가 최소 표본(5) 미만이면 실패율 무시(4/4=100%여도) — 이상 없음")
    void failureRate_belowMinSample_ignored() {
        Digest d = service.computeDigest(date, metrics(0, 4, 4), null, 0);
        assertThat(d.hasAnomaly()).isFalse();
    }

    @Test
    @DisplayName("활성 사용자 전일 대비 -60%(100→40) — 급감 이상 감지")
    void activeUsersDrop_flagged() {
        Digest d = service.computeDigest(date, metrics(40, 0, 0), snapshot(100, 0), 0);
        assertThat(d.anomalies()).anyMatch(a -> a.contains("활성 사용자") && a.contains("급감"));
    }

    @Test
    @DisplayName("전일 기준값이 최소(10) 미만이면 급변 무시(5→0) — 이상 없음")
    void dod_belowMinBase_ignored() {
        Digest d = service.computeDigest(date, metrics(0, 0, 0), snapshot(5, 0), 0);
        assertThat(d.hasAnomaly()).isFalse();
    }

    @Test
    @DisplayName("전일 스냅샷 없음(첫날) — 전일 대비 이상은 발생하지 않음")
    void noYesterday_noDoDAnomaly() {
        Digest d = service.computeDigest(date, metrics(40, 0, 0), null, 0);
        assertThat(d.anomalies()).noneMatch(a -> a.contains("전일 대비"));
    }

    @Test
    @DisplayName("정상 지표 — 이상 신호 0개")
    void allHealthy_noAnomaly() {
        Digest d = service.computeDigest(date, metrics(50, 20, 1), snapshot(48, 22), 0);
        assertThat(d.hasAnomaly()).isFalse();
    }

    @Test
    @DisplayName("미처리 문의가 임계(10) 이상 누적되면 백로그 이상으로 감지")
    void openFeedbackBacklog_flagged() {
        Digest d = service.computeDigest(date, metrics(50, 20, 1), snapshot(48, 22), 12);
        assertThat(d.openFeedback()).isEqualTo(12);
        assertThat(d.anomalies()).anyMatch(a -> a.contains("미처리 문의") && a.contains("12"));
    }

    @Test
    @DisplayName("미처리 문의가 임계 미만(9)이면 백로그 이상 없음")
    void openFeedbackBacklog_belowThreshold_notFlagged() {
        Digest d = service.computeDigest(date, metrics(50, 20, 1), snapshot(48, 22), 9);
        assertThat(d.anomalies()).noneMatch(a -> a.contains("미처리 문의"));
    }
}
