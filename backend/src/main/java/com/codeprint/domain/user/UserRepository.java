// 사용자 도메인 Repository 인터페이스
package com.codeprint.domain.user;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByGithubId(Long githubId);

    Optional<User> findByEmail(String email);

    boolean existsByGithubId(Long githubId);

    // 어드민 전용 — 전체 사용자 수 조회
    long count();

    // 어드민 전용 — 페이지 단위 사용자 목록 조회
    org.springframework.data.domain.Page<User> findAll(org.springframework.data.domain.Pageable pageable);
}
