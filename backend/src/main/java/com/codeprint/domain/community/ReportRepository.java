// 신고 레포지토리 인터페이스 (도메인 포트)
package com.codeprint.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    // 처리 상태별 신고 수 (미처리 백로그 집계용)
    long countByStatus(String status);
}
