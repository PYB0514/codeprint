// 사용자 도메인 Repository 인터페이스
package com.codeprint.domain.user;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    // 여러 ID로 사용자 일괄 조회 (목록 화면 작성자명 N+1 제거용)
    java.util.List<User> findByIdIn(java.util.List<UUID> ids);

    Optional<User> findByGithubId(Long githubId);

    Optional<User> findByEmail(String email);

    boolean existsByGithubId(Long githubId);

    // 사용자명 키워드로 검색 (최대 10명)
    java.util.List<User> searchByUsername(String keyword);

    // 어드민 전용 — 전체 사용자 수 조회
    long count();

    // 어드민 전용 — 페이지 단위 사용자 목록 조회
    org.springframework.data.domain.Page<User> findAll(org.springframework.data.domain.Pageable pageable);

    // 계정 영구 삭제 (연관 데이터 CASCADE 처리)
    void delete(UUID id);
}
