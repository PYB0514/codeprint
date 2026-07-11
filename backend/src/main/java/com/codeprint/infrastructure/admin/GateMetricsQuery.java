// 지표 대시보드용 지표 집계 — 여러 테이블을 가로지르는 리포팅 read-model (네이티브 count, AdminMetricsQuery와 동일 패턴)
package com.codeprint.infrastructure.admin;

import com.codeprint.application.admin.GateMetrics;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Repository
public class GateMetricsQuery {

    @PersistenceContext
    private EntityManager em;

    // 지표 대시보드 3층 체계(북극성·경험·실적) 현재 값 집계
    public GateMetrics collect() {
        Timestamp last30d = Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS));
        Timestamp last7d = Timestamp.from(Instant.now().minus(7, ChronoUnit.DAYS));
        return new GateMetrics(
                countSince("SELECT count(DISTINCT project_id) FROM gate_check_logs WHERE created_at >= :since", last30d),
                countSince("SELECT count(DISTINCT project_id) FROM analyses WHERE created_at >= :since", last7d),
                countSince("SELECT count(*) FROM posts WHERE created_at >= :since", last7d),
                count("SELECT count(*) FROM gate_check_logs WHERE state = 'failure'")
        );
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
