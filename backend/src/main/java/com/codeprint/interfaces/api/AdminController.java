// 어드민 전용 API — 통계 조회, 사용자 목록·정지·복구
package com.codeprint.interfaces.api;

import com.codeprint.application.admin.AdminDigestService;
import com.codeprint.application.admin.Digest;
import com.codeprint.domain.admin.PlanGrantLog;
import com.codeprint.domain.admin.PlanGrantLogRepository;
import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.project.ProjectRepository;
import com.codeprint.domain.user.User;
import com.codeprint.domain.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final AnalysisRepository analysisRepository;
    private final PlanGrantLogRepository planGrantLogRepository;
    private final AdminDigestService adminDigestService;

    private static final java.time.ZoneId KST = java.time.ZoneId.of("Asia/Seoul");

    // 플랜 변경 요청 DTO (사유 필수 — 감사 기록용)
    record PlanGrantRequest(@NotBlank String plan, @NotBlank @Size(max = 500) String reason) {}

    // 서비스 전체 통계 조회
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(Map.of(
                "totalUsers", userRepository.count(),
                "totalProjects", projectRepository.count(),
                "totalAnalyses", analysisRepository.count()
        ));
    }

    // 최신 일일 다이제스트 조회 (저장된 스냅샷 기준)
    @GetMapping("/digest")
    public ResponseEntity<?> getDigest() {
        Optional<Digest> digest = adminDigestService.latestStoredDigest();
        return digest.isPresent()
                ? ResponseEntity.ok(digest.get())
                : ResponseEntity.ok(Map.of("message", "아직 집계된 다이제스트가 없습니다"));
    }

    // 다이제스트 수동 생성·발송 (기본: 전일) — 데모·검증용
    @PostMapping("/digest/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> runDigest(@RequestParam(required = false) String date) {
        java.time.LocalDate target = date != null
                ? java.time.LocalDate.parse(date)
                : java.time.LocalDate.now(KST).minusDays(1);
        return ResponseEntity.ok(adminDigestService.runFor(target));
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

    // 사용자 플랜 변경 (FREE↔DESKTOP) — 감사 로그를 남기는 인가된 관리자 액션. 결제 주문을 위조하지 않고 별도 grant 기록으로 모델링.
    @PostMapping("/users/{id}/plan")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> changePlan(@PathVariable UUID id,
                                        @Valid @RequestBody PlanGrantRequest req,
                                        @AuthenticationPrincipal User admin) {
        String target = req.plan().toUpperCase();
        if (!target.equals("FREE") && !target.equals("DESKTOP")) {
            return ResponseEntity.badRequest().body(Map.of("message", "plan은 FREE 또는 DESKTOP만 가능합니다. (팀 플랜은 별도 경로)"));
        }
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        User user = opt.get();
        String oldPlan = user.getPlan().name();
        if (target.equals(oldPlan)) {
            return ResponseEntity.ok(Map.of("message", "이미 해당 플랜입니다.", "plan", oldPlan));
        }
        if (target.equals("DESKTOP")) user.upgradeToPro();
        else user.downgradeToFree();
        userRepository.save(user);
        // 감사 로그 — 자기 자신에게 부여하는 경우도 기록 (권한 남용 추적)
        planGrantLogRepository.save(PlanGrantLog.create(admin.getId(), id, oldPlan, target, req.reason()));
        return ResponseEntity.ok(Map.of("message", "플랜이 변경되었습니다.", "plan", target));
    }

    // 플랜 변경 감사 로그 최근 50건 조회
    @GetMapping("/plan-grants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> recentPlanGrants() {
        List<Map<String, Object>> logs = planGrantLogRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(l -> Map.<String, Object>of(
                        "id", l.getId().toString(),
                        "actorAdminId", l.getActorAdminId().toString(),
                        "targetUserId", l.getTargetUserId().toString(),
                        "oldPlan", l.getOldPlan(),
                        "newPlan", l.getNewPlan(),
                        "reason", l.getReason(),
                        "createdAt", l.getCreatedAt().toString()
                ))
                .toList();
        return ResponseEntity.ok(logs);
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
