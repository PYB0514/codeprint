// 사용자 도메인 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.user;

import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpa;

    // 사용자 엔티티를 저장하고 반환
    @Override
    public User save(User user) {
        return jpa.save(user);
    }

    // UUID로 사용자 조회
    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id);
    }

    // GitHub ID로 사용자 조회
    @Override
    public Optional<User> findByGithubId(Long githubId) {
        return jpa.findByGithubId(githubId);
    }

    // 이메일로 사용자 조회
    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email);
    }

    // GitHub ID 존재 여부 확인
    @Override
    public boolean existsByGithubId(Long githubId) {
        return jpa.existsByGithubId(githubId);
    }

    // 전체 사용자 수 반환
    @Override
    public long count() {
        return jpa.count();
    }

    // 페이지 단위 사용자 목록 반환
    @Override
    public Page<User> findAll(Pageable pageable) {
        return jpa.findAll(pageable);
    }

    // 사용자명 키워드로 검색 (최대 10명)
    @Override
    public List<User> searchByUsername(String keyword) {
        return jpa.findByUsernameContainingIgnoreCase(keyword, Pageable.ofSize(10));
    }
}
