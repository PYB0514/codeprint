// 플랜 변경 감사 로그 저장소
package com.codeprint.domain.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanGrantLogRepository extends JpaRepository<PlanGrantLog, UUID> {

    // 최근 변경 로그 50건 (최신순)
    List<PlanGrantLog> findTop50ByOrderByCreatedAtDesc();
}
