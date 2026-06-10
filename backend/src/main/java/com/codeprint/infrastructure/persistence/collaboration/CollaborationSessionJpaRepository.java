// CollaborationSession JPA Repository 인터페이스
package com.codeprint.infrastructure.persistence.collaboration;

import com.codeprint.domain.collaboration.CollaborationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CollaborationSessionJpaRepository extends JpaRepository<CollaborationSession, UUID> {
    // 초대 코드로 세션 조회
    Optional<CollaborationSession> findByInviteCode(String inviteCode);
    // 그래프ID+소유자ID로 세션 조회
    Optional<CollaborationSession> findByGraphIdAndOwnerId(UUID graphId, UUID ownerId);
    // 초대 코드 존재 여부 확인
    boolean existsByInviteCode(String inviteCode);
}
