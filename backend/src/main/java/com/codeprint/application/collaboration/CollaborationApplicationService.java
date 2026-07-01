// 협업 세션 생성·참가·조회 유스케이스
package com.codeprint.application.collaboration;

import com.codeprint.domain.collaboration.CollaborationSession;
import com.codeprint.domain.collaboration.CollaborationSessionRepository;
import com.codeprint.domain.collaboration.port.UserInfoPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollaborationApplicationService {

    private final CollaborationSessionRepository sessionRepository;
    private final UserInfoPort userInfoPort;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    // Free 플랜 최대 참가자 수 (오너 포함)
    private static final int FREE_PARTICIPANT_LIMIT = 6;

    // 그래프에 대한 협업 세션을 생성하거나 기존 세션을 반환
    @Transactional
    public CollaborationSession createOrGetSession(UUID graphId, UUID ownerId) {
        return sessionRepository.findByGraphIdAndOwnerId(graphId, ownerId)
                .orElseGet(() -> {
                    String code = generateUniqueCode();
                    CollaborationSession session = CollaborationSession.create(graphId, ownerId, code);
                    session.addParticipant(ownerId);
                    return sessionRepository.save(session);
                });
    }

    // 초대 코드로 세션에 참가 — Free 플랜 오너면 6명 초과 불가
    @Transactional
    public CollaborationSession joinSession(String inviteCode, UUID userId) {
        CollaborationSession session = sessionRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 코드입니다."));

        boolean ownerIsPaid = userInfoPort.isPaidPlan(session.getOwnerId());
        if (!ownerIsPaid && !session.hasParticipant(userId)
                && session.getParticipants().size() >= FREE_PARTICIPANT_LIMIT) {
            throw new IllegalStateException("Free 플랜은 최대 6명까지 협업할 수 있습니다. 오너가 Desktop 라이센스로 업그레이드하면 무제한 초대가 가능합니다.");
        }

        session.addParticipant(userId);
        return sessionRepository.save(session);
    }

    // 초대 코드로 세션 정보 조회
    @Transactional(readOnly = true)
    public Map<String, Object> getSessionInfo(String inviteCode) {
        CollaborationSession session = sessionRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 코드입니다."));

        List<Map<String, String>> participants = session.getParticipants().stream()
                .map(p -> {
                    String username = userInfoPort.findUsernameById(p.getUserId());
                    return Map.of("userId", p.getUserId().toString(), "username", username);
                })
                .toList();

        return Map.of(
                "sessionId", session.getId().toString(),
                "graphId", session.getGraphId().toString(),
                "inviteCode", session.getInviteCode(),
                "participants", participants
        );
    }

    // 충돌 없는 8자리 초대 코드 생성
    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (sessionRepository.existsByInviteCode(code));
        return code;
    }
}
