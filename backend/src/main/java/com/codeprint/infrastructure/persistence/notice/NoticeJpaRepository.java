// 공지사항 JPA 레포지토리
package com.codeprint.infrastructure.persistence.notice;

import com.codeprint.domain.notice.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NoticeJpaRepository extends JpaRepository<Notice, UUID> {

    // 활성 공지사항 최신 순 조회
    List<Notice> findByActiveTrueOrderByCreatedAtDesc();

    // 전체 공지사항 최신 순 조회
    List<Notice> findAllByOrderByCreatedAtDesc();
}
