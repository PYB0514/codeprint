// 협업 세션 생성·참가·조회 REST API
package com.codeprint.interfaces.api;

import com.codeprint.application.collaboration.CollaborationApplicationService;
import com.codeprint.domain.collaboration.CollaborationSession;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/collaboration")
@RequiredArgsConstructor
public class CollaborationController {

    private final CollaborationApplicationService collaborationService;

    // 그래프에 대한 협업 세션 생성 또는 기존 세션 반환
    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {
        UUID graphId = UUID.fromString(body.get("graphId"));
        CollaborationSession session = collaborationService.createOrGetSession(graphId, user.getId());
        return ResponseEntity.ok(Map.of(
                "sessionId", session.getId().toString(),
                "inviteCode", session.getInviteCode(),
                "graphId", session.getGraphId().toString()
        ));
    }

    // 초대 코드로 협업 세션에 참가
    @PostMapping("/sessions/{inviteCode}/join")
    public ResponseEntity<Map<String, Object>> joinSession(
            @PathVariable String inviteCode,
            @AuthenticationPrincipal User user) {
        CollaborationSession session = collaborationService.joinSession(inviteCode, user.getId());
        return ResponseEntity.ok(Map.of(
                "sessionId", session.getId().toString(),
                "graphId", session.getGraphId().toString(),
                "inviteCode", session.getInviteCode()
        ));
    }

    // 초대 코드로 세션 정보 및 참가자 목록 조회
    @GetMapping("/sessions/{inviteCode}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String inviteCode) {
        return ResponseEntity.ok(collaborationService.getSessionInfo(inviteCode));
    }
}
