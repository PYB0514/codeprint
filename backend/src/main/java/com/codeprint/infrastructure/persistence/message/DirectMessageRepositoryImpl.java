// 쪽지 저장소 구현체
package com.codeprint.infrastructure.persistence.message;

import com.codeprint.domain.message.DirectMessage;
import com.codeprint.domain.message.DirectMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DirectMessageRepositoryImpl implements DirectMessageRepository {

    private final DirectMessageJpaBaseRepository jpa;
    private final EntityManager em;

    // 쪽지 저장
    @Override
    public DirectMessage save(DirectMessage message) {
        return jpa.save(message);
    }

    // ID로 쪽지 조회
    @Override
    public Optional<DirectMessage> findById(UUID id) {
        return jpa.findById(id);
    }

    // 두 유저 간 대화 스레드 조회 (최신순)
    @Override
    public List<DirectMessage> findThread(UUID myId, UUID otherId, Pageable pageable) {
        return em.createQuery(
                "SELECT dm FROM DirectMessage dm " +
                "WHERE (dm.senderId = :myId AND dm.receiverId = :otherId) " +
                "   OR (dm.senderId = :otherId AND dm.receiverId = :myId) " +
                "ORDER BY dm.createdAt DESC",
                DirectMessage.class)
            .setParameter("myId", myId)
            .setParameter("otherId", otherId)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();
    }

    // 받은 쪽지함 — 대화 상대별 최신 메시지 1개
    @Override
    public List<DirectMessage> findInboxLatest(UUID receiverId, Pageable pageable) {
        return em.createQuery(
                "SELECT dm FROM DirectMessage dm " +
                "WHERE dm.id IN (" +
                "  SELECT MAX(dm2.id) FROM DirectMessage dm2 " +
                "  WHERE dm2.receiverId = :receiverId " +
                "  GROUP BY dm2.senderId" +
                ") ORDER BY dm.createdAt DESC",
                DirectMessage.class)
            .setParameter("receiverId", receiverId)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();
    }

    // 안 읽은 쪽지 수
    @Override
    public long countUnread(UUID receiverId) {
        return jpa.countByReceiverIdAndReadAtIsNull(receiverId);
    }
}
