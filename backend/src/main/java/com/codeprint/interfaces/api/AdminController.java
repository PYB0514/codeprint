// 어드민 전용 API — 통계 조회, 사용자 목록·정지·복구
package com.codeprint.interfaces.api;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.project.ProjectRepository;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final AnalysisRepository analysisRepository;

    // 서비스 전체 통계 조회
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(Map.of(
                "totalUsers", userRepository.count(),
                "totalProjects", projectRepository.count(),
                "totalAnalyses", analysisRepository.count()
        ));
    }

    // 사용자 목록 페이지 조회 (최신 가입 순)
    @GetMapping("/users")
    public ResponseEntity<?> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<User> users = userRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(Map.of(
                "users", users.getContent().stream().map(this::toUserResponse).toList(),
                "totalElements", users.getTotalElements(),
                "totalPages", users.getTotalPages(),
                "page", page
        ));
    }

    // 계정 정지
    @PatchMapping("/users/{id}/disable")
    public ResponseEntity<?> disableUser(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(user -> {
                    user.disable();
                    userRepository.save(user);
                    return ResponseEntity.ok(Map.of("message", "계정이 정지되었습니다.", "enabled", false));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 계정 복구
    @PatchMapping("/users/{id}/enable")
    public ResponseEntity<?> enableUser(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(user -> {
                    user.enable();
                    userRepository.save(user);
                    return ResponseEntity.ok(Map.of("message", "계정이 복구되었습니다.", "enabled", true));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // User 엔티티를 응답 Map으로 변환
    private Map<String, Object> toUserResponse(User user) {
        return Map.of(
                "id", user.getId().toString(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "plan", user.getPlan().name(),
                "role", user.getRole().name(),
                "enabled", user.isEnabled(),
                "createdAt", user.getCreatedAt().toString()
        );
    }
}
