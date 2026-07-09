// 쪽지 전송·조회·읽음 처리 서비스
package com.codeprint.application.message;

import com.codeprint.domain.message.DirectMessage;
import com.codeprint.domain.message.DirectMessageRepository;
import com.codeprint.domain.message.UserBlock;
import com.codeprint.domain.message.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageApplicationService {

    private final DirectMessageRepository messageRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserQueryPort userQueryPort;

    // 쪽지 전송
    @Transactional
    public DirectMessage send(UUID senderId, UUID receiverId, String content) {
        if (userQueryPort.findById(receiverId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "수신자를 찾을 수 없습니다.");
        }
        if (senderId.equals(receiverId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자신에게 쪽지를 보낼 수 없습니다.");
        }
        if (userBlockRepository.existsByBlockerAndBlocked(receiverId, senderId)
                || userBlockRepository.existsByBlockerAndBlocked(senderId, receiverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "차단된 사용자와는 쪽지를 주고받을 수 없습니다.");
        }
        return messageRepository.save(DirectMessage.of(senderId, receiverId, content));
    }

    // 사용자 차단
    @Transactional
    public void block(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자신을 차단할 수 없습니다.");
        }
        if (userBlockRepository.existsByBlockerAndBlocked(blockerId, blockedId)) return;
        userBlockRepository.save(UserBlock.of(blockerId, blockedId));
    }

    // 사용자 차단 해제
    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        userBlockRepository.deleteByBlockerAndBlocked(blockerId, blockedId);
    }

    // 내가 차단한 사용자 ID 목록
    @Transactional(readOnly = true)
    public List<UUID> getBlockedUserIds(UUID blockerId) {
        return userBlockRepository.findByBlockerId(blockerId).stream().map(UserBlock::getBlockedId).toList();
    }

    // 받은 쪽지함 — 대화 상대별 최신 1개
    @Transactional(readOnly = true)
    public List<DirectMessage> getInbox(UUID userId, int page) {
        return messageRepository.findInboxLatest(userId, PageRequest.of(page, 20));
    }

    // 특정 유저와의 대화 스레드
    @Transactional(readOnly = true)
    public List<DirectMessage> getThread(UUID myId, UUID otherId, int page) {
        return messageRepository.findThread(myId, otherId, PageRequest.of(page, 30));
    }

    // 쪽지 읽음 처리
    @Transactional
    public void markRead(UUID messageId, UUID userId) {
        DirectMessage dm = messageRepository.findById(messageId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!dm.getReceiverId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        dm.markAsRead();
        messageRepository.save(dm);
    }

    // 안 읽은 쪽지 수
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return messageRepository.countUnread(userId);
    }

    // 유저 요약 정보 조회 (컨트롤러 응답 구성용)
    @Transactional(readOnly = true)
    public UserSummaryDto getUser(UUID userId) {
        return userQueryPort.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // 여러 유저 요약을 한 번에 조회해 userId→요약 맵 반환 (쪽지 목록 N+1 제거)
    @Transactional(readOnly = true)
    public Map<UUID, UserSummaryDto> getUsers(Collection<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<UUID, UserSummaryDto> map = new HashMap<>();
        for (UserSummaryDto u : userQueryPort.findByIds(List.copyOf(userIds))) map.put(u.id(), u);
        return map;
    }
}
