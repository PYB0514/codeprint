// RefreshToken 저장소 회귀 테스트 — 로그아웃 @Transactional 누락 반복 버그 방지
package com.codeprint.infrastructure.persistence.user;

import com.codeprint.domain.user.RefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRepositoryTest {

    @Mock
    private RefreshTokenJpaRepository jpa;

    @InjectMocks
    private RefreshTokenRepositoryImpl repository;

    private UUID userId;
    private RefreshToken token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        token = RefreshToken.create(userId, "hash-abc", Instant.now().plusSeconds(3600));
    }

    // deleteByTokenHash — @Transactional 선언 여부 검증 (누락 시 런타임 500 발생했던 케이스)
    @Test
    @DisplayName("deleteByTokenHash — @Transactional 어노테이션이 선언돼 있어야 한다")
    void deleteByTokenHash_hasTransactionalAnnotation() throws NoSuchMethodException {
        Method method = RefreshTokenRepositoryImpl.class.getMethod("deleteByTokenHash", String.class);
        assertThat(method.isAnnotationPresent(Transactional.class))
                .as("deleteByTokenHash must have @Transactional to avoid InvalidDataAccessApiUsageException")
                .isTrue();
    }

    // deleteAllByUserId — @Transactional 선언 여부 검증
    @Test
    @DisplayName("deleteAllByUserId — @Transactional 어노테이션이 선언돼 있어야 한다")
    void deleteAllByUserId_hasTransactionalAnnotation() throws NoSuchMethodException {
        Method method = RefreshTokenRepositoryImpl.class.getMethod("deleteAllByUserId", UUID.class);
        assertThat(method.isAnnotationPresent(Transactional.class))
                .as("deleteAllByUserId must have @Transactional to avoid InvalidDataAccessApiUsageException")
                .isTrue();
    }

    // deleteByTokenHash — JPA 위임 호출 검증
    @Test
    @DisplayName("deleteByTokenHash — JPA repository에 위임한다")
    void deleteByTokenHash_delegatesToJpa() {
        repository.deleteByTokenHash("hash-abc");
        verify(jpa).deleteByTokenHash("hash-abc");
    }

    // deleteAllByUserId — JPA 위임 호출 검증
    @Test
    @DisplayName("deleteAllByUserId — JPA repository에 위임한다")
    void deleteAllByUserId_delegatesToJpa() {
        repository.deleteAllByUserId(userId);
        verify(jpa).deleteAllByUserId(userId);
    }

    // findByTokenHash — 토큰 조회 정상 동작
    @Test
    @DisplayName("findByTokenHash — 저장된 토큰 반환")
    void findByTokenHash_returnsToken() {
        when(jpa.findByTokenHash("hash-abc")).thenReturn(Optional.of(token));
        Optional<RefreshToken> result = repository.findByTokenHash("hash-abc");
        assertThat(result).isPresent().contains(token);
    }
}
