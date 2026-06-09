// CollaborationSession JPA Repository 인터페이스
package com.codeprint.infrastructure.persistence.collaboration;

import com.codeprint.domain.collaboration.CollaborationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CollaborationSessionJpaRepository extends JpaRepository<CollaborationSession, UUID> {
    Optional<CollaborationSession> findByInviteCode(String inviteCode);
    Optional<CollaborationSession> findByGraphIdAndOwnerId(UUID graphId, UUID ownerId);
    boolean existsByInviteCode(String inviteCode);
}
