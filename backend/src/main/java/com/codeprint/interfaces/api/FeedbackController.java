// 사용자 문의/피드백 제출 및 관리자 조회 API
package com.codeprint.interfaces.api;

import com.codeprint.domain.community.Feedback;
import com.codeprint.domain.community.FeedbackRepository;
import com.codeprint.domain.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;

    // 피드백 제출 요청 DTO
    record SubmitRequest(
        @NotBlank String category,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 3000) String content,
        @Size(max = 200) String email
    ) {}

    // 피드백 응답 DTO
    record FeedbackResponse(UUID id, String category, String title, String content, String email, UUID userId, Instant createdAt, String status) {}

    // 처리 상태 변경 요청 DTO
    record StatusRequest(@NotBlank String status) {}

    // 피드백 제출 (로그인 사용자만)
    @PostMapping
    public ResponseEntity<Void> submit(
        @Valid @RequestBody SubmitRequest req,
        @AuthenticationPrincipal User user
    ) {
        Feedback feedback = Feedback.create(user.getId(), req.category(), req.title(), req.content(), req.email());
        feedbackRepository.save(feedback);
        return ResponseEntity.status(201).build();
    }

    // 관리자 전체 조회 (최신순)
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FeedbackResponse>> listAll() {
        List<FeedbackResponse> list = feedbackRepository.findAll().stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .map(f -> new FeedbackResponse(f.getId(), f.getCategory(), f.getTitle(), f.getContent(), f.getEmail(), f.getUserId(), f.getCreatedAt(), f.getStatus()))
            .toList();
        return ResponseEntity.ok(list);
    }

    // 관리자 문의 처리 상태 변경 (OPEN↔RESOLVED)
    @PatchMapping("/admin/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        if (!"OPEN".equals(req.status()) && !"RESOLVED".equals(req.status())) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Feedback> opt = feedbackRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Feedback feedback = opt.get();
        if ("RESOLVED".equals(req.status())) feedback.resolve();
        else feedback.reopen();
        feedbackRepository.save(feedback);
        return ResponseEntity.noContent().build();
    }
}
