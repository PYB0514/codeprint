// 일별 서비스 지표 스냅샷 엔티티 — 전일 대비 비교·이상 감지용 (하루 1행)
package com.codeprint.domain.admin;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyStats {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "stat_date", nullable = false, unique = true)
    private LocalDate statDate;

    @Column(name = "new_users", nullable = false)
    private int newUsers;

    @Column(name = "active_users", nullable = false)
    private int activeUsers;

    @Column(name = "new_projects", nullable = false)
    private int newProjects;

    @Column(name = "analyses_total", nullable = false)
    private int analysesTotal;

    @Column(name = "analyses_failed", nullable = false)
    private int analysesFailed;

    @Column(name = "payments_count", nullable = false)
    private int paymentsCount;

    @Column(name = "payments_amount", nullable = false)
    private long paymentsAmount;

    @Column(name = "new_feedback", nullable = false)
    private int newFeedback;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 하루치 지표 스냅샷 생성
    public static DailyStats create(LocalDate statDate, int newUsers, int activeUsers, int newProjects,
                                    int analysesTotal, int analysesFailed, int paymentsCount,
                                    long paymentsAmount, int newFeedback) {
        DailyStats s = new DailyStats();
        s.id = UUID.randomUUID();
        s.statDate = statDate;
        s.newUsers = newUsers;
        s.activeUsers = activeUsers;
        s.newProjects = newProjects;
        s.analysesTotal = analysesTotal;
        s.analysesFailed = analysesFailed;
        s.paymentsCount = paymentsCount;
        s.paymentsAmount = paymentsAmount;
        s.newFeedback = newFeedback;
        s.createdAt = Instant.now();
        return s;
    }

    // 같은 날짜 재집계 시 값 갱신 (1일 1행 유지, delete+insert 대신 제자리 UPDATE)
    public void update(int newUsers, int activeUsers, int newProjects, int analysesTotal,
                       int analysesFailed, int paymentsCount, long paymentsAmount, int newFeedback) {
        this.newUsers = newUsers;
        this.activeUsers = activeUsers;
        this.newProjects = newProjects;
        this.analysesTotal = analysesTotal;
        this.analysesFailed = analysesFailed;
        this.paymentsCount = paymentsCount;
        this.paymentsAmount = paymentsAmount;
        this.newFeedback = newFeedback;
    }
}
