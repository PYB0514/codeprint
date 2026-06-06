// 공지사항 도메인 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.notice;

import com.codeprint.domain.notice.Notice;
import com.codeprint.domain.notice.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NoticeRepositoryImpl implements NoticeRepository {

    private final NoticeJpaRepository jpa;

    // 공지사항 저장 후 반환
    @Override
    public Notice save(Notice notice) {
        return jpa.save(notice);
    }

    // UUID로 공지사항 조회
    @Override
    public Optional<Notice> findById(UUID id) {
        return jpa.findById(id);
    }

    // 활성 공지사항 최신 순 조회
    @Override
    public List<Notice> findAllActive() {
        return jpa.findByActiveTrueOrderByCreatedAtDesc();
    }

    // 전체 공지사항 최신 순 조회 (어드민용)
    @Override
    public List<Notice> findAll() {
        return jpa.findAllByOrderByCreatedAtDesc();
    }

    // UUID로 공지사항 삭제
    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }
}
