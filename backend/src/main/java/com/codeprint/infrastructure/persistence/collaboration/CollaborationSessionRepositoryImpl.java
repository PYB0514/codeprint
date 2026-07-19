// CollaborationSessionRepository 구현체
package com.codeprint.infrastructure.persistence.collaboration;

import com.codeprint.domain.collaboration.CollaborationSession;
import com.codeprint.domain.collaboration.CollaborationSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CollaborationSessionRepositoryImpl implements CollaborationSessionRepository {

    private final CollaborationSessionJpaRepository jpa;

    @Override
    public CollaborationSession save(CollaborationSession session) {
        return jpa.save(session);
    }

    @Override
    public Optional<CollaborationSession> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<CollaborationSession> findByInviteCode(String inviteCode) {
        return jpa.findByInviteCode(inviteCode);
    }

    @Override
    public Optional<CollaborationSession> findByGraphIdAndOwnerId(UUID graphId, UUID ownerId) {
        return jpa.findByGraphIdAndOwnerId(graphId, ownerId);
    }

    @Override
    public boolean existsByInviteCode(String inviteCode) {
        return jpa.existsByInviteCode(inviteCode);
    }
}
