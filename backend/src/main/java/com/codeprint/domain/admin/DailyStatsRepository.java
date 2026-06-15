// 일별 지표 스냅샷 저장소
package com.codeprint.domain.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyStatsRepository extends JpaRepository<DailyStats, UUID> {

    // 특정 날짜 스냅샷 조회 (전일 대비 비교용)
    Optional<DailyStats> findByStatDate(LocalDate statDate);

    // 최근 N일 스냅샷 (최신순) — 추세 표시용
    List<DailyStats> findTop14ByOrderByStatDateDesc();
}
