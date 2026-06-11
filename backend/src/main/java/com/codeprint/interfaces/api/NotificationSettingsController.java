// 유저 알림 설정 조회·수정 REST API
package com.codeprint.interfaces.api;

import com.codeprint.application.notification.NotificationSettingsApplicationService;
import com.codeprint.domain.notification.UserNotificationSettings;
import com.codeprint.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/settings")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsApplicationService settingsService;

    // Principal에서 로그인 사용자 ID 추출
    private UUID currentUserId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof User u) {
            return u.getId();
        }
        throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    record SettingsResponse(boolean teamChat, boolean dm) {}
    record UpdateRequest(boolean teamChat, boolean dm) {}

    // 알림 설정 조회 (없으면 기본값 반환)
    @GetMapping
    public SettingsResponse get(Principal principal) {
        UserNotificationSettings s = settingsService.get(currentUserId(principal));
        return new SettingsResponse(s.isTeamChat(), s.isDm());
    }

    // 알림 설정 업데이트
    @PutMapping
    public SettingsResponse update(@RequestBody UpdateRequest req, Principal principal) {
        UserNotificationSettings s = settingsService.update(currentUserId(principal), req.teamChat(), req.dm());
        return new SettingsResponse(s.isTeamChat(), s.isDm());
    }
}
