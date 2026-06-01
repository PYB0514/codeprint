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
}
