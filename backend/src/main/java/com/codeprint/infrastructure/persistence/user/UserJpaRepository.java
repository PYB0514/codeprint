package com.codeprint.infrastructure.persistence.user;

import com.codeprint.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<User, UUID> {

    Optional<User> findByGithubId(Long githubId);

    Optional<User> findByEmail(String email);

    boolean existsByGithubId(Long githubId);
}
