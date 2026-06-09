// RefreshToken JPA 저장소 인터페이스
package com.codeprint.infrastructure.persistence.user;

import com.codeprint.domain.user.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteAllByUserId(UUID userId);

    void deleteByTokenHash(String tokenHash);
}
