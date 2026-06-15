// 일일 다이제스트 계산 서비스 — 이상 신호 판정은 순수 로직(TDD 대상), 저장 스냅샷 조회만 도메인 리포 의존
package com.codeprint.application.admin;

import com.codeprint.domain.admin.DailyStats;
import com.codeprint.domain.admin.DailyStatsRepository;
import com.codeprint.infrastructure.admin.AdminMetricsQuery;
import com.codeprint.shared.event.DailyDigestReadyEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminDigestService {

    // 분석 실패율을 신뢰할 최소 분석 수 — 표본이 적으면 비율이 불안정
    static final int MIN_ANALYSES_FOR_RATE = 5;
    // 분석 실패율 경고 임계 — 20% 초과면 엔진 이상 가능성
    static final double FAILURE_RATE_THRESHOLD = 0.20;
    // 전일 대비 급변 임계 — ±50% 벗어나면 이상
    static final double DOD_CHANGE_THRESHOLD = 0.50;
    // 전일 대비 비교 최소 기준값 — 작은 수의 변동은 노이즈라 무시
    static final int DOD_MIN_BASE = 10;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyStatsRepository dailyStatsRepository;
    private final AdminMetricsQuery metricsQuery;
    private final ApplicationEventPublisher eventPublisher;

    // 순수 로직 — 오늘 지표 + 전일 스냅샷(null 가능)으로 이상 신호를 판정
    public Digest computeDigest(LocalDate date, DailyMetrics today, DailyStats yesterday) {
        List<String> anomalies = new ArrayList<>();

        if (today.analysesTotal() >= MIN_ANALYSES_FOR_RATE) {
            double rate = (double) today.analysesFailed() / today.analysesTotal();
            if (rate >= FAILURE_RATE_THRESHOLD) {
                anomalies.add("분석 실패율 " + Math.round(rate * 100) + "% ("
                        + today.analysesFailed() + "/" + today.analysesTotal() + ")");
            }
        }

        if (yesterday != null) {
            addDoDAnomaly(anomalies, "활성 사용자", today.activeUsers(), yesterday.getActiveUsers());
            addDoDAnomaly(anomalies, "분석 수", today.analysesTotal(), yesterday.getAnalysesTotal());
        }

        return new Digest(date, today, anomalies);
    }

    // 전일 대비 변화율이 임계를 넘으면 이상 신호 추가
    private void addDoDAnomaly(List<String> out, String label, int todayVal, int baseVal) {
        if (baseVal < DOD_MIN_BASE) return;
        double change = (double) (todayVal - baseVal) / baseVal;
        if (Math.abs(change) >= DOD_CHANGE_THRESHOLD) {
            int pct = (int) Math.round(change * 100);
            String dir = change > 0 ? "급증" : "급감";
            out.add(label + " 전일 대비 " + (pct > 0 ? "+" : "") + pct + "% " + dir
                    + " (" + baseVal + "→" + todayVal + ")");
        }
    }

    // 저장된 스냅샷에서 최신 다이제스트 구성 (REST/MCP 조회용) — 최신 스냅샷을 오늘, 직전을 전일로
    @Transactional(readOnly = true)
    public Optional<Digest> latestStoredDigest() {
        List<DailyStats> recent = dailyStatsRepository.findTop14ByOrderByStatDateDesc();
        if (recent.isEmpty()) return Optional.empty();
        DailyStats latest = recent.get(0);
        DailyStats prev = recent.size() > 1 ? recent.get(1) : null;
        return Optional.of(computeDigest(latest.getStatDate(), metricsOf(latest), prev));
    }

    // 스냅샷 엔티티 → 지표 값 객체 변환
    static DailyMetrics metricsOf(DailyStats s) {
        return new DailyMetrics(s.getNewUsers(), s.getActiveUsers(), s.getNewProjects(),
                s.getAnalysesTotal(), s.getAnalysesFailed(), s.getPaymentsCount(),
                s.getPaymentsAmount(), s.getNewFeedback());
    }

    // 지정 날짜의 다이제스트 생성·스냅샷 저장·관리자 발송 (스케줄·수동 트리거 공용)
    @Transactional
    public Digest runFor(LocalDate date) {
        Instant start = date.atStartOfDay(KST).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(KST).toInstant();
        DailyMetrics today = metricsQuery.collect(start, end);
        DailyStats yesterday = dailyStatsRepository.findByStatDate(date.minusDays(1)).orElse(null);
        Digest digest = computeDigest(date, today, yesterday);

        // 스냅샷 업서트 — 같은 날짜 행이 있으면 제자리 UPDATE, 없으면 INSERT
        // (delete+insert는 한 트랜잭션 내 Hibernate flush가 INSERT를 DELETE보다 먼저 실행해 유니크 위반)
        DailyStats snapshot = dailyStatsRepository.findByStatDate(date).orElse(null);
        if (snapshot != null) {
            snapshot.update(today.newUsers(), today.activeUsers(), today.newProjects(),
                    today.analysesTotal(), today.analysesFailed(), today.paymentsCount(),
                    today.paymentsAmount(), today.newFeedback());
            dailyStatsRepository.save(snapshot);
        } else {
            dailyStatsRepository.save(DailyStats.create(date, today.newUsers(), today.activeUsers(),
                    today.newProjects(), today.analysesTotal(), today.analysesFailed(),
                    today.paymentsCount(), today.paymentsAmount(), today.newFeedback()));
        }

        // 관리자 발송은 사이드이펙트 — 이벤트로 분리(NotificationEventHandler가 인앱 알림 + 웹푸시 수행)
        eventPublisher.publishEvent(new DailyDigestReadyEvent(metricsQuery.adminUserIds(), formatKorean(digest)));
        return digest;
    }

    // 다이제스트 → 한국어 알림 문구 (인앱·웹푸시 공용)
    static String formatKorean(Digest d) {
        DailyMetrics m = d.metrics();
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(d.date()).append("] 가입 ").append(m.newUsers())
                .append(" · 활성 ").append(m.activeUsers())
                .append(" · 프로젝트 ").append(m.newProjects())
                .append(" · 분석 ").append(m.analysesTotal()).append("(실패 ").append(m.analysesFailed()).append(")")
                .append(" · 결제 ").append(m.paymentsCount()).append("건 ").append(m.paymentsAmount()).append("원")
                .append(" · 문의 ").append(m.newFeedback());
        if (d.hasAnomaly()) {
            sb.append("\n⚠ 이상: ").append(String.join(" / ", d.anomalies()));
        }
        return sb.toString();
    }
}
