// 사용자 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.user;

import com.codeprint.domain.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<User, UUID> {

    Optional<User> findByGithubId(Long githubId);

    Optional<User> findByEmail(String email);

    boolean existsByGithubId(Long githubId);

    // 사용자명 키워드 검색 (대소문자 무시, 최대 N개)
    List<User> findByUsernameContainingIgnoreCase(String keyword, Pageable pageable);
}
