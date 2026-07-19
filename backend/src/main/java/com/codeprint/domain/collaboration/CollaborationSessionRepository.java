// 협업 세션 Repository 인터페이스
package com.codeprint.domain.collaboration;

import java.util.Optional;
import java.util.UUID;

public interface CollaborationSessionRepository {
    CollaborationSession save(CollaborationSession session);
    Optional<CollaborationSession> findById(UUID id);
    Optional<CollaborationSession> findByInviteCode(String inviteCode);
    Optional<CollaborationSession> findByGraphIdAndOwnerId(UUID graphId, UUID ownerId);
    boolean existsByInviteCode(String inviteCode);
}
