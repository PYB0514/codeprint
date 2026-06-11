// 쪽지 저장소 도메인 인터페이스
package com.codeprint.domain.message;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DirectMessageRepository {

    DirectMessage save(DirectMessage message);

    Optional<DirectMessage> findById(UUID id);

    // 나와 특정 유저 간의 대화 스레드 (최신순)
    List<DirectMessage> findThread(UUID myId, UUID otherId, Pageable pageable);

    // 받은 쪽지함 — 최근 대화 상대별 최신 메시지 1개씩
    List<DirectMessage> findInboxLatest(UUID receiverId, Pageable pageable);

    // 안 읽은 쪽지 수
    long countUnread(UUID receiverId);
}
