// 공지사항 도메인 Repository 인터페이스
package com.codeprint.domain.notice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoticeRepository {

    // 공지사항 저장
    Notice save(Notice notice);

    // ID로 공지사항 조회
    Optional<Notice> findById(UUID id);

    // 활성 공지사항 전체 조회 (최신 순)
    List<Notice> findAllActive();

    // 전체 공지사항 조회 (어드민용, 최신 순)
    List<Notice> findAll();

    // ID로 공지사항 삭제
    void deleteById(UUID id);
}
