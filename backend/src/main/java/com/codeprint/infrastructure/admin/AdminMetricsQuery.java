// 일일 다이제스트용 지표 집계 — 여러 테이블을 가로지르는 리포팅 read-model (네이티브 count)
package com.codeprint.infrastructure.admin;

import com.codeprint.application.admin.DailyMetrics;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class AdminMetricsQuery {

    @PersistenceContext
    private EntityManager em;

    // [start, end) 구간의 하루치 지표 집계
    public DailyMetrics collect(Instant start, Instant end) {
        Timestamp s = Timestamp.from(start);
        Timestamp e = Timestamp.from(end);
        return new DailyMetrics(
                count("SELECT count(*) FROM users WHERE created_at >= :start AND created_at < :end", s, e),
                // 활성 사용자 근사 — 로그인 토큰 발급 기준 고유 사용자 (활동 추적 컬럼 부재로 인한 프록시)
                count("SELECT count(DISTINCT user_id) FROM refresh_tokens WHERE created_at >= :start AND created_at < :end", s, e),
                count("SELECT count(*) FROM projects WHERE created_at >= :start AND created_at < :end", s, e),
                count("SELECT count(*) FROM analyses WHERE created_at >= :start AND created_at < :end", s, e),
                count("SELECT count(*) FROM analyses WHERE status = 'FAILED' AND created_at >= :start AND created_at < :end", s, e),
                count("SELECT count(*) FROM toss_payment_orders WHERE status = 'CONFIRMED' AND confirmed_at >= :start AND confirmed_at < :end", s, e),
                sum("SELECT COALESCE(sum(amount), 0) FROM toss_payment_orders WHERE status = 'CONFIRMED' AND confirmed_at >= :start AND confirmed_at < :end", s, e),
                count("SELECT count(*) FROM feedbacks WHERE created_at >= :start AND created_at < :end", s, e)
        );
    }

    // ADMIN 역할 사용자 ID 목록 (다이제스트 수신자)
    public List<UUID> adminUserIds() {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("SELECT id FROM users WHERE role = 'ADMIN'").getResultList();
        return rows.stream()
                .map(r -> r instanceof UUID u ? u : UUID.fromString(r.toString()))
                .toList();
    }

    private int count(String sql, Timestamp s, Timestamp e) {
        Number n = (Number) em.createNativeQuery(sql).setParameter("start", s).setParameter("end", e).getSingleResult();
        return n.intValue();
    }

    private long sum(String sql, Timestamp s, Timestamp e) {
        Number n = (Number) em.createNativeQuery(sql).setParameter("start", s).setParameter("end", e).getSingleResult();
        return n.longValue();
    }
}
