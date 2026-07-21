// 지표 대시보드용 지표 집계 — 여러 테이블을 가로지르는 리포팅 read-model (네이티브 count, AdminMetricsQuery와 동일 패턴)
package com.codeprint.infrastructure.admin;

import com.codeprint.application.admin.GateMetrics;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Repository
public class GateMetricsQuery {

    @PersistenceContext
    private EntityManager em;

    // BENCH_SPEC.md §4 1단계에서 벤치로 검증된 HIGH 8종 — 가드레일 정밀도 분모/분자를 HIGH급으로 한정하는 데 사용
    private static final Set<String> HIGH_SEVERITY_TYPES = Set.of(
            "CYCLIC_IMPORT", "DB_LAYER_BYPASS", "CROSS_CONTEXT_IMPORT", "CROSS_FEATURE_IMPORT",
            "FEATURE_LAYER_VIOLATION", "DOMAIN_IMPORTS_INFRA", "LAYERED_REVERSE_DEPENDENCY", "INTENT_DRIFT");

    // 지표 대시보드 4층 체계(북극성·경험·실적·가드레일) 현재 값 집계
    public GateMetrics collect() {
        Timestamp last30d = Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS));
        Timestamp last7d = Timestamp.from(Instant.now().minus(7, ChronoUnit.DAYS));
        return new GateMetrics(
                countSince("SELECT count(DISTINCT project_id) FROM gate_check_logs WHERE created_at >= :since", last30d),
                countSince("SELECT count(DISTINCT project_id) FROM analyses WHERE created_at >= :since", last7d),
                countSince("SELECT count(*) FROM posts WHERE created_at >= :since", last7d),
                count("SELECT count(*) FROM gate_check_logs WHERE state = 'failure'"),
                fpReportRatePct(last30d)
        );
    }

    // HIGH 경고 발생(gate_check_logs.high_count 합계) 대비 오탐 미신고 비율 — 근사치(발생은 PR 게이트 시점 누적,
    // 신고는 fingerprint 단위 고유 신고라 단위가 완전히 같진 않음). HIGH 발생 0건이면 100(분모 0 방지).
    // 주의: 이 값은 "사용자 신고" 기반이라 벤치(oracle) 기준 정밀도가 아니다 — fp_reports가 0건인 지금은
    // 항상 100에 수렴해 거짓 안심을 준다. 벤치 기반 precision 연결은 별도 백로그(decisions/DECISIONS_BACKEND.md 참조).
    private int fpReportRatePct(Timestamp since) {
        Number highOccurrences = (Number) em.createNativeQuery(
                "SELECT coalesce(sum(high_count), 0) FROM gate_check_logs WHERE created_at >= :since")
                .setParameter("since", since).getSingleResult();
        long occurrences = highOccurrences.longValue();
        if (occurrences == 0) return 100;

        Number highFpReports = (Number) em.createNativeQuery(
                "SELECT count(*) FROM fp_reports WHERE created_at >= :since AND warning_type IN :types")
                .setParameter("since", since)
                .setParameter("types", List.copyOf(HIGH_SEVERITY_TYPES))
                .getSingleResult();
        long fpReports = highFpReports.longValue();

        long precise = Math.max(0, occurrences - fpReports);
        return (int) Math.min(100, Math.round(precise * 100.0 / occurrences));
    }

    private int countSince(String sql, Timestamp since) {
        Number n = (Number) em.createNativeQuery(sql).setParameter("since", since).getSingleResult();
        return n.intValue();
    }

    private int count(String sql) {
        Number n = (Number) em.createNativeQuery(sql).getSingleResult();
        return n.intValue();
    }
}
