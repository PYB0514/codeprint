// 사용자 도메인 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.user;

import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpa;

    @Override
    public User save(User user) {
        return jpa.save(user);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<User> findByGithubId(Long githubId) {
        return jpa.findByGithubId(githubId);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email);
    }

    @Override
    public boolean existsByGithubId(Long githubId) {
        return jpa.existsByGithubId(githubId);
    }
}
